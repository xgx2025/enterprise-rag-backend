package com.hope.enterpriserag.knowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.google.gson.JsonParser;
import com.hope.enterpriserag.common.exception.BusinessException;
import com.hope.enterpriserag.knowledge.config.RetrievalProperties;
import com.hope.enterpriserag.knowledge.dto.RetrievalResponse;
import com.hope.enterpriserag.knowledge.dto.RetrievalStatsResponse;
import com.hope.enterpriserag.knowledge.dto.ScoredChunkResponse;
import com.hope.enterpriserag.knowledge.embedding.EmbeddingService;
import com.hope.enterpriserag.knowledge.entity.DocumentChunk;
import com.hope.enterpriserag.knowledge.entity.KnowledgeBase;
import com.hope.enterpriserag.knowledge.entity.KnowledgeDocument;
import com.hope.enterpriserag.knowledge.mapper.DocumentChunkMapper;
import com.hope.enterpriserag.knowledge.mapper.KnowledgeBaseMapper;
import com.hope.enterpriserag.knowledge.mapper.KnowledgeDocumentMapper;
import com.hope.enterpriserag.knowledge.retrieval.ContextAssembler;
import com.hope.enterpriserag.knowledge.retrieval.Reranker;
import com.hope.enterpriserag.knowledge.retrieval.RetrievalAccessContext;
import com.hope.enterpriserag.knowledge.retrieval.RetrievalCommand;
import com.hope.enterpriserag.knowledge.retrieval.RetrievedChunk;
import com.hope.enterpriserag.knowledge.vector.VectorSearchHit;
import com.hope.enterpriserag.knowledge.vector.VectorSearchRequest;
import com.hope.enterpriserag.knowledge.vector.VectorSearchResult;
import com.hope.enterpriserag.knowledge.vector.VectorStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

/**
 * 企业知识检索编排服务。
 * 依次完成服务端访问范围计算、Dense/Sparse 召回、RRF 融合、父块回溯、重排和预算化上下文组装。
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "rag.vectorization", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class RetrievalService {
    private static final int MAX_RESULT_LIMIT = 20;
    private static final int MAX_CONTEXT_CHARACTERS = 30_000;

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final DocumentChunkMapper chunkMapper;
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final Reranker reranker;
    private final ContextAssembler contextAssembler;
    private final RetrievalProperties properties;

    /**
     * 在认证主体边界内执行完整检索链路。
     *
     * @param access 由服务端认证信息生成的不可提升访问边界
     * @param command 客户端可控制的查询、知识库缩小范围和策略开关
     * @return 各阶段候选、最终上下文、来源和耗时
     */
    public RetrievalResponse retrieve(RetrievalAccessContext access, RetrievalCommand command) {
        validate(access, command);
        long startedAt = System.nanoTime();
        String traceId = UUID.randomUUID().toString();
        Map<String, Long> timing = new LinkedHashMap<>();
        LocalDate effectiveDate = LocalDate.now();

        long stageStartedAt = System.nanoTime();
        List<Long> knowledgeBaseIds = resolveKnowledgeBaseIds(access, command.knowledgeBaseIds());
        List<KnowledgeDocument> documents = selectAccessibleDocuments(access, knowledgeBaseIds, effectiveDate);
        Map<Long, KnowledgeDocument> documentsById = documents.stream()
                .collect(Collectors.toMap(KnowledgeDocument::getId, document -> document));
        timing.put("permission.filter", elapsedMillis(stageStartedAt));

        List<RetrievedChunk> denseCandidates = List.of();
        List<RetrievedChunk> sparseCandidates = List.of();
        List<RetrievedChunk> fused = List.of();
        int rawCount = 0;
        stageStartedAt = System.nanoTime();
        if (!knowledgeBaseIds.isEmpty() && !documentsById.isEmpty()) {
            vectorStore.ensureReady(embeddingService.dimensions());
            List<Float> queryEmbedding = null;
            if (command.denseEnabled()) {
                List<List<Float>> embeddings = embeddingService.embed(List.of(command.query().trim()));
                if (embeddings.size() != 1) {
                    throw new IllegalStateException("查询 Embedding 返回数量不正确");
                }
                queryEmbedding = embeddings.getFirst();
            }
            VectorSearchResult searchResult = vectorStore.search(new VectorSearchRequest(
                    access.tenantId(), knowledgeBaseIds, List.copyOf(documentsById.keySet()),
                    access.maximumSecurityLevel(), effectiveDate, command.query().trim(),
                    command.denseEnabled(), command.sparseEnabled(),
                    bounded(properties.getDenseTopK(), 1, 100),
                    bounded(properties.getSparseTopK(), 1, 100),
                    bounded(properties.getFusionTopK(), 1, 100),
                    bounded(properties.getRrfK(), 1, 16_383), queryEmbedding));
            log.info("企业检索向量召回完成: tenantId={}, userId={}, traceId={}, knowledgeBases={}, denseHits={}, sparseHits={}, hybridHits={}",
                    access.tenantId(), access.userId(), traceId, knowledgeBaseIds.size(),
                    searchResult.denseHits().size(), searchResult.sparseHits().size(),
                    searchResult.hybridHits().size());
            rawCount = searchResult.denseHits().size() + searchResult.sparseHits().size();
            Map<Long, DocumentChunk> chunks = loadAuthorizedChildren(searchResult, documentsById,
                    access.tenantId());
            Map<Long, Double> denseScores = scores(searchResult.denseHits());
            Map<Long, Double> sparseScores = scores(searchResult.sparseHits());
            denseCandidates = materialize(searchResult.denseHits(), documentsById, chunks,
                    denseScores, Map.of(), false);
            sparseCandidates = materialize(searchResult.sparseHits(), documentsById, chunks,
                    Map.of(), sparseScores, false);
            fused = materialize(searchResult.hybridHits(), documentsById, chunks,
                    denseScores, sparseScores, true);
        }
        timing.put("milvus.hybrid", elapsedMillis(stageStartedAt));

        stageStartedAt = System.nanoTime();
        List<RetrievedChunk> withParents = backtrackParents(access.tenantId(), fused);
        timing.put("parent.backtrack", elapsedMillis(stageStartedAt));

        stageStartedAt = System.nanoTime();
        List<RetrievedChunk> reranked = command.rerankEnabled()
                ? reranker.rerank(command.query(), withParents)
                : withParents.stream()
                .map(candidate -> candidate.withRerankScore(candidate.fusionScore()))
                .sorted(Comparator.comparingDouble(RetrievedChunk::rerankScore).reversed())
                .toList();
        int resultLimit = command.resultLimit() == null
                ? bounded(properties.getRerankTopK(), 1, MAX_RESULT_LIMIT)
                : bounded(command.resultLimit(), 1, MAX_RESULT_LIMIT);
        reranked = reranked.stream().limit(resultLimit).toList();
        timing.put("rerank", elapsedMillis(stageStartedAt));

        stageStartedAt = System.nanoTime();
        int contextBudget = command.contextMaxCharacters() == null
                ? bounded(properties.getContextMaxCharacters(), 1_000, MAX_CONTEXT_CHARACTERS)
                : bounded(command.contextMaxCharacters(), 1_000, MAX_CONTEXT_CHARACTERS);
        var assembly = contextAssembler.assemble(reranked, resultLimit, contextBudget,
                bounded(properties.getMaxSourcesPerDocument(), 1, resultLimit));
        timing.put("context.build", elapsedMillis(stageStartedAt));

        long totalTime = elapsedMillis(startedAt);
        int materializedCount = denseCandidates.size() + sparseCandidates.size();
        int permissionFiltered = Math.max(0, rawCount - materializedCount);
        var stats = new RetrievalStatsResponse(rawCount, permissionFiltered,
                fused.size(), reranked.size(), totalTime);
        log.info("企业检索完成: tenantId={}, userId={}, traceId={}, knowledgeBases={}, denseCandidates={}, sparseCandidates={}, fusionCandidates={}, rerankKept={}, elapsedMs={}",
                access.tenantId(), access.userId(), traceId, knowledgeBaseIds.size(), denseCandidates.size(),
                sparseCandidates.size(), fused.size(), reranked.size(), totalTime);
        return new RetrievalResponse(traceId, command.query(),
                scored(denseCandidates, RetrievedChunk::denseScore, false),
                scored(sparseCandidates, RetrievedChunk::sparseScore, false),
                scored(fused, RetrievedChunk::fusionScore, false),
                scored(reranked, RetrievedChunk::rerankScore, true),
                assembly.context(),
                "{\"status\":\"CONTEXT_READY\",\"message\":\"检索与上下文组装完成，尚未调用回答模型\"}",
                java.util.Collections.unmodifiableMap(new LinkedHashMap<>(timing)), assembly.sources(), stats);
    }

    private List<Long> resolveKnowledgeBaseIds(RetrievalAccessContext access, List<Long> requestedIds) {
        Set<Long> requested = requestedIds == null ? Set.of() : requestedIds.stream()
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        LambdaQueryWrapper<KnowledgeBase> query = new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getTenantId, access.tenantId())
                .eq(KnowledgeBase::getStatus, "ACTIVE")
                .le(KnowledgeBase::getSecurityLevel, access.maximumSecurityLevel());
        if (!requested.isEmpty()) {
            query.in(KnowledgeBase::getId, requested);
        }
        List<Long> available = knowledgeBaseMapper.selectList(query).stream()
                .map(KnowledgeBase::getId)
                .toList();
        if (!requested.isEmpty() && available.size() != requested.size()) {
            log.warn("检索知识库范围校验失败: tenantId={}, userId={}, requestedCount={}, availableCount={}",
                    access.tenantId(), access.userId(), requested.size(), available.size());
            throw new BusinessException(403, "所选知识库不存在、已停用或无权访问");
        }
        return available;
    }

    private List<KnowledgeDocument> selectAccessibleDocuments(RetrievalAccessContext access,
                                                               List<Long> knowledgeBaseIds,
                                                               LocalDate effectiveDate) {
        if (knowledgeBaseIds.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<KnowledgeDocument> query = new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getTenantId, access.tenantId())
                .in(KnowledgeDocument::getKnowledgeBaseId, knowledgeBaseIds)
                .eq(KnowledgeDocument::getDeleted, 0)
                .eq(KnowledgeDocument::getStatus, "ACTIVE")
                .eq(KnowledgeDocument::getEmbeddingStatus, "COMPLETED")
                .le(KnowledgeDocument::getSecurityLevel, access.maximumSecurityLevel())
                .and(wrapper -> wrapper.isNull(KnowledgeDocument::getEffectiveFrom)
                        .or().le(KnowledgeDocument::getEffectiveFrom, effectiveDate))
                .and(wrapper -> wrapper.isNull(KnowledgeDocument::getEffectiveTo)
                        .or().ge(KnowledgeDocument::getEffectiveTo, effectiveDate));
        return documentMapper.selectList(query).stream()
                .filter(document -> hasAllowedRole(access.roles(), document))
                .toList();
    }

    private Map<Long, DocumentChunk> loadAuthorizedChildren(VectorSearchResult searchResult,
                                                             Map<Long, KnowledgeDocument> documentsById,
                                                             Long tenantId) {
        List<Long> chunkIds = java.util.stream.Stream.of(searchResult.denseHits(), searchResult.sparseHits(),
                        searchResult.hybridHits())
                .flatMap(List::stream)
                .map(VectorSearchHit::chunkId)
                .distinct()
                .toList();
        if (chunkIds.isEmpty()) {
            return Map.of();
        }
        return chunkMapper.selectList(new LambdaQueryWrapper<DocumentChunk>()
                        .eq(DocumentChunk::getTenantId, tenantId)
                        .in(DocumentChunk::getDocumentId, documentsById.keySet())
                        .in(DocumentChunk::getId, chunkIds)
                        .isNotNull(DocumentChunk::getParentChunkId)
                        .eq(DocumentChunk::getEmbeddingStatus, "COMPLETED"))
                .stream().collect(Collectors.toMap(DocumentChunk::getId, chunk -> chunk));
    }

    private Map<Long, Double> scores(List<VectorSearchHit> hits) {
        return hits.stream().collect(Collectors.toMap(VectorSearchHit::chunkId, VectorSearchHit::score,
                Math::max, LinkedHashMap::new));
    }

    private List<RetrievedChunk> materialize(List<VectorSearchHit> hits,
                                              Map<Long, KnowledgeDocument> documentsById,
                                              Map<Long, DocumentChunk> chunks,
                                              Map<Long, Double> denseScores,
                                              Map<Long, Double> sparseScores,
                                              boolean fusionStage) {
        List<RetrievedChunk> result = new ArrayList<>();
        for (VectorSearchHit hit : hits) {
            KnowledgeDocument document = documentsById.get(hit.documentId());
            DocumentChunk chunk = chunks.get(hit.chunkId());
            if (document == null || chunk == null || !document.getId().equals(chunk.getDocumentId())
                    || !hit.parentChunkId().equals(chunk.getParentChunkId())) {
                continue;
            }
            result.add(candidate(document, chunk,
                    denseScores.getOrDefault(hit.chunkId(), 0.0),
                    sparseScores.getOrDefault(hit.chunkId(), 0.0))
                    .withRetrievalScores(
                            denseScores.getOrDefault(hit.chunkId(), 0.0),
                            sparseScores.getOrDefault(hit.chunkId(), 0.0),
                            fusionStage ? hit.score() : 0.0));
        }
        return List.copyOf(result);
    }

    private RetrievedChunk candidate(KnowledgeDocument document, DocumentChunk chunk,
                                     double denseScore, double sparseScore) {
        if (document == null) {
            throw new IllegalStateException("检索候选缺少已授权文档元数据");
        }
        return new RetrievedChunk(chunk.getId(), chunk.getParentChunkId(), document.getId(),
                document.getKnowledgeBaseId(), document.getTitle(), document.getVersion(),
                document.getSecurityLevel(), document.getAuthorityLevel(), document.getEffectiveFrom(),
                chunk.getSectionPath(), chunk.getPageNumber(), chunk.getChunkIndex(), chunk.getContent(),
                null, denseScore, sparseScore, 0.0, 0.0);
    }

    private List<RetrievedChunk> backtrackParents(Long tenantId, List<RetrievedChunk> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        List<Long> parentIds = candidates.stream().map(RetrievedChunk::parentChunkId).distinct().toList();
        Map<Long, DocumentChunk> parents = chunkMapper.selectList(new LambdaQueryWrapper<DocumentChunk>()
                        .eq(DocumentChunk::getTenantId, tenantId)
                        .in(DocumentChunk::getId, parentIds)
                        .isNull(DocumentChunk::getParentChunkId))
                .stream().collect(Collectors.toMap(DocumentChunk::getId, parent -> parent));
        return candidates.stream().map(candidate -> {
            DocumentChunk parent = parents.get(candidate.parentChunkId());
            if (parent == null || !candidate.documentId().equals(parent.getDocumentId())) {
                return candidate.withParentContent(candidate.childContent());
            }
            return candidate.withParentContent(parent.getContent());
        }).toList();
    }

    private boolean hasAllowedRole(Set<String> roles, KnowledgeDocument document) {
        String value = document.getAllowedRoles();
        if (value == null || value.isBlank() || "[]".equals(value.trim())) {
            return true;
        }
        try {
            var array = JsonParser.parseString(value).getAsJsonArray();
            for (var element : array) {
                String required = element.getAsString().trim().toUpperCase(Locale.ROOT);
                if (roles.contains(required) || roles.contains("ROLE_" + required)) {
                    return true;
                }
            }
            return false;
        } catch (RuntimeException e) {
            log.warn("拒绝使用角色元数据无效的文档: tenantId={}, documentId={}",
                    document.getTenantId(), document.getId());
            return false;
        }
    }

    private List<ScoredChunkResponse> scored(List<RetrievedChunk> candidates,
                                             ToDoubleFunction<RetrievedChunk> score,
                                             boolean parentContent) {
        List<ScoredChunkResponse> result = new ArrayList<>(candidates.size());
        for (int index = 0; index < candidates.size(); index++) {
            RetrievedChunk candidate = candidates.get(index);
            String content = parentContent && candidate.parentContent() != null
                    ? candidate.parentContent() : candidate.childContent();
            result.add(new ScoredChunkResponse(index + 1, score.applyAsDouble(candidate), content,
                    String.valueOf(candidate.documentId()), candidate.documentTitle(), candidate.version(),
                    candidate.sectionPath(), candidate.pageNumber(), candidate.chunkIndex()));
        }
        return List.copyOf(result);
    }

    private void validate(RetrievalAccessContext access, RetrievalCommand command) {
        if (access == null || access.tenantId() == null || access.userId() == null) {
            throw new BusinessException(401, "用户认证信息无效");
        }
        if (access.maximumSecurityLevel() < 1 || access.maximumSecurityLevel() > 3) {
            throw new IllegalArgumentException("检索访问安全等级必须在 1 到 3 之间");
        }
        if (command == null || command.query() == null || command.query().isBlank()) {
            throw new BusinessException("检索问题不能为空");
        }
        if (command.query().trim().length() > 2_000) {
            throw new BusinessException("检索问题不能超过 2000 个字符");
        }
        if (!command.denseEnabled() && !command.sparseEnabled()) {
            throw new BusinessException("Dense 和 Sparse 检索不能同时关闭");
        }
    }

    private int bounded(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

}

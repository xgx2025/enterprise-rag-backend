package com.hope.enterpriserag.knowledge.vector;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hope.enterpriserag.knowledge.config.MilvusProperties;
import io.milvus.common.clientenum.FunctionType;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DescribeCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.vector.request.AnnSearchReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.HybridSearchReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.request.data.EmbeddedText;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.request.ranker.RRFRanker;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Milvus 2.6 向量存储实现。
 * Collection 使用子块 ID 作为主键并建立租户、知识库、文档状态、安全等级和有效期过滤索引。
 */
@Slf4j
@RequiredArgsConstructor
public class MilvusVectorStore implements VectorStore {
    private static final String CHUNK_ID = "chunk_id";
    private static final String VECTOR = "embedding";
    private static final String CONTENT = "content";
    private static final String SPARSE_VECTOR = "sparse_embedding";
    private static final String TENANT_ID = "tenant_id";
    private static final String KNOWLEDGE_BASE_ID = "knowledge_base_id";
    private static final String DOCUMENT_ID = "document_id";
    private static final String DOCUMENT_STATUS = "document_status";
    private static final String SECURITY_LEVEL = "security_level";
    private static final String EFFECTIVE_FROM = "effective_from";
    private static final String EFFECTIVE_TO = "effective_to";

    private final MilvusClientV2 client;
    private final MilvusProperties properties;
    private final Gson gson = new Gson();
    private volatile boolean collectionReady;
    private volatile int configuredDimensions;

    @Override
    public void ensureReady(int dimensions) {
        if (collectionReady) {
            verifyConfiguredDimension(dimensions);
            return;
        }
        synchronized (this) {
            if (collectionReady) {
                verifyConfiguredDimension(dimensions);
                return;
            }
            Boolean exists = client.hasCollection(HasCollectionReq.builder()
                    .databaseName(properties.getDatabaseName())
                    .collectionName(properties.getCollectionName())
                    .build());
            if (!Boolean.TRUE.equals(exists)) {
                createCollection(dimensions);
                log.info("Milvus Collection 创建成功: database={}, collection={}, dimensions={}",
                        properties.getDatabaseName(), properties.getCollectionName(), dimensions);
            } else {
                validateExistingCollection(dimensions);
                log.info("复用已有 Milvus Collection: database={}, collection={}",
                        properties.getDatabaseName(), properties.getCollectionName());
            }
            client.loadCollection(LoadCollectionReq.builder()
                    .databaseName(properties.getDatabaseName())
                    .collectionName(properties.getCollectionName())
                    .build());
            configuredDimensions = dimensions;
            collectionReady = true;
        }
    }

    @Override
    public void deleteDocument(Long tenantId, Long documentId) {
        requireReady();
        client.delete(DeleteReq.builder()
                .databaseName(properties.getDatabaseName())
                .collectionName(properties.getCollectionName())
                .filter(TENANT_ID + " == {tenantId} && " + DOCUMENT_ID + " == {documentId}")
                .filterTemplateValues(Map.of("tenantId", tenantId, "documentId", documentId))
                .build());
    }

    @Override
    public void upsert(List<VectorRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        requireReady();
        List<JsonObject> rows = records.stream().map(this::toRow).toList();
        client.upsert(UpsertReq.builder()
                .databaseName(properties.getDatabaseName())
                .collectionName(properties.getCollectionName())
                .data(rows)
                .build());
    }

    @Override
    public void updateMetadata(List<VectorMetadata> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return;
        }
        requireReady();
        List<JsonObject> rows = metadata.stream().map(item -> {
            JsonObject row = new JsonObject();
            row.addProperty(CHUNK_ID, item.chunkId());
            row.addProperty(DOCUMENT_STATUS, item.documentStatus());
            row.addProperty(EFFECTIVE_FROM, epochDay(item.effectiveFrom()));
            row.addProperty(EFFECTIVE_TO, epochDay(item.effectiveTo()));
            return row;
        }).toList();
        client.upsert(UpsertReq.builder()
                .databaseName(properties.getDatabaseName())
                .collectionName(properties.getCollectionName())
                .data(rows)
                .partialUpdate(true)
                .build());
    }

    @Override
    public VectorSearchResult search(VectorSearchRequest request) {
        validateSearchRequest(request);
        requireReady();
        String filter = governanceFilter();
        Map<String, Object> filterValues = governanceFilterValues(request);
        List<String> outputFields = List.of(DOCUMENT_ID, "parent_chunk_id", "chunk_index");

        List<VectorSearchHit> denseHits = request.denseEnabled()
                ? searchDense(request, filter, filterValues, outputFields)
                : List.of();
        log.info("Milvus Dense Search: query={}, tenantId={}, knowledgeBaseIds={}, documentIds={}, hits={}",
                request.query(), request.tenantId(), request.knowledgeBaseIds(), request.documentIds(), denseHits.size());
        List<VectorSearchHit> sparseHits = request.sparseEnabled()
                ? searchSparse(request, filter, filterValues, outputFields)
                : List.of();
        log.info("Milvus BM25 Search: query={}, tenantId={}, knowledgeBaseIds={}, documentIds={}, hits={}",
                request.query(), request.tenantId(), request.knowledgeBaseIds(), request.documentIds(), sparseHits.size());

        List<VectorSearchHit> hybridHits;
        if (request.denseEnabled() && request.sparseEnabled()) {
            AnnSearchReq dense = AnnSearchReq.builder()
                    .vectorFieldName(VECTOR)
                    .vectors(List.of(new FloatVec(request.embedding())))
                    .metricType(IndexParam.MetricType.COSINE)
                    .limit(request.denseTopK())
                    .filter(filter)
                    .filterTemplateValues(filterValues)
                    .build();
            AnnSearchReq sparse = AnnSearchReq.builder()
                    .vectorFieldName(SPARSE_VECTOR)
                    .vectors(List.of(new EmbeddedText(request.query().trim())))
                    .metricType(IndexParam.MetricType.BM25)
                    .params("{\"drop_ratio_search\":0}")
                    .limit(request.sparseTopK())
                    .filter(filter)
                    .filterTemplateValues(filterValues)
                    .build();
            SearchResp response = client.hybridSearch(HybridSearchReq.builder()
                    .databaseName(properties.getDatabaseName())
                    .collectionName(properties.getCollectionName())
                    .searchRequests(List.of(dense, sparse))
                    .ranker(RRFRanker.builder().k(request.rrfK()).build())
                    .limit(request.fusionTopK())
                    .outFields(outputFields)
                    .consistencyLevel(ConsistencyLevel.STRONG)
                    .build());
            hybridHits = hits(response);
        } else {
            hybridHits = request.denseEnabled() ? denseHits : sparseHits;
        }
        return new VectorSearchResult(denseHits, sparseHits, hybridHits);
    }

    private List<VectorSearchHit> searchDense(VectorSearchRequest request, String filter,
                                               Map<String, Object> filterValues, List<String> outputFields) {
        return hits(client.search(SearchReq.builder()
                .databaseName(properties.getDatabaseName())
                .collectionName(properties.getCollectionName())
                .annsField(VECTOR)
                .metricType(IndexParam.MetricType.COSINE)
                .consistencyLevel(ConsistencyLevel.STRONG)
                .limit(request.denseTopK())
                .filter(filter)
                .filterTemplateValues(filterValues)
                .outputFields(outputFields)
                .data(List.of(new FloatVec(request.embedding())))
                .build()));
    }

    private List<VectorSearchHit> searchSparse(VectorSearchRequest request, String filter,
                                                Map<String, Object> filterValues, List<String> outputFields) {
        return hits(client.search(SearchReq.builder()
                .databaseName(properties.getDatabaseName())
                .collectionName(properties.getCollectionName())
                .annsField(SPARSE_VECTOR)
                .metricType(IndexParam.MetricType.BM25)
                .searchParams(Map.of("drop_ratio_search", 0))
                .consistencyLevel(ConsistencyLevel.STRONG)
                .limit(request.sparseTopK())
                .filter(filter)
                .filterTemplateValues(filterValues)
                .outputFields(outputFields)
                .data(List.of(new EmbeddedText(request.query().trim())))
                .build()));
    }

    private String governanceFilter() {
        return TENANT_ID + " == {tenantId}"
                + " && " + KNOWLEDGE_BASE_ID + " in {knowledgeBaseIds}"
                + " && " + DOCUMENT_ID + " in {documentIds}"
                + " && " + DOCUMENT_STATUS + " == {documentStatus}"
                + " && " + SECURITY_LEVEL + " <= {maximumSecurityLevel}"
                + " && (" + EFFECTIVE_FROM + " == 0 || " + EFFECTIVE_FROM + " <= {effectiveDate})"
                + " && (" + EFFECTIVE_TO + " == 0 || " + EFFECTIVE_TO + " >= {effectiveDate})";
    }

    private Map<String, Object> governanceFilterValues(VectorSearchRequest request) {
        return Map.of(
                "tenantId", request.tenantId(),
                "knowledgeBaseIds", request.knowledgeBaseIds(),
                "documentIds", request.documentIds(),
                "documentStatus", "ACTIVE",
                "maximumSecurityLevel", request.maximumSecurityLevel(),
                "effectiveDate", request.effectiveDate().toEpochDay());
    }

    private List<VectorSearchHit> hits(SearchResp response) {
        if (response == null || response.getSearchResults() == null || response.getSearchResults().isEmpty()) {
            return List.of();
        }
        return response.getSearchResults().getFirst().stream()
                .map(result -> new VectorSearchHit(
                        number(result.getId(), CHUNK_ID).longValue(),
                        number(result.getEntity().get(DOCUMENT_ID), DOCUMENT_ID).longValue(),
                        number(result.getEntity().get("parent_chunk_id"), "parent_chunk_id").longValue(),
                        number(result.getEntity().get("chunk_index"), "chunk_index").intValue(),
                        result.getScore() == null ? 0.0 : result.getScore().doubleValue()))
                .toList();
    }

    private void createCollection(int dimensions) {
        CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder().build();
        schema.setEnableDynamicField(false);
        schema.addField(field(CHUNK_ID, DataType.Int64, true, null));
        schema.addField(field(TENANT_ID, DataType.Int64, false, null));
        schema.addField(field(KNOWLEDGE_BASE_ID, DataType.Int64, false, null));
        schema.addField(field(DOCUMENT_ID, DataType.Int64, false, null));
        schema.addField(field("parent_chunk_id", DataType.Int64, false, null));
        schema.addField(field("chunk_index", DataType.Int64, false, null));
        schema.addField(field("version", DataType.VarChar, false, 128));
        schema.addField(field(DOCUMENT_STATUS, DataType.VarChar, false, 32));
        schema.addField(field("department", DataType.VarChar, false, 128));
        schema.addField(field(SECURITY_LEVEL, DataType.Int64, false, null));
        schema.addField(field("authority_level", DataType.Int64, false, null));
        schema.addField(field(EFFECTIVE_FROM, DataType.Int64, false, null));
        schema.addField(field(EFFECTIVE_TO, DataType.Int64, false, null));
        schema.addField(field("allowed_roles", DataType.VarChar, false, 4096));
        schema.addField(field("section_path", DataType.VarChar, false, 1024));
        schema.addField(field("page_number", DataType.Int64, false, null));
        schema.addField(AddFieldReq.builder()
                .fieldName(CONTENT)
                .dataType(DataType.VarChar)
                .maxLength(8192)
                .enableAnalyzer(true)
                .analyzerParams(Map.of("type", "chinese"))
                .build());
        schema.addField(AddFieldReq.builder()
                .fieldName(SPARSE_VECTOR)
                .dataType(DataType.SparseFloatVector)
                .build());
        schema.addField(AddFieldReq.builder()
                .fieldName(VECTOR)
                .dataType(DataType.FloatVector)
                .dimension(dimensions)
                .build());
        schema.addFunction(CreateCollectionReq.Function.builder()
                .functionType(FunctionType.BM25)
                .name("content_bm25")
                .inputFieldNames(List.of(CONTENT))
                .outputFieldNames(List.of(SPARSE_VECTOR))
                .build());

        List<IndexParam> indexes = new ArrayList<>();
        indexes.add(IndexParam.builder()
                .fieldName(VECTOR)
                .indexType(IndexParam.IndexType.AUTOINDEX)
                .metricType(IndexParam.MetricType.COSINE)
                .build());
        indexes.add(IndexParam.builder()
                .fieldName(SPARSE_VECTOR)
                .indexType(IndexParam.IndexType.AUTOINDEX)
                .metricType(IndexParam.MetricType.BM25)
                .extraParams(Map.of(
                        "inverted_index_algo", "DAAT_MAXSCORE",
                        "bm25_k1", 1.2,
                        "bm25_b", 0.75))
                .build());
        for (String fieldName : List.of(TENANT_ID, KNOWLEDGE_BASE_ID, DOCUMENT_ID, DOCUMENT_STATUS,
                SECURITY_LEVEL, EFFECTIVE_FROM, EFFECTIVE_TO)) {
            indexes.add(IndexParam.builder()
                    .fieldName(fieldName)
                    .indexType(IndexParam.IndexType.AUTOINDEX)
                    .build());
        }

        client.createCollection(CreateCollectionReq.builder()
                .databaseName(properties.getDatabaseName())
                .collectionName(properties.getCollectionName())
                .description("Enterprise RAG child chunks for dense and BM25 hybrid search")
                .collectionSchema(schema)
                .indexParams(indexes)
                .build());
    }

    private void validateExistingCollection(int dimensions) {
        var response = client.describeCollection(DescribeCollectionReq.builder()
                .databaseName(properties.getDatabaseName())
                .collectionName(properties.getCollectionName())
                .build());
        var schema = response.getCollectionSchema();
        if (schema == null || schema.getField(VECTOR) == null) {
            throw new IllegalStateException("已有 Milvus Collection 缺少 embedding 字段");
        }
        Integer actualDimensions = schema.getField(VECTOR).getDimension();
        if (!Integer.valueOf(dimensions).equals(actualDimensions)) {
            throw new IllegalStateException("已有 Milvus Collection 向量维度为 " + actualDimensions
                    + "，与配置维度 " + dimensions + " 不一致");
        }
        for (String fieldName : List.of(CHUNK_ID, TENANT_ID, KNOWLEDGE_BASE_ID, DOCUMENT_ID,
                "parent_chunk_id", "chunk_index", DOCUMENT_STATUS, SECURITY_LEVEL, EFFECTIVE_FROM, EFFECTIVE_TO,
                CONTENT, SPARSE_VECTOR)) {
            if (schema.getField(fieldName) == null) {
                throw new IllegalStateException("已有 Milvus Collection 缺少字段: " + fieldName);
            }
        }
        if (!DataType.VarChar.equals(schema.getField(CONTENT).getDataType())
                || !Boolean.TRUE.equals(schema.getField(CONTENT).getEnableAnalyzer())) {
            throw new IllegalStateException("已有 Milvus Collection 的 content 字段未启用文本 Analyzer");
        }
        if (!DataType.SparseFloatVector.equals(schema.getField(SPARSE_VECTOR).getDataType())) {
            throw new IllegalStateException("已有 Milvus Collection 的 sparse_embedding 字段类型无效");
        }
        boolean hasBm25Function = schema.getFunctionList() != null && schema.getFunctionList().stream()
                .anyMatch(function -> FunctionType.BM25.equals(function.getFunctionType())
                        && List.of(CONTENT).equals(function.getInputFieldNames())
                        && List.of(SPARSE_VECTOR).equals(function.getOutputFieldNames()));
        if (!hasBm25Function) {
            throw new IllegalStateException("已有 Milvus Collection 缺少 content 到 sparse_embedding 的 BM25 Function");
        }
    }

    private AddFieldReq field(String name, DataType type, boolean primary, Integer maxLength) {
        var builder = AddFieldReq.builder()
                .fieldName(name)
                .dataType(type);
        if (primary) {
            builder.isPrimaryKey(true).autoID(false);
        }
        if (maxLength != null) {
            builder.maxLength(maxLength);
        }
        return builder.build();
    }

    private JsonObject toRow(VectorRecord record) {
        validate(record);
        JsonObject row = new JsonObject();
        row.addProperty(CHUNK_ID, record.chunkId());
        row.addProperty(TENANT_ID, record.tenantId());
        row.addProperty(KNOWLEDGE_BASE_ID, record.knowledgeBaseId());
        row.addProperty(DOCUMENT_ID, record.documentId());
        row.addProperty("parent_chunk_id", record.parentChunkId());
        row.addProperty("chunk_index", record.chunkIndex().longValue());
        row.addProperty("version", value(record.version()));
        row.addProperty(DOCUMENT_STATUS, value(record.documentStatus()));
        row.addProperty("department", value(record.department()));
        row.addProperty(SECURITY_LEVEL, record.securityLevel().longValue());
        row.addProperty("authority_level", record.authorityLevel().longValue());
        row.addProperty(EFFECTIVE_FROM, epochDay(record.effectiveFrom()));
        row.addProperty(EFFECTIVE_TO, epochDay(record.effectiveTo()));
        row.addProperty("allowed_roles", value(record.allowedRoles()));
        row.addProperty("section_path", value(record.sectionPath()));
        row.addProperty("page_number", record.pageNumber() == null ? -1L : record.pageNumber().longValue());
        row.addProperty(CONTENT, record.content());
        row.add(VECTOR, gson.toJsonTree(record.embedding()));
        return row;
    }

    private void validate(VectorRecord record) {
        if (record.chunkId() == null || record.tenantId() == null || record.knowledgeBaseId() == null
                || record.documentId() == null || record.parentChunkId() == null || record.chunkIndex() == null
                || record.securityLevel() == null || record.authorityLevel() == null || record.content() == null
                || record.content().isBlank() || record.embedding() == null) {
            throw new IllegalArgumentException("Milvus 向量记录缺少必填字段");
        }
        if (record.embedding().size() != configuredDimensions) {
            throw new IllegalArgumentException("Milvus 向量维度与 Collection 配置不一致");
        }
        if (value(record.allowedRoles()).length() > 4096) {
            throw new IllegalArgumentException("文档访问角色元数据超过 Milvus 字段限制");
        }
        if (record.content().length() > 8192) {
            throw new IllegalArgumentException("子块正文超过 Milvus content 字段限制");
        }
    }

    private void validateSearchRequest(VectorSearchRequest request) {
        if (request == null || request.tenantId() == null || request.effectiveDate() == null
                || request.knowledgeBaseIds() == null || request.knowledgeBaseIds().isEmpty()
                || request.documentIds() == null || request.documentIds().isEmpty()
                || request.query() == null || request.query().isBlank()) {
            throw new IllegalArgumentException("Milvus 检索请求缺少必填字段");
        }
        if (!request.denseEnabled() && !request.sparseEnabled()) {
            throw new IllegalArgumentException("Milvus Dense 和 BM25 检索不能同时关闭");
        }
        if (request.denseTopK() <= 0 || request.denseTopK() > 200
                || request.sparseTopK() <= 0 || request.sparseTopK() > 200
                || request.fusionTopK() <= 0 || request.fusionTopK() > 200) {
            throw new IllegalArgumentException("Milvus 检索 topK 必须在 1 到 200 之间");
        }
        if (request.rrfK() <= 0 || request.rrfK() >= 16384) {
            throw new IllegalArgumentException("Milvus RRF k 必须在 1 到 16383 之间");
        }
        if (request.maximumSecurityLevel() < 1 || request.maximumSecurityLevel() > 3) {
            throw new IllegalArgumentException("Milvus 检索安全等级必须在 1 到 3 之间");
        }
        if (request.denseEnabled() && (request.embedding() == null
                || request.embedding().size() != configuredDimensions)) {
            throw new IllegalArgumentException("Milvus 查询向量维度与 Collection 配置不一致");
        }
    }

    private Number number(Object value, String fieldName) {
        if (value instanceof Number number) {
            return number;
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                // 统一转换为下面的安全异常，不暴露 SDK 原始结果。
            }
        }
        throw new IllegalStateException("Milvus 检索结果字段格式无效: " + fieldName);
    }

    private long epochDay(LocalDate date) {
        return date == null ? 0L : date.toEpochDay();
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private void requireReady() {
        if (!collectionReady) {
            throw new IllegalStateException("Milvus Collection 尚未初始化");
        }
    }

    private void verifyConfiguredDimension(int dimensions) {
        if (configuredDimensions != dimensions) {
            throw new IllegalStateException("同一进程内请求了不同的 Milvus 向量维度");
        }
    }
}

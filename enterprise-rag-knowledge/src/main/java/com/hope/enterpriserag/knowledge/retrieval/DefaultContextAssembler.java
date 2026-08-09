package com.hope.enterpriserag.knowledge.retrieval;

import com.hope.enterpriserag.knowledge.dto.RetrievalSourceResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 默认上下文组装器。
 * 相同父块只保留一次，并限制单文档首轮来源数量，避免一个长文档挤占全部上下文。
 */
@Component
public class DefaultContextAssembler implements ContextAssembler {
    private static final int MIN_USEFUL_CONTENT = 80;

    @Override
    public ContextAssembly assemble(List<RetrievedChunk> candidates, int maximumSources,
                                    int maximumCharacters, int maximumSourcesPerDocument) {
        if (candidates == null || candidates.isEmpty() || maximumSources <= 0 || maximumCharacters <= 0) {
            return new ContextAssembly("", List.of());
        }
        List<RetrievedChunk> selected = selectDiverse(candidates, maximumSources,
                Math.max(1, maximumSourcesPerDocument));
        StringBuilder context = new StringBuilder(Math.min(maximumCharacters, 16_384));
        List<RetrievalSourceResponse> sources = new ArrayList<>();
        for (RetrievedChunk candidate : selected) {
            String content = usableContent(candidate);
            String sourceId = "S" + (sources.size() + 1);
            String header = header(sourceId, candidate);
            int remaining = maximumCharacters - context.length() - header.length() - 2;
            if (remaining < MIN_USEFUL_CONTENT) {
                break;
            }
            String quote = content.length() <= remaining ? content : content.substring(0, remaining - 1) + "…";
            if (!context.isEmpty()) {
                context.append("\n\n");
            }
            context.append(header).append('\n').append(quote);
            sources.add(new RetrievalSourceResponse(sourceId, String.valueOf(candidate.documentId()),
                    candidate.documentTitle(), candidate.version(), candidate.effectiveFrom(),
                    candidate.sectionPath(), candidate.pageNumber(), quote, candidate.securityLevel(),
                    candidate.rerankScore()));
        }
        return new ContextAssembly(context.toString(), List.copyOf(sources));
    }

    private List<RetrievedChunk> selectDiverse(List<RetrievedChunk> candidates, int maximumSources,
                                               int maximumSourcesPerDocument) {
        List<RetrievedChunk> selected = new ArrayList<>();
        Set<Long> parentIds = new HashSet<>();
        Map<Long, Integer> perDocument = new HashMap<>();
        for (RetrievedChunk candidate : candidates) {
            if (selected.size() >= maximumSources) {
                break;
            }
            if (parentIds.contains(candidate.parentChunkId())
                    || perDocument.getOrDefault(candidate.documentId(), 0) >= maximumSourcesPerDocument) {
                continue;
            }
            selected.add(candidate);
            parentIds.add(candidate.parentChunkId());
            perDocument.merge(candidate.documentId(), 1, Integer::sum);
        }
        if (selected.size() < maximumSources) {
            for (RetrievedChunk candidate : candidates) {
                if (selected.size() >= maximumSources) {
                    break;
                }
                if (parentIds.add(candidate.parentChunkId())) {
                    selected.add(candidate);
                }
            }
        }
        return selected;
    }

    private String usableContent(RetrievedChunk candidate) {
        return candidate.parentContent() == null || candidate.parentContent().isBlank()
                ? candidate.childContent() : candidate.parentContent();
    }

    private String header(String sourceId, RetrievedChunk candidate) {
        StringBuilder header = new StringBuilder("【来源 ").append(sourceId).append("】")
                .append(candidate.documentTitle());
        if (candidate.version() != null && !candidate.version().isBlank()) {
            header.append(' ').append(candidate.version());
        }
        if (candidate.sectionPath() != null && !candidate.sectionPath().isBlank()) {
            header.append(" | ").append(candidate.sectionPath());
        }
        if (candidate.pageNumber() != null && candidate.pageNumber() > 0) {
            header.append(" | 第").append(candidate.pageNumber()).append('页');
        }
        return header.toString();
    }
}

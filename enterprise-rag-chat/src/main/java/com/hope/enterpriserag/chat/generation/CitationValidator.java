package com.hope.enterpriserag.chat.generation;

import com.hope.enterpriserag.knowledge.dto.RetrievalSourceResponse;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 回答引用校验器，拒绝无引用和引用不存在来源编号的模型输出。
 */
@Component
public class CitationValidator {
    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[(S\\d+)]");

    /** 校验回答中的所有来源编号都属于本次受控上下文。 */
    public CitationValidationResult validate(String answer, List<RetrievalSourceResponse> sources) {
        if (answer == null || answer.isBlank()) {
            return new CitationValidationResult(false, List.of(), "ANSWER_EMPTY");
        }
        Map<String, RetrievalSourceResponse> allowed = sources.stream()
                .collect(Collectors.toMap(RetrievalSourceResponse::sourceId, Function.identity()));
        Matcher matcher = CITATION_PATTERN.matcher(answer);
        Set<String> citedIds = new LinkedHashSet<>();
        while (matcher.find()) {
            citedIds.add(matcher.group(1));
        }
        if (citedIds.isEmpty()) {
            return new CitationValidationResult(false, List.of(), "CITATION_MISSING");
        }
        if (!allowed.keySet().containsAll(citedIds)) {
            return new CitationValidationResult(false, List.of(), "CITATION_OUT_OF_SCOPE");
        }
        List<RetrievalSourceResponse> cited = sources.stream()
                .filter(source -> citedIds.contains(source.sourceId()))
                .toList();
        return new CitationValidationResult(true, cited, null);
    }
}

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
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(?<![A-Za-z])\\d+(?:\\.\\d+)?%?");
    private static final Pattern SENTENCE_PATTERN = Pattern.compile(
            "[^。！？!?；;\\n]+[。！？!?；;]?(?:\\s*\\[S\\d+])*", Pattern.MULTILINE);

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
        CitationValidationResult claimValidation = validateClaims(answer, allowed);
        if (!claimValidation.valid()) {
            return claimValidation;
        }
        List<RetrievalSourceResponse> cited = sources.stream()
                .filter(source -> citedIds.contains(source.sourceId()))
                .toList();
        return new CitationValidationResult(true, cited, null);
    }

    private CitationValidationResult validateClaims(String answer,
                                                      Map<String, RetrievalSourceResponse> allowed) {
        Matcher sentences = SENTENCE_PATTERN.matcher(answer);
        while (sentences.find()) {
            String sentence = sentences.group().trim();
            String claim = CITATION_PATTERN.matcher(sentence).replaceAll("")
                    .replaceFirst("^[#>*\\-+\\d.、\\s]+", "").trim();
            if (claim.isBlank() || claim.endsWith("：") || claim.endsWith(":")) {
                continue;
            }
            Matcher citations = CITATION_PATTERN.matcher(sentence);
            List<RetrievalSourceResponse> citedSources = new java.util.ArrayList<>();
            while (citations.find()) {
                RetrievalSourceResponse source = allowed.get(citations.group(1));
                if (source != null) {
                    citedSources.add(source);
                }
            }
            if (citedSources.isEmpty()) {
                return new CitationValidationResult(false, List.of(), "CLAIM_CITATION_MISSING");
            }
            if (citedSources.stream().noneMatch(source -> supports(claim, source.quote()))) {
                return new CitationValidationResult(false, List.of(), "CLAIM_NOT_SUPPORTED");
            }
        }
        return new CitationValidationResult(true, List.of(), null);
    }

    private boolean supports(String claim, String quote) {
        if (quote == null || quote.isBlank()) {
            return false;
        }
        Set<String> claimNumbers = matches(NUMBER_PATTERN, claim);
        Set<String> quoteNumbers = matches(NUMBER_PATTERN, quote);
        if (!quoteNumbers.containsAll(claimNumbers)) {
            return false;
        }
        Set<String> claimTerms = bigrams(normalize(claim));
        Set<String> quoteTerms = bigrams(normalize(quote));
        if (claimTerms.isEmpty()) {
            return !claimNumbers.isEmpty();
        }
        long overlap = claimTerms.stream().filter(quoteTerms::contains).count();
        return (double) overlap / claimTerms.size() >= 0.18;
    }

    private Set<String> matches(Pattern pattern, String value) {
        Set<String> result = new LinkedHashSet<>();
        Matcher matcher = pattern.matcher(value);
        while (matcher.find()) {
            result.add(matcher.group());
        }
        return result;
    }

    private Set<String> bigrams(String value) {
        Set<String> result = new LinkedHashSet<>();
        for (int index = 0; index + 1 < value.length(); index++) {
            result.add(value.substring(index, index + 2));
        }
        return result;
    }

    private String normalize(String value) {
        return value.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[\\p{P}\\p{S}\\s]", "");
    }
}

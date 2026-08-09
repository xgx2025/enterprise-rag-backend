package com.hope.enterpriserag.knowledge.retrieval;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 轻量查询词分析器，为第一版 Sparse 召回和确定性重排提供中英文统一词项。
 * 中文连续文本生成二元词，英文、编号和金额等精确片段按完整词保留。
 */
public final class RetrievalTextAnalyzer {
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[a-z0-9][a-z0-9._-]{1,31}|[\\p{IsHan}]{2,24}");
    private static final int MAX_TERMS = 16;

    private RetrievalTextAnalyzer() {
    }

    /** 将查询转换为去重且有界的检索词项。 */
    public static List<String> terms(String query) {
        String normalized = normalize(query);
        Set<String> terms = new LinkedHashSet<>();
        Matcher matcher = TOKEN_PATTERN.matcher(normalized);
        while (matcher.find() && terms.size() < MAX_TERMS) {
            String token = matcher.group();
            if (containsHan(token)) {
                if (token.length() <= 8) {
                    terms.add(token);
                }
                for (int index = 0; index < token.length() - 1 && terms.size() < MAX_TERMS; index++) {
                    terms.add(token.substring(index, index + 2));
                }
            } else {
                terms.add(token);
            }
        }
        return new ArrayList<>(terms);
    }

    /** 计算 0 到 1 的词项覆盖分数，适合候选集内相对排序。 */
    public static double lexicalScore(String query, String content) {
        if (query == null || content == null || content.isBlank()) {
            return 0.0;
        }
        String normalizedQuery = normalize(query);
        String normalizedContent = normalize(content);
        List<String> terms = terms(query);
        if (terms.isEmpty()) {
            return normalizedContent.contains(normalizedQuery) ? 1.0 : 0.0;
        }
        long matched = terms.stream().filter(normalizedContent::contains).count();
        double coverage = (double) matched / terms.size();
        double exact = !normalizedQuery.isBlank() && normalizedContent.contains(normalizedQuery) ? 1.0 : 0.0;
        return Math.min(1.0, coverage * 0.8 + exact * 0.2);
    }

    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return Normalizer.normalize(text, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static boolean containsHan(String value) {
        return value.codePoints().anyMatch(codePoint -> Character.UnicodeScript.of(codePoint)
                == Character.UnicodeScript.HAN);
    }
}

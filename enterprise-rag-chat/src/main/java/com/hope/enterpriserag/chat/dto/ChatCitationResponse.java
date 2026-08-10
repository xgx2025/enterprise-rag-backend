package com.hope.enterpriserag.chat.dto;

import java.time.LocalDate;

/** 对外展示的回答引用快照。 */
public record ChatCitationResponse(
        String sourceId,
        String documentId,
        String title,
        String version,
        LocalDate effectiveDate,
        String sectionPath,
        Integer pageNumber,
        String quote,
        Integer securityLevel,
        double score
) {
}

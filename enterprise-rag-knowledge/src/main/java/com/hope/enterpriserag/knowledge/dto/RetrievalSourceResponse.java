package com.hope.enterpriserag.knowledge.dto;

import java.time.LocalDate;

/**
 * 最终上下文中的可追溯来源。
 *
 * @param sourceId       上下文内来源编号
 * @param documentId     文档 ID
 * @param title          文档标题
 * @param version        文档版本
 * @param effectiveDate  文档生效日期
 * @param sectionPath    章节路径
 * @param pageNumber     页码
 * @param quote          实际进入上下文的父块内容
 * @param securityLevel  文档安全等级
 * @param score          最终重排分数
 */
public record RetrievalSourceResponse(
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

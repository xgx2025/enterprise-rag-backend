package com.hope.enterpriserag.knowledge.dto;

/**
 * 检索调试阶段中的分块排名项。
 *
 * @param rank          当前阶段排名，从 1 开始
 * @param score         当前阶段相关性分数
 * @param content       已通过权限校验的子块或父块正文
 * @param documentId    来源文档 ID
 * @param documentTitle 来源文档标题
 * @param version       文档版本
 * @param sectionPath   章节路径
 * @param pageNumber    页码，解析器无法识别时为空
 * @param chunkIndex    命中子块顺序
 */
public record ScoredChunkResponse(
        int rank,
        double score,
        String content,
        String documentId,
        String documentTitle,
        String version,
        String sectionPath,
        Integer pageNumber,
        int chunkIndex
) {
}

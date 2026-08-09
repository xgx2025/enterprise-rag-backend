package com.hope.enterpriserag.knowledge.vector;

import java.time.LocalDate;
import java.util.List;

/**
 * Milvus Dense、BM25 与 Hybrid Search 请求。
 * 所有权限和生命周期字段都由服务端登录态及文档治理规则生成，禁止直接信任客户端传值。
 *
 * @param tenantId             当前租户 ID
 * @param knowledgeBaseIds     允许检索的知识库 ID
 * @param documentIds          已在 MySQL 完成角色校验的文档 ID 白名单
 * @param maximumSecurityLevel 当前用户可访问的最高安全等级
 * @param effectiveDate        文档有效期判断日期
 * @param query                原始查询文本，由 Milvus BM25 Function 转换为稀疏向量
 * @param denseEnabled         是否执行稠密向量召回
 * @param sparseEnabled        是否执行 BM25 召回
 * @param denseTopK            稠密召回数量
 * @param sparseTopK           BM25 召回数量
 * @param fusionTopK           Hybrid Search 融合后保留数量
 * @param rrfK                 RRF 平滑参数
 * @param embedding            查询向量；关闭稠密召回时允许为空
 */
public record VectorSearchRequest(
        Long tenantId,
        List<Long> knowledgeBaseIds,
        List<Long> documentIds,
        int maximumSecurityLevel,
        LocalDate effectiveDate,
        String query,
        boolean denseEnabled,
        boolean sparseEnabled,
        int denseTopK,
        int sparseTopK,
        int fusionTopK,
        int rrfK,
        List<Float> embedding
) {
}

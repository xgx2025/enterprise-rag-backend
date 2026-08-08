package com.hope.enterpriserag.knowledge.vector;

import java.time.LocalDate;
import java.util.List;

/**
 * Milvus 稠密向量检索请求。
 * 所有权限和生命周期字段都由服务端登录态及文档治理规则生成，禁止直接信任客户端传值。
 *
 * @param tenantId          当前租户 ID
 * @param knowledgeBaseIds  允许检索的知识库 ID
 * @param maximumSecurityLevel 当前用户可访问的最高安全等级
 * @param effectiveDate     文档有效期判断日期
 * @param topK              最大召回数量
 * @param embedding         查询向量
 */
public record VectorSearchRequest(
        Long tenantId,
        List<Long> knowledgeBaseIds,
        int maximumSecurityLevel,
        LocalDate effectiveDate,
        int topK,
        List<Float> embedding
) {
}

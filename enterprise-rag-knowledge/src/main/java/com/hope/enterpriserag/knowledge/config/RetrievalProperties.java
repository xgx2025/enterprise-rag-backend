package com.hope.enterpriserag.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 企业检索流水线参数。
 * 数量限制用于约束外部模型调用、数据库候选规模和最终上下文大小，避免单次查询无界放大。
 */
@Data
@ConfigurationProperties(prefix = "rag.retrieval")
public class RetrievalProperties {
    private int denseTopK = 30;
    private int sparseTopK = 30;
    private int fusionTopK = 20;
    private int rerankTopK = 8;
    private int contextMaxCharacters = 12_000;
    private int maxSourcesPerDocument = 2;
}

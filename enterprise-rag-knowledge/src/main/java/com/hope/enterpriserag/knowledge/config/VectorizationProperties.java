package com.hope.enterpriserag.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 文档向量化流水线配置，控制是否启用以及单次处理的子块数量。
 * 关闭时文档会停留在 {@code WAITING_VECTOR}，便于未配置外部服务的开发环境启动。
 */
@Data
@ConfigurationProperties(prefix = "rag.vectorization")
public class VectorizationProperties {
    /** 是否在文档分块完成后自动执行向量化。 */
    private boolean enabled;
    /** 单次发送给 Embedding 服务并写入 Milvus 的最大子块数。 */
    private int batchSize = 16;
}

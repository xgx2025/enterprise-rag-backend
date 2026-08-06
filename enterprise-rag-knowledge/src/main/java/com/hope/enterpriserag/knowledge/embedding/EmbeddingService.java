package com.hope.enterpriserag.knowledge.embedding;

import java.util.List;

/**
 * 文本向量化服务契约。
 * 实现必须保持输出顺序与输入顺序一致，并在返回前校验每个向量的维度。
 */
public interface EmbeddingService {

    /**
     * 批量生成文本向量。
     *
     * @param texts 非空文本列表，正文不得写入日志
     * @return 与输入等长、顺序一致的浮点向量列表
     * @throws EmbeddingException 外部模型调用失败或返回内容不符合契约
     */
    List<List<Float>> embed(List<String> texts);

    /** @return 当前模型约定的固定向量维度 */
    int dimensions();
}

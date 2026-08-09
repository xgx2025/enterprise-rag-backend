package com.hope.enterpriserag.knowledge.retrieval;

import java.util.List;

/**
 * 父块上下文组装契约，负责去重、来源编号、文档多样性和字符预算控制。
 */
public interface ContextAssembler {
    /** 将已重排候选转换为可直接交给回答模型的受控上下文。 */
    ContextAssembly assemble(List<RetrievedChunk> candidates, int maximumSources,
                             int maximumCharacters, int maximumSourcesPerDocument);
}

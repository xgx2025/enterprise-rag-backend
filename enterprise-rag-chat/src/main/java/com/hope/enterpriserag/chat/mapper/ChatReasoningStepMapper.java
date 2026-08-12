package com.hope.enterpriserag.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hope.enterpriserag.chat.entity.ChatReasoningStep;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户可见推理摘要步骤的持久化接口。
 */
@Mapper
public interface ChatReasoningStepMapper extends BaseMapper<ChatReasoningStep> {
}

package com.hope.enterpriserag.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hope.enterpriserag.chat.entity.ChatConversation;
import org.apache.ibatis.annotations.Mapper;

/** 会话持久化接口。 */
@Mapper
public interface ChatConversationMapper extends BaseMapper<ChatConversation> {
}

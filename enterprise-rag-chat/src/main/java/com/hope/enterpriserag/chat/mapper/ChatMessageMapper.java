package com.hope.enterpriserag.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hope.enterpriserag.chat.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

/** 消息持久化接口。 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}

package com.hope.enterpriserag.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hope.enterpriserag.chat.entity.ChatCitation;
import org.apache.ibatis.annotations.Mapper;

/** 引用快照持久化接口。 */
@Mapper
public interface ChatCitationMapper extends BaseMapper<ChatCitation> {
}

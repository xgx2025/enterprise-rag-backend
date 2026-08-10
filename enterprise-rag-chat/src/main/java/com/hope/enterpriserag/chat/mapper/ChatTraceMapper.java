package com.hope.enterpriserag.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hope.enterpriserag.chat.entity.ChatTrace;
import org.apache.ibatis.annotations.Mapper;

/** 回答 Trace 持久化接口。 */
@Mapper
public interface ChatTraceMapper extends BaseMapper<ChatTrace> {
}

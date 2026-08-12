package com.hope.enterpriserag.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hope.enterpriserag.chat.entity.ChatRetrievalResult;
import org.apache.ibatis.annotations.Mapper;

/** 最终检索结果快照持久化接口。 */
@Mapper
public interface ChatRetrievalResultMapper extends BaseMapper<ChatRetrievalResult> {
}

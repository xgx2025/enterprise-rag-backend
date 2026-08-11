package com.hope.enterpriserag.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hope.enterpriserag.chat.entity.ChatRequestClaim;
import org.apache.ibatis.annotations.Mapper;

/** Chat 请求幂等占位 Mapper。 */
@Mapper
public interface ChatRequestClaimMapper extends BaseMapper<ChatRequestClaim> {
}

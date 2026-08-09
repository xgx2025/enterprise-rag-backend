package com.hope.enterpriserag.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hope.enterpriserag.knowledge.entity.KnowledgeBase;
import org.apache.ibatis.annotations.Mapper;

/** 知识库数据访问接口。 */
@Mapper
public interface KnowledgeBaseMapper extends BaseMapper<KnowledgeBase> {
}

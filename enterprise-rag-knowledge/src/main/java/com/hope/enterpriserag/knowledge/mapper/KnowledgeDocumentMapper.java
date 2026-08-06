package com.hope.enterpriserag.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hope.enterpriserag.knowledge.entity.KnowledgeDocument;
import org.apache.ibatis.annotations.Mapper;

/** 企业知识文档数据访问接口。 */
@Mapper
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocument> {
}

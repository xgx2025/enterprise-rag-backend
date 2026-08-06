package com.hope.enterpriserag.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hope.enterpriserag.knowledge.entity.DocumentChunk;
import org.apache.ibatis.annotations.Mapper;

/** 文档分块数据访问接口。 */
@Mapper
public interface DocumentChunkMapper extends BaseMapper<DocumentChunk> {
}

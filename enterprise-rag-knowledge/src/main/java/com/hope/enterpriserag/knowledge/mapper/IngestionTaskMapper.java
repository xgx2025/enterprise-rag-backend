package com.hope.enterpriserag.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hope.enterpriserag.knowledge.entity.IngestionTask;
import org.apache.ibatis.annotations.Mapper;

/** 文档摄取任务数据访问接口。 */
@Mapper
public interface IngestionTaskMapper extends BaseMapper<IngestionTask> {
}

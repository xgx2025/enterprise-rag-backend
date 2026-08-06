package com.hope.enterpriserag.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hope.enterpriserag.system.entity.SysTenant;
import org.apache.ibatis.annotations.Mapper;

/**
 * 租户表 Mapper，继承 MyBatis-Plus {@link BaseMapper} 提供基础 CRUD。
 */
@Mapper
public interface SysTenantMapper extends BaseMapper<SysTenant> {
}

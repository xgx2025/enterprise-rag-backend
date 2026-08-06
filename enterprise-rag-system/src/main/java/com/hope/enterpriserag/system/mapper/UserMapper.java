package com.hope.enterpriserag.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hope.enterpriserag.system.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户表 Mapper，继承 MyBatis-Plus {@link BaseMapper} 提供基础 CRUD。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}

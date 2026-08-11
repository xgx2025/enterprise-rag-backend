package com.hope.enterpriserag.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hope.enterpriserag.system.entity.UserAccessProfile;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户知识访问配置 Mapper，仅供系统主数据服务解析服务端授权信息。
 */
@Mapper
public interface UserAccessProfileMapper extends BaseMapper<UserAccessProfile> {
}

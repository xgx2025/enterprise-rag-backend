package com.hope.enterpriserag.system.service;

import cn.hutool.core.util.IdUtil;
import com.hope.enterpriserag.system.entity.SysTenant;
import com.hope.enterpriserag.system.mapper.SysTenantMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 租户领域服务，封装租户相关的数据库操作。
 */
@Slf4j
@Service
public class TenantService {

    private final SysTenantMapper tenantMapper;

    public TenantService(SysTenantMapper tenantMapper) {
        this.tenantMapper = tenantMapper;
    }

    /** 创建新租户，自动填充雪花 ID 和时间字段 */
    public void create(SysTenant tenant) {
        if (tenant.getId() == null) {
            tenant.setId(IdUtil.getSnowflakeNextId());
        }
        LocalDateTime now = LocalDateTime.now();
        tenant.setCreatedAt(now);
        tenant.setUpdatedAt(now);
        tenantMapper.insert(tenant);
        log.info("租户创建成功: tenantId={}, tenantCode={}", tenant.getId(), tenant.getTenantCode());
    }
}

-- 种子租户数据，在 init.sql 之后手动执行。
-- ID 由 Hutool IdUtil.getSnowflakeNextId() 预生成。
INSERT IGNORE INTO sys_tenant (id, tenant_code, tenant_name, status) VALUES
    (2085195827416526848, 'DEFAULT',  '默认租户', 1),
    (2085195827546550272, 'INTERNAL', '内部租户', 1),
    (2085195827663990784, 'DEMO',     '演示租户', 1);

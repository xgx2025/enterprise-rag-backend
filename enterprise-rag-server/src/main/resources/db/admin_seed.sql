-- 种子管理员用户，在 tenant_seed.sql 之后手动执行。
-- ID 由 Hutool IdUtil.getSnowflakeNextId() 预生成。
-- 密码为 BCrypt("admin123")。
INSERT INTO sys_user (id, tenant_id, username, password, real_name, status)
SELECT
    2085198999769886720,
    id,
    'admin',
    '$2a$10$PDqmkHNPebffwP2OS1FjXOy4TRex30m8WSkm4pTJ52n9KUan3vGLy',
    '系统管理员',
    1
FROM sys_tenant
WHERE tenant_code = 'DEFAULT'
ON DUPLICATE KEY UPDATE
    password   = '$2a$10$PDqmkHNPebffwP2OS1FjXOy4TRex30m8WSkm4pTJ52n9KUan3vGLy',
    real_name  = '系统管理员',
    status     = 1;

INSERT INTO sys_user_access (user_id, tenant_id, roles, maximum_security_level)
SELECT id, tenant_id, 'ROLE_USER,ROLE_KB_ADMIN', 3
FROM sys_user
WHERE id = 2085198999769886720
ON DUPLICATE KEY UPDATE
    tenant_id = VALUES(tenant_id),
    roles = 'ROLE_USER,ROLE_KB_ADMIN',
    maximum_security_level = 3;

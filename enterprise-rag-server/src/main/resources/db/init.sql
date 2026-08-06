CREATE TABLE IF NOT EXISTS sys_tenant (
    id              BIGINT UNSIGNED NOT NULL COMMENT '租户ID（Hutool 雪花算法生成）',
    tenant_code     VARCHAR(64) NOT NULL COMMENT '租户编码',
    tenant_name     VARCHAR(128) NOT NULL COMMENT '租户名称',
    status          TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0禁用，1正常',
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                    ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_code (tenant_code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '企业租户表';

-- CREATE TABLE IF NOT EXISTS does not update tables created by older versions.
ALTER TABLE sys_tenant
    MODIFY COLUMN id BIGINT UNSIGNED NOT NULL COMMENT '租户ID（Hutool 雪花算法生成）';

CREATE TABLE IF NOT EXISTS sys_user (
    id              BIGINT UNSIGNED NOT NULL COMMENT '用户ID（Hutool 雪花算法生成）',
    tenant_id       BIGINT UNSIGNED NOT NULL COMMENT '所属租户ID',
    username        VARCHAR(64) NOT NULL COMMENT '用户名',
    password        VARCHAR(256) NOT NULL COMMENT '密码（BCrypt加密）',
    real_name       VARCHAR(64) DEFAULT NULL COMMENT '真实姓名',
    email           VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    phone           VARCHAR(32) DEFAULT NULL COMMENT '手机号',
    status          TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0禁用，1正常',
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                    ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    KEY idx_tenant_id (tenant_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '系统用户表';

ALTER TABLE sys_user
    MODIFY COLUMN id BIGINT UNSIGNED NOT NULL COMMENT '用户ID（Hutool 雪花算法生成）';

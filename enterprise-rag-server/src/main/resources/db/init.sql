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

CREATE TABLE IF NOT EXISTS kb_knowledge_base (
    id                  BIGINT UNSIGNED NOT NULL COMMENT '知识库ID',
    tenant_id           BIGINT UNSIGNED NOT NULL COMMENT '租户ID',
    name                VARCHAR(128) NOT NULL COMMENT '知识库名称',
    description         VARCHAR(500) DEFAULT NULL COMMENT '知识库说明',
    department          VARCHAR(64) DEFAULT NULL COMMENT '所属部门',
    security_level      TINYINT NOT NULL DEFAULT 1 COMMENT '默认安全等级：1公开 2内部 3机密',
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
    created_by          BIGINT UNSIGNED NOT NULL,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_kb_tenant_name (tenant_id, name),
    KEY idx_kb_tenant_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='企业知识库';

CREATE TABLE IF NOT EXISTS kb_document (
    id                  BIGINT UNSIGNED NOT NULL COMMENT '文档ID',
    tenant_id           BIGINT UNSIGNED NOT NULL COMMENT '租户ID',
    knowledge_base_id   BIGINT UNSIGNED NOT NULL COMMENT '知识库ID',
    title               VARCHAR(256) NOT NULL COMMENT '文档标题',
    file_name           VARCHAR(256) NOT NULL COMMENT '原始文件名',
    file_type           VARCHAR(32) NOT NULL COMMENT '文件扩展名',
    file_size           BIGINT UNSIGNED NOT NULL COMMENT '文件字节数',
    content_type        VARCHAR(128) DEFAULT NULL,
    storage_provider    VARCHAR(32) NOT NULL DEFAULT 'ALIYUN_OSS',
    bucket_name         VARCHAR(128) NOT NULL,
    object_key          VARCHAR(512) NOT NULL,
    content_hash        CHAR(64) NOT NULL COMMENT 'SHA-256',
    version             VARCHAR(64) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PROCESSING' COMMENT 'DRAFT/PROCESSING/READY/ACTIVE/EXPIRED/FAILED/ARCHIVED',
    department          VARCHAR(64) NOT NULL,
    security_level      TINYINT NOT NULL DEFAULT 1,
    allowed_roles       JSON DEFAULT NULL,
    authority_level     TINYINT NOT NULL DEFAULT 1,
    effective_from      DATE DEFAULT NULL,
    effective_to        DATE DEFAULT NULL,
    replaces_document_id BIGINT UNSIGNED DEFAULT NULL,
    parse_status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    embedding_status    VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/COMPLETED/FAILED',
    process_progress    TINYINT UNSIGNED NOT NULL DEFAULT 0,
    failure_stage       VARCHAR(64) DEFAULT NULL,
    failure_message     VARCHAR(500) DEFAULT NULL,
    chunk_count         INT UNSIGNED NOT NULL DEFAULT 0,
    created_by          BIGINT UNSIGNED NOT NULL,
    deleted             TINYINT NOT NULL DEFAULT 0,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_doc_object_key (object_key),
    KEY idx_doc_tenant_kb (tenant_id, knowledge_base_id, deleted),
    KEY idx_doc_tenant_status (tenant_id, status, deleted),
    KEY idx_doc_content_hash (tenant_id, content_hash, deleted),
    KEY idx_doc_title_version (tenant_id, knowledge_base_id, title, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档';

CREATE TABLE IF NOT EXISTS kb_chunk (
    id                  BIGINT UNSIGNED NOT NULL COMMENT '分块ID',
    tenant_id           BIGINT UNSIGNED NOT NULL COMMENT '租户ID',
    document_id         BIGINT UNSIGNED NOT NULL COMMENT '文档ID',
    parent_chunk_id     BIGINT UNSIGNED DEFAULT NULL,
    chunk_index         INT UNSIGNED NOT NULL,
    content             MEDIUMTEXT NOT NULL,
    section_path        VARCHAR(500) DEFAULT NULL,
    page_number         INT DEFAULT NULL,
    token_count         INT UNSIGNED NOT NULL DEFAULT 0,
    embedding_status    VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/COMPLETED/FAILED',
    metadata_json       JSON DEFAULT NULL,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_chunk_document_index (document_id, chunk_index),
    KEY idx_chunk_tenant_document (tenant_id, document_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档分块';

CREATE TABLE IF NOT EXISTS kb_ingestion_task (
    id                  BIGINT UNSIGNED NOT NULL COMMENT '任务ID',
    tenant_id           BIGINT UNSIGNED NOT NULL,
    document_id         BIGINT UNSIGNED NOT NULL,
    task_type           VARCHAR(32) NOT NULL DEFAULT 'PARSE_AND_CHUNK' COMMENT 'PARSE_AND_CHUNK/VECTORIZE',
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/WAITING_VECTOR/RUNNING/SUCCEEDED/FAILED',
    progress            TINYINT UNSIGNED NOT NULL DEFAULT 0,
    current_stage       VARCHAR(64) DEFAULT NULL,
    retry_count         INT UNSIGNED NOT NULL DEFAULT 0,
    error_message       VARCHAR(500) DEFAULT NULL,
    started_at          DATETIME(3) DEFAULT NULL,
    finished_at         DATETIME(3) DEFAULT NULL,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_task_document (document_id, created_at),
    KEY idx_task_tenant_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档入库任务';

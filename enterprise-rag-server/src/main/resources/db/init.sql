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
    UNIQUE KEY uk_user_email (email),
    KEY idx_tenant_id (tenant_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '系统用户表';

CREATE TABLE IF NOT EXISTS sys_user_access (
    user_id                 BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    tenant_id               BIGINT UNSIGNED NOT NULL COMMENT '所属租户ID',
    roles                   VARCHAR(512) NOT NULL DEFAULT 'ROLE_USER' COMMENT '服务端角色编码，逗号分隔',
    maximum_security_level  TINYINT NOT NULL DEFAULT 1 COMMENT '最高知识安全等级：1公开 2内部 3机密',
    created_at              DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at              DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (user_id),
    KEY idx_user_access_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户知识访问配置';

-- 为升级前已有用户补齐最小权限；固定种子管理员 ID 获得知识治理权限。
INSERT IGNORE INTO sys_user_access (user_id, tenant_id, roles, maximum_security_level)
SELECT id, tenant_id, 'ROLE_USER', 1 FROM sys_user;

INSERT INTO sys_user_access (user_id, tenant_id, roles, maximum_security_level)
SELECT id, tenant_id, 'ROLE_USER,ROLE_KB_ADMIN', 3
FROM sys_user
WHERE id = 2085198999769886720
ON DUPLICATE KEY UPDATE
    tenant_id = VALUES(tenant_id),
    roles = 'ROLE_USER,ROLE_KB_ADMIN',
    maximum_security_level = 3;

ALTER TABLE sys_user
    MODIFY COLUMN id BIGINT UNSIGNED NOT NULL COMMENT '用户ID（Hutool 雪花算法生成）';

-- 兼容已经创建的数据库：邮箱是无租户参数登录时的全局唯一标识。
SET @email_index_sql = IF(
    EXISTS(
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'sys_user'
          AND index_name = 'uk_user_email'
    ),
    'SELECT 1',
    'ALTER TABLE sys_user ADD UNIQUE KEY uk_user_email (email)'
);
PREPARE email_index_statement FROM @email_index_sql;
EXECUTE email_index_statement;
DEALLOCATE PREPARE email_index_statement;

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
    task_type           VARCHAR(32) NOT NULL DEFAULT 'PARSE_AND_CHUNK' COMMENT 'PARSE_AND_CHUNK/VECTORIZE/REINDEX_*',
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

CREATE TABLE IF NOT EXISTS chat_conversation (
    id                  BIGINT UNSIGNED NOT NULL COMMENT '会话ID（雪花算法）',
    tenant_id           BIGINT UNSIGNED NOT NULL COMMENT '租户ID',
    user_id             BIGINT UNSIGNED NOT NULL COMMENT '会话所属用户ID',
    title               VARCHAR(160) NOT NULL DEFAULT '新对话',
    knowledge_base_ids  JSON NOT NULL COMMENT '知识库ID范围快照',
    retrieval_strategy  JSON NOT NULL COMMENT '检索策略快照',
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DELETED',
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_chat_conversation_owner (tenant_id, user_id, status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='可信问答会话';

CREATE TABLE IF NOT EXISTS chat_message (
    id                  BIGINT UNSIGNED NOT NULL COMMENT '消息ID（雪花算法）',
    tenant_id           BIGINT UNSIGNED NOT NULL COMMENT '租户ID',
    conversation_id     BIGINT UNSIGNED NOT NULL COMMENT '会话ID',
    parent_message_id   BIGINT UNSIGNED DEFAULT NULL COMMENT '助手消息对应的用户消息ID',
    regenerated_from_id BIGINT UNSIGNED DEFAULT NULL COMMENT '被重新生成替代的助手消息ID',
    role                VARCHAR(20) NOT NULL COMMENT 'USER/ASSISTANT',
    content             MEDIUMTEXT NOT NULL,
    status              VARCHAR(20) NOT NULL COMMENT 'RUNNING/COMPLETED/FAILED/CANCELLED/SUPERSEDED',
    answer_status       VARCHAR(20) DEFAULT NULL COMMENT 'SUPPORTED/PARTIAL/INSUFFICIENT',
    trace_id            VARCHAR(64) DEFAULT NULL,
    error_code          VARCHAR(64) DEFAULT NULL,
    error_message       VARCHAR(500) DEFAULT NULL,
    retrieval_stats     JSON DEFAULT NULL,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_chat_message_conversation (tenant_id, conversation_id, created_at),
    KEY idx_chat_message_parent (parent_message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='可信问答消息';

CREATE TABLE IF NOT EXISTS chat_citation (
    id                  BIGINT UNSIGNED NOT NULL COMMENT '引用ID（雪花算法）',
    tenant_id           BIGINT UNSIGNED NOT NULL COMMENT '租户ID',
    message_id          BIGINT UNSIGNED NOT NULL COMMENT '助手消息ID',
    source_id           VARCHAR(20) NOT NULL COMMENT '回答内来源编号，如S1',
    document_id         BIGINT UNSIGNED NOT NULL COMMENT '来源文档ID',
    title               VARCHAR(256) NOT NULL,
    version             VARCHAR(64) DEFAULT NULL,
    effective_date      DATE DEFAULT NULL,
    section_path        VARCHAR(500) DEFAULT NULL,
    page_number         INT DEFAULT NULL,
    quote               MEDIUMTEXT NOT NULL COMMENT '实际进入回答上下文的引文快照',
    security_level      TINYINT NOT NULL DEFAULT 1,
    score               DOUBLE NOT NULL DEFAULT 0,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_chat_citation_source (message_id, source_id),
    KEY idx_chat_citation_document (tenant_id, document_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='回答引用快照';

CREATE TABLE IF NOT EXISTS chat_retrieval_result (
    id                  BIGINT UNSIGNED NOT NULL COMMENT '最终检索结果ID（雪花算法）',
    tenant_id           BIGINT UNSIGNED NOT NULL COMMENT '租户ID',
    message_id          BIGINT UNSIGNED NOT NULL COMMENT '助手消息ID',
    rank_number         INT UNSIGNED NOT NULL COMMENT '最终上下文顺序，从1开始',
    source_id           VARCHAR(20) NOT NULL COMMENT '上下文来源编号，如S1',
    document_id         BIGINT UNSIGNED NOT NULL COMMENT '来源文档ID',
    title               VARCHAR(256) NOT NULL,
    version             VARCHAR(64) DEFAULT NULL,
    effective_date      DATE DEFAULT NULL,
    section_path        VARCHAR(500) DEFAULT NULL,
    page_number         INT DEFAULT NULL,
    content             MEDIUMTEXT NOT NULL COMMENT '实际进入模型上下文的片段快照',
    security_level      TINYINT NOT NULL DEFAULT 1,
    score               DOUBLE NOT NULL DEFAULT 0 COMMENT '最终重排相关性分数',
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_chat_retrieval_rank (message_id, rank_number),
    UNIQUE KEY uk_chat_retrieval_source (message_id, source_id),
    KEY idx_chat_retrieval_document (tenant_id, document_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问答最终检索结果快照';

CREATE TABLE IF NOT EXISTS chat_reasoning_step (
    id                  BIGINT UNSIGNED NOT NULL COMMENT '推理摘要步骤ID（雪花算法）',
    tenant_id           BIGINT UNSIGNED NOT NULL COMMENT '租户ID',
    message_id          BIGINT UNSIGNED NOT NULL COMMENT '助手消息ID',
    sequence_number     INT UNSIGNED NOT NULL COMMENT '消息内展示顺序，从0开始',
    step_key            VARCHAR(64) NOT NULL COMMENT '稳定阶段标识',
    title               VARCHAR(128) NOT NULL COMMENT '面向用户的阶段标题',
    detail              VARCHAR(500) NOT NULL COMMENT '不包含Prompt和正文的安全摘要',
    status              VARCHAR(20) NOT NULL COMMENT 'RUNNING/COMPLETED/FAILED',
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_chat_reasoning_sequence (message_id, sequence_number),
    KEY idx_chat_reasoning_owner (tenant_id, message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户可见的问答推理摘要';

CREATE TABLE IF NOT EXISTS chat_trace (
    id                  BIGINT UNSIGNED NOT NULL COMMENT 'Trace记录ID（雪花算法）',
    trace_id            VARCHAR(64) NOT NULL COMMENT '检索链路Trace ID',
    tenant_id           BIGINT UNSIGNED NOT NULL,
    user_id             BIGINT UNSIGNED NOT NULL,
    conversation_id     BIGINT UNSIGNED NOT NULL,
    user_message_id     BIGINT UNSIGNED NOT NULL,
    assistant_message_id BIGINT UNSIGNED NOT NULL,
    knowledge_base_ids  JSON NOT NULL,
    retrieval_strategy  JSON NOT NULL,
    retrieval_stats     JSON NOT NULL,
    retrieval_timing    JSON NOT NULL,
    model               VARCHAR(128) NOT NULL,
    answer_status       VARCHAR(20) NOT NULL,
    prompt_tokens       INT UNSIGNED NOT NULL DEFAULT 0,
    completion_tokens   INT UNSIGNED NOT NULL DEFAULT 0,
    total_tokens        INT UNSIGNED NOT NULL DEFAULT 0,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_chat_trace_id (trace_id),
    UNIQUE KEY uk_chat_trace_message (assistant_message_id),
    KEY idx_chat_trace_owner (tenant_id, user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='可信问答检索与生成Trace';

CREATE TABLE IF NOT EXISTS chat_request_claim (
    id                  BIGINT UNSIGNED NOT NULL COMMENT '幂等占位ID',
    tenant_id           BIGINT UNSIGNED NOT NULL,
    user_id             BIGINT UNSIGNED NOT NULL,
    request_id          VARCHAR(64) NOT NULL COMMENT '客户端请求幂等键',
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_chat_request_owner (tenant_id, user_id, request_id),
    KEY idx_chat_request_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Chat请求幂等占位';

-- 失败信息只属于当前失败态，清理早期版本因 null 更新策略遗留的历史错误提示。
UPDATE kb_document
SET failure_stage = NULL,
    failure_message = NULL
WHERE status <> 'FAILED'
  AND (failure_stage IS NOT NULL OR failure_message IS NOT NULL);

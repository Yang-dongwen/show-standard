-- Flyway V1 · MySQL 基线（cloud profile）
-- 终态结构与 sqlite/V1__baseline.sql 对齐；索引写在表定义内
-- 禁止修改已发布脚本；增量请加 V2__xxx.sql

CREATE TABLE IF NOT EXISTS t_tenant (
    id BIGINT PRIMARY KEY,
    tenant_key VARCHAR(32) NOT NULL,
    shop_name VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    plan_code VARCHAR(32) NOT NULL DEFAULT 'free',
    max_customers INT NOT NULL DEFAULT 5000,
    max_employees INT NOT NULL DEFAULT 50,
    tags VARCHAR(255) NOT NULL DEFAULT '',
    remark VARCHAR(500) NOT NULL DEFAULT '',
    expire_at TIMESTAMP NULL DEFAULT NULL,
    write_mode VARCHAR(16) NOT NULL DEFAULT 'normal',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tenant_key (tenant_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_saas_plan (
    code VARCHAR(32) PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    max_customers INT NOT NULL DEFAULT 500,
    max_employees INT NOT NULL DEFAULT 10,
    trial_days INT NOT NULL DEFAULT 0,
    description VARCHAR(255) NOT NULL DEFAULT '',
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'active'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_saas_audit_log (
    id BIGINT PRIMARY KEY,
    operator VARCHAR(64) NOT NULL,
    action VARCHAR(64) NOT NULL,
    target_type VARCHAR(64) NOT NULL DEFAULT '',
    target_id VARCHAR(64) NOT NULL DEFAULT '',
    detail VARCHAR(500) NOT NULL DEFAULT '',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_saas_audit_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_saas_announcement (
    id BIGINT PRIMARY KEY,
    title VARCHAR(128) NOT NULL,
    content VARCHAR(1000) NOT NULL,
    scope VARCHAR(16) NOT NULL DEFAULT 'all',
    tenant_id BIGINT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    created_by VARCHAR(64) NOT NULL DEFAULT '',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_saas_announcement_status (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_manager (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    nickname VARCHAR(64) NOT NULL,
    role VARCHAR(16) NOT NULL DEFAULT 'owner',
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_manager_username (username),
    UNIQUE KEY uk_manager_tenant_username (tenant_id, username),
    KEY idx_manager_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_invite_code (
    id BIGINT PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    max_uses INT NOT NULL DEFAULT 1,
    used_count INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    expire_at TIMESTAMP NULL DEFAULT NULL,
    note VARCHAR(255) NOT NULL DEFAULT '',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_invite_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_platform_admin (
    id BIGINT PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    nickname VARCHAR(64) NOT NULL DEFAULT '平台管理员',
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_platform_admin_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_manager_wx_bind (
    id BIGINT PRIMARY KEY,
    manager_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    openid VARCHAR(64) NOT NULL,
    unionid VARCHAR(64) NULL,
    app_id VARCHAR(64) NOT NULL DEFAULT '',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_manager_wx_openid (openid),
    UNIQUE KEY uk_manager_wx_manager (manager_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_saas_billing_record (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    plan_code VARCHAR(32) NOT NULL DEFAULT '',
    days INT NOT NULL DEFAULT 0,
    amount DECIMAL(12, 2) NOT NULL DEFAULT 0,
    note VARCHAR(255) NOT NULL DEFAULT '',
    operator VARCHAR(64) NOT NULL DEFAULT '',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_saas_billing_tenant (tenant_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_customer (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    phone VARCHAR(32) NOT NULL,
    verify_code VARCHAR(4) NOT NULL,
    remark VARCHAR(255) NOT NULL DEFAULT '',
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_customer_tenant_phone (tenant_id, phone),
    KEY idx_customer_tenant_created (tenant_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_employee (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_employee_tenant_created (tenant_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_service_type (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    price DECIMAL(12, 2) NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_service_tenant_name (tenant_id, name),
    KEY idx_service_tenant_created (tenant_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_recharge_record (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    remark VARCHAR(255) NOT NULL DEFAULT '',
    status VARCHAR(16) NOT NULL DEFAULT 'normal',
    related_id BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_recharge_tenant_customer (tenant_id, customer_id),
    KEY idx_recharge_tenant_created (tenant_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_consume_record (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    service_type_id BIGINT NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    remark VARCHAR(255) NOT NULL DEFAULT '',
    status VARCHAR(16) NOT NULL DEFAULT 'normal',
    related_id BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_consume_tenant_customer (tenant_id, customer_id),
    KEY idx_consume_tenant_employee (tenant_id, employee_id),
    KEY idx_consume_tenant_created (tenant_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_audit_log (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    action VARCHAR(64) NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    entity_id VARCHAR(64) NOT NULL,
    detail VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_audit_tenant_created (tenant_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_account (
    customer_id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    balance DECIMAL(12, 2) NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_account_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_tenant_setting (
    tenant_id BIGINT NOT NULL,
    setting_key VARCHAR(64) NOT NULL,
    setting_value VARCHAR(500) NOT NULL DEFAULT '',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_schema_meta (
    meta_key VARCHAR(64) PRIMARY KEY,
    meta_value VARCHAR(255) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

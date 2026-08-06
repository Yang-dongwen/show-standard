CREATE TABLE IF NOT EXISTS t_manager (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL UNIQUE,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    nickname VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_manager_tenant_username UNIQUE (tenant_id, username)
);

CREATE TABLE IF NOT EXISTS t_customer (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    phone VARCHAR(32) NOT NULL,
    verify_code VARCHAR(4) NOT NULL,
    remark VARCHAR(255) NOT NULL DEFAULT '',
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_customer_tenant_phone UNIQUE (tenant_id, phone)
);
CREATE INDEX IF NOT EXISTS idx_customer_tenant_created ON t_customer(tenant_id, created_at DESC);

CREATE TABLE IF NOT EXISTS t_employee (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_employee_tenant_created ON t_employee(tenant_id, created_at DESC);

CREATE TABLE IF NOT EXISTS t_service_type (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    price NUMERIC(12, 2) NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_service_tenant_name UNIQUE (tenant_id, name)
);
CREATE INDEX IF NOT EXISTS idx_service_tenant_created ON t_service_type(tenant_id, created_at DESC);

CREATE TABLE IF NOT EXISTS t_recharge_record (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    remark VARCHAR(255) NOT NULL DEFAULT '',
    status VARCHAR(16) NOT NULL DEFAULT 'normal',
    related_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_recharge_tenant_customer ON t_recharge_record(tenant_id, customer_id);
CREATE INDEX IF NOT EXISTS idx_recharge_tenant_created ON t_recharge_record(tenant_id, created_at DESC);

CREATE TABLE IF NOT EXISTS t_consume_record (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    service_type_id BIGINT NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    remark VARCHAR(255) NOT NULL DEFAULT '',
    status VARCHAR(16) NOT NULL DEFAULT 'normal',
    related_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_consume_tenant_customer ON t_consume_record(tenant_id, customer_id);
CREATE INDEX IF NOT EXISTS idx_consume_tenant_employee ON t_consume_record(tenant_id, employee_id);
CREATE INDEX IF NOT EXISTS idx_consume_tenant_created ON t_consume_record(tenant_id, created_at DESC);

CREATE TABLE IF NOT EXISTS t_audit_log (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    action VARCHAR(64) NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    entity_id VARCHAR(64) NOT NULL,
    detail VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_audit_tenant_created ON t_audit_log(tenant_id, created_at DESC);

-- 账户余额快照（原子扣减/充值的单一真相源）
CREATE TABLE IF NOT EXISTS t_account (
    customer_id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    balance NUMERIC(12, 2) NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_account_tenant ON t_account(tenant_id);

-- 租户设置（今日目标等）
CREATE TABLE IF NOT EXISTS t_tenant_setting (
    tenant_id BIGINT NOT NULL,
    setting_key VARCHAR(64) NOT NULL,
    setting_value VARCHAR(500) NOT NULL DEFAULT '',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, setting_key)
);

-- schema 迁移版本
CREATE TABLE IF NOT EXISTS t_schema_meta (
    meta_key VARCHAR(64) PRIMARY KEY,
    meta_value VARCHAR(255) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

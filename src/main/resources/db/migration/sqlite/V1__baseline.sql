-- Flyway V1 · SQLite 基线（本地 desktop）
-- 终态结构（含多账号 role、write_mode、账单、微信绑定等）
-- 禁止修改已发布脚本；增量请加 V2__xxx.sql

-- SaaS 租户（门店）
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
    expire_at TIMESTAMP,
    write_mode VARCHAR(16) NOT NULL DEFAULT 'normal',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_tenant_key ON t_tenant(tenant_key);

-- SaaS 套餐目录
CREATE TABLE IF NOT EXISTS t_saas_plan (
    code VARCHAR(32) PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    max_customers INT NOT NULL DEFAULT 500,
    max_employees INT NOT NULL DEFAULT 10,
    trial_days INT NOT NULL DEFAULT 0,
    description VARCHAR(255) NOT NULL DEFAULT '',
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'active'
);

-- SaaS 运营审计
CREATE TABLE IF NOT EXISTS t_saas_audit_log (
    id BIGINT PRIMARY KEY,
    operator VARCHAR(64) NOT NULL,
    action VARCHAR(64) NOT NULL,
    target_type VARCHAR(64) NOT NULL DEFAULT '',
    target_id VARCHAR(64) NOT NULL DEFAULT '',
    detail VARCHAR(500) NOT NULL DEFAULT '',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_saas_audit_created ON t_saas_audit_log(created_at DESC);

-- SaaS 公告（C 端可见）
CREATE TABLE IF NOT EXISTS t_saas_announcement (
    id BIGINT PRIMARY KEY,
    title VARCHAR(128) NOT NULL,
    content VARCHAR(1000) NOT NULL,
    scope VARCHAR(16) NOT NULL DEFAULT 'all',
    tenant_id BIGINT,
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    created_by VARCHAR(64) NOT NULL DEFAULT '',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_saas_announcement_status ON t_saas_announcement(status, created_at DESC);

-- 门店登录账号（一店可多账号：店长/收银/店员）
CREATE TABLE IF NOT EXISTS t_manager (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    nickname VARCHAR(64) NOT NULL,
    role VARCHAR(16) NOT NULL DEFAULT 'owner',
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_manager_tenant_username UNIQUE (tenant_id, username)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_manager_username ON t_manager(username);
CREATE INDEX IF NOT EXISTS idx_manager_tenant ON t_manager(tenant_id);

-- 邀请码（SaaS 开店）
CREATE TABLE IF NOT EXISTS t_invite_code (
    id BIGINT PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    max_uses INT NOT NULL DEFAULT 1,
    used_count INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    expire_at TIMESTAMP,
    note VARCHAR(255) NOT NULL DEFAULT '',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_invite_code ON t_invite_code(code);

-- 平台管理员
CREATE TABLE IF NOT EXISTS t_platform_admin (
    id BIGINT PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    nickname VARCHAR(64) NOT NULL DEFAULT '平台管理员',
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_platform_admin_username ON t_platform_admin(username);

-- 店长微信绑定（商家小程序）
CREATE TABLE IF NOT EXISTS t_manager_wx_bind (
    id BIGINT PRIMARY KEY,
    manager_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    openid VARCHAR(64) NOT NULL,
    unionid VARCHAR(64),
    app_id VARCHAR(64) NOT NULL DEFAULT '',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_manager_wx_openid ON t_manager_wx_bind(openid);
CREATE UNIQUE INDEX IF NOT EXISTS uk_manager_wx_manager ON t_manager_wx_bind(manager_id);

-- SaaS 人工续期账单
CREATE TABLE IF NOT EXISTS t_saas_billing_record (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    plan_code VARCHAR(32) NOT NULL DEFAULT '',
    days INT NOT NULL DEFAULT 0,
    amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    note VARCHAR(255) NOT NULL DEFAULT '',
    operator VARCHAR(64) NOT NULL DEFAULT '',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_saas_billing_tenant ON t_saas_billing_record(tenant_id, created_at DESC);

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

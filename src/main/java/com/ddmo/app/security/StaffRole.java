package com.ddmo.app.security;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * C 端门店登录角色：店长 / 收银 / 店员。
 */
public enum StaffRole {

    OWNER("owner", "店长"),
    CASHIER("cashier", "收银员"),
    STAFF("staff", "店员");

    private final String code;
    private final String label;

    StaffRole(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    public boolean isOwner() {
        return this == OWNER;
    }

    public static StaffRole fromCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return OWNER;
        }
        String c = raw.trim().toLowerCase(Locale.ROOT);
        // 兼容历史 admin / manager
        if ("admin".equals(c) || "manager".equals(c)) {
            return OWNER;
        }
        for (StaffRole r : values()) {
            if (r.code.equals(c)) {
                return r;
            }
        }
        return OWNER;
    }

    public static boolean isValidAssignable(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String c = raw.trim().toLowerCase(Locale.ROOT);
        return CASHIER.code.equals(c) || STAFF.code.equals(c) || OWNER.code.equals(c);
    }

    /** 可分配给店员账号的角色（不可自建第二个店长时由业务限制） */
    public Set<String> permissions() {
        return switch (this) {
            case OWNER -> allPermissions();
            case CASHIER -> Set.of(
                Perm.DASHBOARD, Perm.CUSTOMERS, Perm.CUSTOMERS_WRITE,
                Perm.TRANSACTIONS, Perm.RECHARGE, Perm.CONSUME, Perm.REVERSE,
                Perm.REPORTS
            );
            case STAFF -> Set.of(
                Perm.DASHBOARD, Perm.CUSTOMERS, Perm.CUSTOMERS_WRITE,
                Perm.TRANSACTIONS, Perm.CONSUME
            );
        };
    }

    private static Set<String> allPermissions() {
        Set<String> s = new LinkedHashSet<>();
        s.add(Perm.DASHBOARD);
        s.add(Perm.CUSTOMERS);
        s.add(Perm.CUSTOMERS_WRITE);
        s.add(Perm.VIEW_VERIFY_CODE);
        s.add(Perm.TRANSACTIONS);
        s.add(Perm.RECHARGE);
        s.add(Perm.CONSUME);
        s.add(Perm.REVERSE);
        s.add(Perm.EMPLOYEES);
        s.add(Perm.REPORTS);
        s.add(Perm.AUDIT);
        s.add(Perm.SETTINGS);
        s.add(Perm.BACKUP);
        s.add(Perm.STAFF_ACCOUNTS);
        return Collections.unmodifiableSet(s);
    }

    /** 权限码常量 */
    public static final class Perm {
        public static final String DASHBOARD = "dashboard";
        public static final String CUSTOMERS = "customers";
        public static final String CUSTOMERS_WRITE = "customers:write";
        /** 列表/详情中查看明文校验码（默认仅店长） */
        public static final String VIEW_VERIFY_CODE = "customers:verify";
        public static final String TRANSACTIONS = "transactions";
        public static final String RECHARGE = "recharge";
        public static final String CONSUME = "consume";
        public static final String REVERSE = "reverse";
        public static final String EMPLOYEES = "employees";
        public static final String REPORTS = "reports";
        public static final String AUDIT = "audit";
        public static final String SETTINGS = "settings";
        public static final String BACKUP = "backup";
        public static final String STAFF_ACCOUNTS = "staff_accounts";

        private Perm() {
        }
    }
}

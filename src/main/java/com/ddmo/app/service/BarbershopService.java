package com.ddmo.app.service;

import com.ddmo.app.dto.ConsumeRequest;
import com.ddmo.app.dto.CustomerRequest;
import com.ddmo.app.dto.EmployeeRequest;
import com.ddmo.app.dto.RechargeRequest;
import com.ddmo.app.dto.ServiceTypeRequest;
import com.ddmo.app.model.AuditLog;
import com.ddmo.app.model.ConsumeRecord;
import com.ddmo.app.model.Customer;
import com.ddmo.app.model.Employee;
import com.ddmo.app.model.RechargeRecord;
import com.ddmo.app.model.ServiceType;
import com.ddmo.app.config.DbDialect;
import com.ddmo.app.security.StaffRole;
import com.ddmo.app.security.TenantContext;
import com.ddmo.app.util.SnowflakeIdGenerator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class BarbershopService {

    private final JdbcTemplate jdbcTemplate;
    private final SnowflakeIdGenerator idGenerator;
    private final TenantAccessService tenantAccessService;
    private final RolePermissionService rolePermissionService;
    private final DbDialect dbDialect;
    /** 租户级写锁，防止余额并发超扣（配合账户表原子 UPDATE） */
    private final ConcurrentHashMap<Long, ReentrantLock> tenantLocks = new ConcurrentHashMap<>();

    public BarbershopService(
        JdbcTemplate jdbcTemplate,
        SnowflakeIdGenerator idGenerator,
        TenantAccessService tenantAccessService,
        RolePermissionService rolePermissionService,
        DbDialect dbDialect
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = idGenerator;
        this.tenantAccessService = tenantAccessService;
        this.rolePermissionService = rolePermissionService;
        this.dbDialect = dbDialect;
    }

    public List<Customer> listCustomers(String keyword) {
        long tenantId = tenantId();
        String k = safeKeyword(keyword);
        return jdbcTemplate.query("""
                SELECT c.id, c.name, c.phone, c.verify_code, c.remark, c.status, c.created_at,
                       COALESCE(a.balance, 0) AS balance
                FROM t_customer c
                LEFT JOIN t_account a ON a.customer_id = c.id AND a.tenant_id = c.tenant_id
                WHERE c.tenant_id = ? AND (? = '' OR LOWER(c.name) LIKE LOWER(?) OR LOWER(c.phone) LIKE LOWER(?))
                ORDER BY c.created_at DESC
                """,
            (rs, i) -> mapCustomer(rs),
            tenantId, k, "%" + k + "%", "%" + k + "%");
    }

    public Map<String, Object> listCustomersPaged(String keyword, int page, int size) {
        long tenantId = tenantId();
        String k = safeKeyword(keyword);
        int safeSize = safeSize(size);
        int safePage = Math.max(page, 1);

        Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(1) FROM t_customer c
                WHERE c.tenant_id = ? AND (? = '' OR LOWER(c.name) LIKE LOWER(?) OR LOWER(c.phone) LIKE LOWER(?))
                """, Long.class, tenantId, k, "%" + k + "%", "%" + k + "%");
        long totalVal = total == null ? 0 : total;
        int totalPages = totalVal == 0 ? 1 : (int) Math.ceil(totalVal / (double) safeSize);
        if (safePage > totalPages) {
            safePage = totalPages;
        }
        int offset = (safePage - 1) * safeSize;

        List<Customer> items = jdbcTemplate.query("""
                SELECT c.id, c.name, c.phone, c.verify_code, c.remark, c.status, c.created_at,
                       COALESCE(a.balance, 0) AS balance
                FROM t_customer c
                LEFT JOIN t_account a ON a.customer_id = c.id AND a.tenant_id = c.tenant_id
                WHERE c.tenant_id = ? AND (? = '' OR LOWER(c.name) LIKE LOWER(?) OR LOWER(c.phone) LIKE LOWER(?))
                ORDER BY c.created_at DESC
                LIMIT ? OFFSET ?
                """,
            (rs, i) -> mapCustomer(rs),
            tenantId, k, "%" + k + "%", "%" + k + "%", safeSize, offset);

        return pageResult(items, safePage, safeSize, totalVal, totalPages);
    }

    @Transactional
    public Customer createCustomer(CustomerRequest request) {
        long tenantId = tenantId();
        tenantAccessService.assertCanWrite(tenantId);
        assertCustomerQuota(tenantId);
        validateText(request.getName(), "会员姓名不能为空");
        validateText(request.getPhone(), "手机号不能为空");
        String phone = request.getPhone().trim();
        ensurePhoneUnique(tenantId, phone, null);
        String verifyCode = normalizeVerifyCode(request.getVerifyCode(), phone);

        long id = idGenerator.nextId();
        jdbcTemplate.update("""
                INSERT INTO t_customer(id, tenant_id, name, phone, verify_code, remark, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
            id, tenantId, request.getName().trim(), phone, verifyCode, defaultText(request.getRemark()));

        jdbcTemplate.update("""
                INSERT INTO t_account(customer_id, tenant_id, balance, updated_at)
                VALUES (?, ?, 0, CURRENT_TIMESTAMP)
                """, id, tenantId);

        recordLog("CREATE_CUSTOMER", "customer", String.valueOf(id), "创建会员: " + request.getName().trim());

        BigDecimal init = request.getInitialRechargeAmount();
        if (init != null) {
            if (init.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("初次充值金额不能小于0");
            }
            if (init.compareTo(BigDecimal.ZERO) > 0) {
                creditBalance(tenantId, id, init);
                long rechargeId = idGenerator.nextId();
                jdbcTemplate.update("""
                        INSERT INTO t_recharge_record(id, tenant_id, customer_id, amount, remark, status, created_at)
                        VALUES (?, ?, ?, ?, ?, 'normal', CURRENT_TIMESTAMP)
                        """,
                    rechargeId, tenantId, id, init, "初次充值");
                recordLog("CREATE_RECHARGE", "recharge", String.valueOf(rechargeId),
                    "会员 " + request.getName().trim() + " 初次充值 " + init);
            }
        }
        return forApi(getCustomerById(tenantId, String.valueOf(id)));
    }

    @Transactional
    public Customer updateCustomer(String id, CustomerRequest request) {
        long tenantId = tenantId();
        tenantAccessService.assertCanWrite(tenantId);
        Customer old = getCustomerById(tenantId, id);
        validateText(request.getName(), "会员姓名不能为空");
        validateText(request.getPhone(), "手机号不能为空");
        String phone = request.getPhone().trim();
        ensurePhoneUnique(tenantId, phone, id);
        String verifyCode = request.getVerifyCode() == null || request.getVerifyCode().isBlank()
            ? old.getVerifyCode()
            : normalizeVerifyCode(request.getVerifyCode(), phone);

        jdbcTemplate.update("""
                UPDATE t_customer
                SET name = ?, phone = ?, verify_code = ?, remark = ?, updated_at = CURRENT_TIMESTAMP
                WHERE tenant_id = ? AND id = ?
                """,
            request.getName().trim(), phone, verifyCode, defaultText(request.getRemark()), tenantId, parseId(id));
        recordLog("UPDATE_CUSTOMER", "customer", id, "更新会员: " + request.getName().trim());
        return forApi(getCustomerById(tenantId, id));
    }

    @Transactional
    public Customer toggleCustomerStatus(String id) {
        long tenantId = tenantId();
        tenantAccessService.assertCanWrite(tenantId);
        Customer old = getCustomerById(tenantId, id);
        String next = "active".equals(old.getStatus()) ? "inactive" : "active";
        jdbcTemplate.update("UPDATE t_customer SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE tenant_id = ? AND id = ?",
            next, tenantId, parseId(id));
        recordLog("active".equals(next) ? "RESTORE_CUSTOMER" : "DELETE_CUSTOMER", "customer", id,
            ("active".equals(next) ? "恢复" : "停用") + "会员: " + old.getName());
        return forApi(getCustomerById(tenantId, id));
    }

    public List<Employee> listEmployees(String keyword) {
        long tenantId = tenantId();
        String k = safeKeyword(keyword);
        return jdbcTemplate.query("""
                SELECT id, name, status, created_at
                FROM t_employee
                WHERE tenant_id = ? AND (? = '' OR LOWER(name) LIKE LOWER(?))
                ORDER BY created_at DESC
                """,
            (rs, i) -> new Employee(
                String.valueOf(rs.getLong("id")),
                rs.getString("name"),
                rs.getString("status"),
                rs.getTimestamp("created_at").toLocalDateTime()
            ),
            tenantId, k, "%" + k + "%");
    }

    public Map<String, Object> listEmployeesPaged(String keyword, int page, int size) {
        long tenantId = tenantId();
        String k = safeKeyword(keyword);
        int safeSize = safeSize(size);
        int safePage = Math.max(page, 1);

        Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(1) FROM t_employee
                WHERE tenant_id = ? AND (? = '' OR LOWER(name) LIKE LOWER(?))
                """, Long.class, tenantId, k, "%" + k + "%");
        long totalVal = total == null ? 0 : total;
        int totalPages = totalVal == 0 ? 1 : (int) Math.ceil(totalVal / (double) safeSize);
        if (safePage > totalPages) {
            safePage = totalPages;
        }
        int offset = (safePage - 1) * safeSize;

        List<Employee> items = jdbcTemplate.query("""
                SELECT id, name, status, created_at
                FROM t_employee
                WHERE tenant_id = ? AND (? = '' OR LOWER(name) LIKE LOWER(?))
                ORDER BY created_at DESC
                LIMIT ? OFFSET ?
                """,
            (rs, i) -> new Employee(
                String.valueOf(rs.getLong("id")),
                rs.getString("name"),
                rs.getString("status"),
                rs.getTimestamp("created_at").toLocalDateTime()
            ),
            tenantId, k, "%" + k + "%", safeSize, offset);

        return pageResult(items, safePage, safeSize, totalVal, totalPages);
    }

    /** 下拉：全部在岗员工（不分页） */
    public List<Employee> listActiveEmployeeOptions() {
        long tenantId = tenantId();
        return jdbcTemplate.query("""
                SELECT id, name, status, created_at
                FROM t_employee
                WHERE tenant_id = ? AND status = 'active'
                ORDER BY name ASC
                """,
            (rs, i) -> new Employee(
                String.valueOf(rs.getLong("id")),
                rs.getString("name"),
                rs.getString("status"),
                rs.getTimestamp("created_at").toLocalDateTime()
            ),
            tenantId);
    }

    /** 下拉：全部在用服务 */
    public List<ServiceType> listActiveServiceOptions() {
        long tenantId = tenantId();
        return jdbcTemplate.query("""
                SELECT id, name, price, status, created_at
                FROM t_service_type
                WHERE tenant_id = ? AND status = 'active'
                ORDER BY name ASC
                """,
            (rs, i) -> mapService(rs),
            tenantId);
    }

    @Transactional
    public Employee createEmployee(EmployeeRequest request) {
        long tenantId = tenantId();
        tenantAccessService.assertCanWrite(tenantId);
        assertEmployeeQuota(tenantId);
        validateText(request.getName(), "员工姓名不能为空");
        long id = idGenerator.nextId();
        jdbcTemplate.update("""
                INSERT INTO t_employee(id, tenant_id, name, status, created_at, updated_at)
                VALUES (?, ?, ?, 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, tenantId, request.getName().trim());
        recordLog("CREATE_EMPLOYEE", "employee", String.valueOf(id), "创建员工: " + request.getName().trim());
        return getEmployeeById(tenantId, String.valueOf(id));
    }

    @Transactional
    public Employee updateEmployee(String id, EmployeeRequest request) {
        long tenantId = tenantId();
        tenantAccessService.assertCanWrite(tenantId);
        getEmployeeById(tenantId, id);
        validateText(request.getName(), "员工姓名不能为空");
        jdbcTemplate.update("UPDATE t_employee SET name = ?, updated_at = CURRENT_TIMESTAMP WHERE tenant_id = ? AND id = ?",
            request.getName().trim(), tenantId, parseId(id));
        recordLog("UPDATE_EMPLOYEE", "employee", id, "更新员工: " + request.getName().trim());
        return getEmployeeById(tenantId, id);
    }

    @Transactional
    public Employee toggleEmployeeStatus(String id) {
        long tenantId = tenantId();
        tenantAccessService.assertCanWrite(tenantId);
        Employee old = getEmployeeById(tenantId, id);
        String next = "active".equals(old.getStatus()) ? "inactive" : "active";
        jdbcTemplate.update("UPDATE t_employee SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE tenant_id = ? AND id = ?",
            next, tenantId, parseId(id));
        recordLog("active".equals(next) ? "RESTORE_EMPLOYEE" : "DISABLE_EMPLOYEE", "employee", id,
            ("active".equals(next) ? "恢复" : "停用") + "员工: " + old.getName());
        return getEmployeeById(tenantId, id);
    }

    public List<ServiceType> listServiceTypes() {
        long tenantId = tenantId();
        return jdbcTemplate.query("""
                SELECT id, name, price, status, created_at
                FROM t_service_type
                WHERE tenant_id = ?
                ORDER BY created_at DESC
                """,
            (rs, i) -> mapService(rs),
            tenantId
        );
    }

    public Map<String, Object> listServiceTypesPaged(int page, int size) {
        long tenantId = tenantId();
        int safeSize = safeSize(size);
        int safePage = Math.max(page, 1);
        Long total = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM t_service_type WHERE tenant_id = ?", Long.class, tenantId);
        long totalVal = total == null ? 0 : total;
        int totalPages = totalVal == 0 ? 1 : (int) Math.ceil(totalVal / (double) safeSize);
        if (safePage > totalPages) {
            safePage = totalPages;
        }
        int offset = (safePage - 1) * safeSize;
        List<ServiceType> items = jdbcTemplate.query("""
                SELECT id, name, price, status, created_at
                FROM t_service_type
                WHERE tenant_id = ?
                ORDER BY created_at DESC
                LIMIT ? OFFSET ?
                """,
            (rs, i) -> mapService(rs),
            tenantId, safeSize, offset);
        return pageResult(items, safePage, safeSize, totalVal, totalPages);
    }

    @Transactional
    public ServiceType createServiceType(ServiceTypeRequest request) {
        long tenantId = tenantId();
        tenantAccessService.assertCanWrite(tenantId);
        validateText(request.getName(), "服务名称不能为空");
        validateAmount(request.getPrice(), "价格必须大于等于0");
        long id = idGenerator.nextId();
        jdbcTemplate.update("""
                INSERT INTO t_service_type(id, tenant_id, name, price, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, tenantId, request.getName().trim(), request.getPrice());
        recordLog("CREATE_SERVICE", "service", String.valueOf(id), "新增服务: " + request.getName().trim());
        return getServiceById(tenantId, String.valueOf(id));
    }

    @Transactional
    public ServiceType updateServiceType(String id, ServiceTypeRequest request) {
        long tenantId = tenantId();
        tenantAccessService.assertCanWrite(tenantId);
        getServiceById(tenantId, id);
        validateText(request.getName(), "服务名称不能为空");
        validateAmount(request.getPrice(), "价格必须大于等于0");
        jdbcTemplate.update("""
                UPDATE t_service_type SET name = ?, price = ?, updated_at = CURRENT_TIMESTAMP
                WHERE tenant_id = ? AND id = ?
                """, request.getName().trim(), request.getPrice(), tenantId, parseId(id));
        recordLog("UPDATE_SERVICE", "service", id, "更新服务: " + request.getName().trim());
        return getServiceById(tenantId, id);
    }

    @Transactional
    public ServiceType toggleServiceTypeStatus(String id) {
        long tenantId = tenantId();
        tenantAccessService.assertCanWrite(tenantId);
        ServiceType old = getServiceById(tenantId, id);
        String next = "active".equals(old.getStatus()) ? "inactive" : "active";
        jdbcTemplate.update("UPDATE t_service_type SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE tenant_id = ? AND id = ?",
            next, tenantId, parseId(id));
        recordLog("active".equals(next) ? "RESTORE_SERVICE" : "DISABLE_SERVICE", "service", id,
            ("active".equals(next) ? "恢复" : "停用") + "服务: " + old.getName());
        return getServiceById(tenantId, id);
    }

    @Transactional
    public RechargeRecord createRecharge(RechargeRequest request) {
        long tenantId = tenantId();
        tenantAccessService.assertCanWrite(tenantId);
        ReentrantLock lock = lockFor(tenantId);
        lock.lock();
        try {
            validateText(request.getCustomerId(), "会员不能为空");
            validateAmountPositive(request.getAmount(), "充值金额必须大于0");
            Customer customer = getCustomerById(tenantId, request.getCustomerId());
            ensureActive(customer.getStatus(), "会员已停用");
            long customerId = parseId(request.getCustomerId());
            ensureAccount(tenantId, customerId);
            creditBalance(tenantId, customerId, request.getAmount());

            long id = idGenerator.nextId();
            jdbcTemplate.update("""
                    INSERT INTO t_recharge_record(id, tenant_id, customer_id, amount, remark, status, created_at)
                    VALUES (?, ?, ?, ?, ?, 'normal', CURRENT_TIMESTAMP)
                    """, id, tenantId, customerId, request.getAmount(), defaultText(request.getRemark()));
            recordLog("CREATE_RECHARGE", "recharge", String.valueOf(id),
                "会员 " + customer.getName() + " 充值 " + request.getAmount());
            return getRechargeById(tenantId, String.valueOf(id));
        } finally {
            lock.unlock();
        }
    }

    @Transactional
    public ConsumeRecord createConsume(ConsumeRequest request) {
        long tenantId = tenantId();
        tenantAccessService.assertCanWrite(tenantId);
        ReentrantLock lock = lockFor(tenantId);
        lock.lock();
        try {
            validateAmountPositive(request.getAmount(), "消费金额必须大于0");
            Customer customer = getCustomerById(tenantId, request.getCustomerId());
            Employee employee = getEmployeeById(tenantId, request.getEmployeeId());
            ServiceType serviceType = getServiceById(tenantId, request.getServiceTypeId());
            ensureActive(customer.getStatus(), "会员已停用");
            ensureActive(employee.getStatus(), "员工已停用");
            ensureActive(serviceType.getStatus(), "服务类型已停用");
            if (request.getVerifyCode() == null || request.getVerifyCode().isBlank()) {
                recordLog("CONSUME_VERIFY_FAIL", "customer", request.getCustomerId(),
                    "消费校验码为空 会员=" + customer.getName());
                throw new IllegalArgumentException("请输入4位校验码");
            }
            if (!Objects.equals(customer.getVerifyCode(), request.getVerifyCode().trim())) {
                recordLog("CONSUME_VERIFY_FAIL", "customer", request.getCustomerId(),
                    "消费校验码错误 会员=" + customer.getName() + " 操作人=" + TenantContext.getUsername());
                throw new IllegalArgumentException("校验码错误，无法消费");
            }

            long customerId = parseId(request.getCustomerId());
            ensureAccount(tenantId, customerId);
            int updated = jdbcTemplate.update("""
                    UPDATE t_account
                    SET balance = balance - ?, updated_at = CURRENT_TIMESTAMP
                    WHERE tenant_id = ? AND customer_id = ? AND balance >= ?
                    """,
                request.getAmount(), tenantId, customerId, request.getAmount());
            if (updated == 0) {
                throw new IllegalArgumentException("余额不足，扣款失败");
            }

            long id = idGenerator.nextId();
            jdbcTemplate.update("""
                    INSERT INTO t_consume_record(id, tenant_id, customer_id, employee_id, service_type_id, amount, remark, status, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 'normal', CURRENT_TIMESTAMP)
                    """,
                id, tenantId, customerId, parseId(request.getEmployeeId()), parseId(request.getServiceTypeId()),
                request.getAmount(), defaultText(request.getRemark()));
            recordLog("CREATE_CONSUME", "consume", String.valueOf(id),
                "会员 " + customer.getName() + " 消费 " + request.getAmount() + "，员工 " + employee.getName());
            return getConsumeById(tenantId, String.valueOf(id));
        } finally {
            lock.unlock();
        }
    }

    /**
     * 冲正充值：扣回余额（需余额足够），原单 status=reversed，写入 reversal 单。
     */
    @Transactional
    public RechargeRecord reverseRecharge(String id, String reason) {
        long tenantId = tenantId();
        tenantAccessService.assertCanWrite(tenantId);
        ReentrantLock lock = lockFor(tenantId);
        lock.lock();
        try {
            RechargeRecord original = getRechargeById(tenantId, id);
            if (!"normal".equals(nullToNormal(original.getStatus()))) {
                throw new IllegalArgumentException("该充值单已冲正或不可冲正");
            }
            long customerId = parseId(original.getCustomerId());
            ensureAccount(tenantId, customerId);
            int updated = jdbcTemplate.update("""
                    UPDATE t_account
                    SET balance = balance - ?, updated_at = CURRENT_TIMESTAMP
                    WHERE tenant_id = ? AND customer_id = ? AND balance >= ?
                    """,
                original.getAmount(), tenantId, customerId, original.getAmount());
            if (updated == 0) {
                throw new IllegalArgumentException("当前余额不足以冲正该充值，请先处理后续消费");
            }

            long revId = idGenerator.nextId();
            jdbcTemplate.update(
                "UPDATE t_recharge_record SET status = 'reversed', related_id = ? WHERE tenant_id = ? AND id = ?",
                revId, tenantId, parseId(id)
            );
            String remark = "冲正充值" + (reason == null || reason.isBlank() ? "" : ": " + reason.trim());
            jdbcTemplate.update("""
                    INSERT INTO t_recharge_record(id, tenant_id, customer_id, amount, remark, status, related_id, created_at)
                    VALUES (?, ?, ?, ?, ?, 'reversal', ?, CURRENT_TIMESTAMP)
                    """,
                revId, tenantId, customerId, original.getAmount(), remark, parseId(id));
            recordLog("REVERSE_RECHARGE", "recharge", id,
                "冲正充值 " + original.getAmount() + " " + remark);
            return getRechargeById(tenantId, String.valueOf(revId));
        } finally {
            lock.unlock();
        }
    }

    /**
     * 冲正消费：退回余额，原单 status=reversed，写入 reversal 单。
     */
    @Transactional
    public ConsumeRecord reverseConsume(String id, String reason) {
        long tenantId = tenantId();
        tenantAccessService.assertCanWrite(tenantId);
        ReentrantLock lock = lockFor(tenantId);
        lock.lock();
        try {
            ConsumeRecord original = getConsumeById(tenantId, id);
            if (!"normal".equals(nullToNormal(original.getStatus()))) {
                throw new IllegalArgumentException("该消费单已冲正或不可冲正");
            }
            long customerId = parseId(original.getCustomerId());
            ensureAccount(tenantId, customerId);
            creditBalance(tenantId, customerId, original.getAmount());

            long revId = idGenerator.nextId();
            jdbcTemplate.update(
                "UPDATE t_consume_record SET status = 'reversed', related_id = ? WHERE tenant_id = ? AND id = ?",
                revId, tenantId, parseId(id)
            );
            String remark = "冲正消费" + (reason == null || reason.isBlank() ? "" : ": " + reason.trim());
            jdbcTemplate.update("""
                    INSERT INTO t_consume_record(id, tenant_id, customer_id, employee_id, service_type_id, amount, remark, status, related_id, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 'reversal', ?, CURRENT_TIMESTAMP)
                    """,
                revId, tenantId, customerId, parseId(original.getEmployeeId()), parseId(original.getServiceTypeId()),
                original.getAmount(), remark, parseId(id));
            recordLog("REVERSE_CONSUME", "consume", id,
                "冲正消费 " + original.getAmount() + " " + remark);
            return getConsumeById(tenantId, String.valueOf(revId));
        } finally {
            lock.unlock();
        }
    }

    public BigDecimal getBalance(String customerId) {
        long tenantId = tenantId();
        getCustomerById(tenantId, customerId);
        ensureAccount(tenantId, parseId(customerId));
        BigDecimal balance = jdbcTemplate.queryForObject(
            "SELECT balance FROM t_account WHERE tenant_id = ? AND customer_id = ?",
            BigDecimal.class, tenantId, parseId(customerId));
        return balance == null ? BigDecimal.ZERO : balance;
    }

    public List<Map<String, Object>> listTransactionRows(String keyword) {
        return listTransactionRowsInternal(keyword, null, null, null, null);
    }

    public Map<String, Object> listTransactionRowsPaged(String keyword, int page, int size) {
        long tenantId = tenantId();
        String k = safeKeyword(keyword);
        int safeSize = safeSize(size);
        int safePage = Math.max(page, 1);

        Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(1) FROM (
                    SELECT r.id
                    FROM t_recharge_record r
                    LEFT JOIN t_customer c ON c.tenant_id = r.tenant_id AND c.id = r.customer_id
                    WHERE r.tenant_id = ?
                      AND (? = '' OR LOWER(COALESCE(c.name,'')) LIKE LOWER(?) OR LOWER(COALESCE(r.remark,'')) LIKE LOWER(?)
                          OR LOWER(COALESCE(r.status,'normal')) LIKE LOWER(?) OR 'recharge' LIKE LOWER(?))
                    UNION ALL
                    SELECT cr.id
                    FROM t_consume_record cr
                    LEFT JOIN t_customer c ON c.tenant_id = cr.tenant_id AND c.id = cr.customer_id
                    LEFT JOIN t_service_type st ON st.tenant_id = cr.tenant_id AND st.id = cr.service_type_id
                    LEFT JOIN t_employee e ON e.tenant_id = cr.tenant_id AND e.id = cr.employee_id
                    WHERE cr.tenant_id = ?
                      AND (? = '' OR LOWER(COALESCE(c.name,'')) LIKE LOWER(?)
                          OR LOWER(COALESCE(st.name,'') || '/' || COALESCE(e.name,'') || '/' || COALESCE(cr.remark,'')) LIKE LOWER(?)
                          OR LOWER(COALESCE(cr.status,'normal')) LIKE LOWER(?) OR 'consume' LIKE LOWER(?))
                ) t
                """,
            Long.class,
            tenantId, k, "%" + k + "%", "%" + k + "%", "%" + k + "%", "%" + k + "%",
            tenantId, k, "%" + k + "%", "%" + k + "%", "%" + k + "%", "%" + k + "%"
        );
        long totalVal = total == null ? 0 : total;
        int totalPages = totalVal == 0 ? 1 : (int) Math.ceil(totalVal / (double) safeSize);
        if (safePage > totalPages) {
            safePage = totalPages;
        }
        int offset = (safePage - 1) * safeSize;
        List<Map<String, Object>> items = listTransactionRowsInternal(keyword, safeSize, offset, null, null);
        return pageResult(items, safePage, safeSize, totalVal, totalPages);
    }

    public List<Map<String, Object>> listTransactionRowsBetween(String keyword, LocalDate start, LocalDate end) {
        return listTransactionRowsInternal(keyword, null, null, start, end);
    }

    private List<Map<String, Object>> listTransactionRowsInternal(
        String keyword, Integer limit, Integer offset, LocalDate start, LocalDate end
    ) {
        long tenantId = tenantId();
        String k = safeKeyword(keyword);
        String dateFilterR = "";
        String dateFilterC = "";
        if (start != null && end != null) {
            dateFilterR = " AND DATE(r.created_at) BETWEEN DATE(?) AND DATE(?) ";
            dateFilterC = " AND DATE(cr.created_at) BETWEEN DATE(?) AND DATE(?) ";
        }
        String limitSql = (limit != null && offset != null) ? " LIMIT ? OFFSET ? " : "";

        String sql = """
            SELECT x.id, x.type, x.customer_id, c.name AS customer_name, x.amount, x.detail, x.status, x.created_at
            FROM (
                SELECT CAST(r.id AS TEXT) AS id, 'recharge' AS type, r.customer_id, r.amount,
                       CASE WHEN COALESCE(r.status,'normal') = 'reversal' THEN '[冲正]' || r.remark
                            WHEN COALESCE(r.status,'normal') = 'reversed' THEN '[已冲正]' || r.remark
                            ELSE r.remark END AS detail,
                       COALESCE(r.status,'normal') AS status, r.created_at
                FROM t_recharge_record r
                WHERE r.tenant_id = ?
                """ + dateFilterR + """
                UNION ALL
                SELECT CAST(cr.id AS TEXT) AS id, 'consume' AS type, cr.customer_id, cr.amount,
                       (CASE WHEN COALESCE(cr.status,'normal') = 'reversal' THEN '[冲正]'
                             WHEN COALESCE(cr.status,'normal') = 'reversed' THEN '[已冲正]'
                             ELSE '' END)
                       || COALESCE(st.name,'未知服务') || '/' || COALESCE(e.name,'未知员工')
                       || CASE WHEN cr.remark = '' THEN '' ELSE '/' || cr.remark END AS detail,
                       COALESCE(cr.status,'normal') AS status, cr.created_at
                FROM t_consume_record cr
                LEFT JOIN t_service_type st ON st.tenant_id = cr.tenant_id AND st.id = cr.service_type_id
                LEFT JOIN t_employee e ON e.tenant_id = cr.tenant_id AND e.id = cr.employee_id
                WHERE cr.tenant_id = ?
                """ + dateFilterC + """
            ) x
            LEFT JOIN t_customer c ON c.tenant_id = ? AND c.id = x.customer_id
            WHERE (? = '' OR LOWER(COALESCE(c.name,'')) LIKE LOWER(?) OR LOWER(COALESCE(x.detail,'')) LIKE LOWER(?)
                OR LOWER(x.type) LIKE LOWER(?) OR LOWER(x.status) LIKE LOWER(?))
            ORDER BY x.created_at DESC
            """ + limitSql;

        return jdbcTemplate.query(sql, (rs, i) -> {
            Map<String, Object> row = new HashMap<>();
            row.put("id", rs.getString("id"));
            row.put("type", rs.getString("type"));
            row.put("customerId", String.valueOf(rs.getLong("customer_id")));
            row.put("customerName", rs.getString("customer_name"));
            row.put("amount", rs.getBigDecimal("amount"));
            row.put("detail", rs.getString("detail"));
            row.put("status", rs.getString("status"));
            row.put("createdAt", rs.getTimestamp("created_at").toLocalDateTime());
            return row;
        }, buildTxnArgs(tenantId, k, start, end, limit, offset));
    }

    private Object[] buildTxnArgs(long tenantId, String k, LocalDate start, LocalDate end, Integer limit, Integer offset) {
        // order: r.tenant, [start,end], cr.tenant, [start,end], join tenant, keyword x5, [limit, offset]
        boolean hasDate = start != null && end != null;
        boolean hasPage = limit != null && offset != null;
        int size = 3 + 5 + (hasDate ? 4 : 0) + (hasPage ? 2 : 0);
        Object[] args = new Object[size];
        int i = 0;
        args[i++] = tenantId;
        if (hasDate) {
            args[i++] = start.toString();
            args[i++] = end.toString();
        }
        args[i++] = tenantId;
        if (hasDate) {
            args[i++] = start.toString();
            args[i++] = end.toString();
        }
        args[i++] = tenantId;
        args[i++] = k;
        args[i++] = "%" + k + "%";
        args[i++] = "%" + k + "%";
        args[i++] = "%" + k + "%";
        args[i++] = "%" + k + "%";
        if (hasPage) {
            args[i++] = limit;
            args[i] = offset;
        }
        return args;
    }

    public List<AuditLog> listAuditLogs(String keyword) {
        long tenantId = tenantId();
        String k = safeKeyword(keyword);
        return jdbcTemplate.query("""
                SELECT id, action, entity_type, entity_id, detail, created_at
                FROM t_audit_log
                WHERE tenant_id = ? AND (? = '' OR LOWER(action) LIKE LOWER(?) OR LOWER(entity_type) LIKE LOWER(?) OR LOWER(detail) LIKE LOWER(?))
                ORDER BY created_at DESC
                """,
            (rs, i) -> mapAudit(rs),
            tenantId, k, "%" + k + "%", "%" + k + "%", "%" + k + "%"
        );
    }

    public Map<String, Object> listAuditLogsPaged(String keyword, int page, int size) {
        long tenantId = tenantId();
        String k = safeKeyword(keyword);
        int safeSize = safeSize(size);
        int safePage = Math.max(page, 1);
        Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(1) FROM t_audit_log
                WHERE tenant_id = ? AND (? = '' OR LOWER(action) LIKE LOWER(?) OR LOWER(entity_type) LIKE LOWER(?) OR LOWER(detail) LIKE LOWER(?))
                """, Long.class, tenantId, k, "%" + k + "%", "%" + k + "%", "%" + k + "%");
        long totalVal = total == null ? 0 : total;
        int totalPages = totalVal == 0 ? 1 : (int) Math.ceil(totalVal / (double) safeSize);
        if (safePage > totalPages) {
            safePage = totalPages;
        }
        int offset = (safePage - 1) * safeSize;
        List<AuditLog> items = jdbcTemplate.query("""
                SELECT id, action, entity_type, entity_id, detail, created_at
                FROM t_audit_log
                WHERE tenant_id = ? AND (? = '' OR LOWER(action) LIKE LOWER(?) OR LOWER(entity_type) LIKE LOWER(?) OR LOWER(detail) LIKE LOWER(?))
                ORDER BY created_at DESC
                LIMIT ? OFFSET ?
                """,
            (rs, i) -> mapAudit(rs),
            tenantId, k, "%" + k + "%", "%" + k + "%", "%" + k + "%", safeSize, offset);
        return pageResult(items, safePage, safeSize, totalVal, totalPages);
    }

    public Map<String, Object> getDashboardSummary() {
        long tenantId = tenantId();
        BigDecimal totalBalance = jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(balance),0) FROM t_account WHERE tenant_id = ?", BigDecimal.class, tenantId);

        Map<String, Object> map = new HashMap<>();
        map.put("activeCustomers", jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM t_customer WHERE tenant_id = ? AND status = 'active'", Long.class, tenantId));
        map.put("totalCustomers", jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM t_customer WHERE tenant_id = ?", Long.class, tenantId));
        map.put("activeEmployees", jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM t_employee WHERE tenant_id = ? AND status = 'active'", Long.class, tenantId));
        map.put("totalBalance", totalBalance == null ? BigDecimal.ZERO : totalBalance);
        String today = dbDialect.todayExpr();
        map.put("todayRecharge", jdbcTemplate.queryForObject(
            """
                SELECT COALESCE(SUM(amount),0) FROM t_recharge_record
                WHERE tenant_id = ? AND COALESCE(status,'normal') = 'normal'
                  AND DATE(created_at) = %s
                """.formatted(today),
            BigDecimal.class, tenantId));
        map.put("todayConsume", jdbcTemplate.queryForObject(
            """
                SELECT COALESCE(SUM(amount),0) FROM t_consume_record
                WHERE tenant_id = ? AND COALESCE(status,'normal') = 'normal'
                  AND DATE(created_at) = %s
                """.formatted(today),
            BigDecimal.class, tenantId));
        map.put("auditCount", jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM t_audit_log WHERE tenant_id = ?", Long.class, tenantId));
        Map<String, Object> shop = getShopProfile();
        map.put("shopName", shop.get("shopName"));
        map.put("tenantKey", shop.get("tenantKey"));
        map.put("planCode", shop.get("planCode"));
        map.put("customerQuota", shop.get("customerQuota"));
        map.put("employeeQuota", shop.get("employeeQuota"));
        return map;
    }

    public Map<String, Object> getReportSummary(LocalDate start, LocalDate end) {
        long tenantId = tenantId();
        String fromDate = start.toString();
        String toDate = end.toString();
        Map<String, Object> map = new HashMap<>();
        map.put("total_recharge", jdbcTemplate.queryForObject(
            """
                SELECT COALESCE(SUM(amount),0) FROM t_recharge_record
                WHERE tenant_id = ? AND COALESCE(status,'normal') = 'normal'
                  AND DATE(created_at) BETWEEN DATE(?) AND DATE(?)
                """,
            BigDecimal.class, tenantId, fromDate, toDate));
        map.put("total_consume", jdbcTemplate.queryForObject(
            """
                SELECT COALESCE(SUM(amount),0) FROM t_consume_record
                WHERE tenant_id = ? AND COALESCE(status,'normal') = 'normal'
                  AND DATE(created_at) BETWEEN DATE(?) AND DATE(?)
                """,
            BigDecimal.class, tenantId, fromDate, toDate));
        map.put("total_customers", jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM t_customer WHERE tenant_id = ?", Long.class, tenantId));
        map.put("new_customers", jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM t_customer WHERE tenant_id = ? AND DATE(created_at) BETWEEN DATE(?) AND DATE(?)",
            Long.class, tenantId, fromDate, toDate));
        map.put("active_customers", jdbcTemplate.queryForObject(
            """
                SELECT COUNT(DISTINCT customer_id) FROM t_consume_record
                WHERE tenant_id = ? AND COALESCE(status,'normal') = 'normal'
                  AND DATE(created_at) BETWEEN DATE(?) AND DATE(?)
                """,
            Long.class, tenantId, fromDate, toDate));
        return map;
    }

    public List<Map<String, Object>> getEmployeePerformance(LocalDate start, LocalDate end) {
        long tenantId = tenantId();
        String fromDate = start.toString();
        String toDate = end.toString();
        return jdbcTemplate.query("""
                SELECT cr.employee_id, e.name AS employee_name, COUNT(1) AS total_count, COALESCE(SUM(cr.amount),0) AS total_amount
                FROM t_consume_record cr
                LEFT JOIN t_employee e ON e.tenant_id = cr.tenant_id AND e.id = cr.employee_id
                WHERE cr.tenant_id = ? AND COALESCE(cr.status,'normal') = 'normal'
                  AND DATE(cr.created_at) BETWEEN DATE(?) AND DATE(?)
                GROUP BY cr.employee_id, e.name
                ORDER BY total_amount DESC
                """,
            (rs, i) -> {
                Map<String, Object> row = new HashMap<>();
                int count = rs.getInt("total_count");
                BigDecimal totalAmount = rs.getBigDecimal("total_amount");
                row.put("employeeId", String.valueOf(rs.getLong("employee_id")));
                row.put("employeeName", rs.getString("employee_name"));
                row.put("total_count", count);
                row.put("total_amount", totalAmount);
                row.put("avg_amount", count == 0 ? BigDecimal.ZERO : totalAmount.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP));
                return row;
            },
            tenantId, fromDate, toDate
        );
    }

    public List<Map<String, Object>> getServiceBreakdown(LocalDate start, LocalDate end) {
        long tenantId = tenantId();
        String fromDate = start.toString();
        String toDate = end.toString();
        return jdbcTemplate.query("""
                SELECT cr.service_type_id, st.name AS service_name, COUNT(1) AS total_count, COALESCE(SUM(cr.amount),0) AS total_amount
                FROM t_consume_record cr
                LEFT JOIN t_service_type st ON st.tenant_id = cr.tenant_id AND st.id = cr.service_type_id
                WHERE cr.tenant_id = ? AND COALESCE(cr.status,'normal') = 'normal'
                  AND DATE(cr.created_at) BETWEEN DATE(?) AND DATE(?)
                GROUP BY cr.service_type_id, st.name
                ORDER BY total_amount DESC
                """,
            (rs, i) -> {
                Map<String, Object> row = new HashMap<>();
                row.put("serviceTypeId", String.valueOf(rs.getLong("service_type_id")));
                row.put("serviceName", rs.getString("service_name"));
                row.put("total_count", rs.getInt("total_count"));
                row.put("total_amount", rs.getBigDecimal("total_amount"));
                return row;
            },
            tenantId, fromDate, toDate
        );
    }

    // ---------- shop profile (t_tenant) ----------

    public Map<String, Object> getShopProfile() {
        long tenantId = tenantId();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
                SELECT tenant_key, shop_name, status, plan_code, max_customers, max_employees
                FROM t_tenant WHERE id = ?
                """,
            tenantId
        );
        Map<String, Object> map = new HashMap<>();
        if (rows.isEmpty()) {
            map.put("tenantId", String.valueOf(tenantId));
            map.put("tenantKey", "");
            map.put("shopName", "我的理发店");
            map.put("status", "active");
            map.put("planCode", "free");
            map.put("maxCustomers", 5000);
            map.put("maxEmployees", 50);
        } else {
            Map<String, Object> t = rows.get(0);
            map.put("tenantId", String.valueOf(tenantId));
            map.put("tenantKey", String.valueOf(t.get("tenant_key")));
            map.put("shopName", String.valueOf(t.get("shop_name")));
            map.put("status", String.valueOf(t.get("status")));
            map.put("planCode", String.valueOf(t.get("plan_code")));
            map.put("maxCustomers", ((Number) t.get("max_customers")).intValue());
            map.put("maxEmployees", ((Number) t.get("max_employees")).intValue());
        }
        Long customerCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM t_customer WHERE tenant_id = ?", Long.class, tenantId
        );
        Long employeeCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM t_employee WHERE tenant_id = ? AND status = 'active'", Long.class, tenantId
        );
        int usedCustomers = customerCount == null ? 0 : customerCount.intValue();
        int usedEmployees = employeeCount == null ? 0 : employeeCount.intValue();
        int maxCustomers = ((Number) map.get("maxCustomers")).intValue();
        int maxEmployees = ((Number) map.get("maxEmployees")).intValue();
        map.put("usedCustomers", usedCustomers);
        map.put("usedEmployees", usedEmployees);
        map.put("customerQuota", usedCustomers + " / " + maxCustomers);
        map.put("employeeQuota", usedEmployees + " / " + maxEmployees);
        return map;
    }

    @Transactional
    public Map<String, Object> updateShopProfile(String shopName) {
        if (shopName == null || shopName.isBlank()) {
            throw new IllegalArgumentException("门店名称不能为空");
        }
        String name = shopName.trim();
        if (name.length() > 64) {
            throw new IllegalArgumentException("门店名称最多64字");
        }
        long tenantId = tenantId();
        tenantAccessService.assertCanWrite(tenantId);
        Integer exists = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM t_tenant WHERE id = ?", Integer.class, tenantId
        );
        if (exists == null || exists == 0) {
            throw new IllegalArgumentException("门店租户不存在，请联系平台");
        }
        jdbcTemplate.update(
            "UPDATE t_tenant SET shop_name = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
            name, tenantId
        );
        recordLog("UPDATE_SHOP", "tenant", String.valueOf(tenantId), "更新门店名称: " + name);
        return getShopProfile();
    }

    private void assertCustomerQuota(long tenantId) {
        int max = readTenantLimit(tenantId, "max_customers", 5000);
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM t_customer WHERE tenant_id = ?", Long.class, tenantId
        );
        long used = count == null ? 0 : count;
        if (used >= max) {
            throw new IllegalArgumentException("会员数量已达套餐上限（" + max + "），请联系平台升级");
        }
    }

    private void assertEmployeeQuota(long tenantId) {
        int max = readTenantLimit(tenantId, "max_employees", 50);
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM t_employee WHERE tenant_id = ? AND status = 'active'",
            Long.class, tenantId
        );
        long used = count == null ? 0 : count;
        if (used >= max) {
            throw new IllegalArgumentException("在岗员工数已达套餐上限（" + max + "），请联系平台升级");
        }
    }

    private int readTenantLimit(long tenantId, String column, int defaultVal) {
        // column 仅内部常量
        try {
            Integer v = jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM t_tenant WHERE id = ?",
                Integer.class, tenantId
            );
            return v == null || v <= 0 ? defaultVal : v;
        } catch (Exception e) {
            return defaultVal;
        }
    }

    // ---------- settings ----------

    public Map<String, String> getSettings() {
        long tenantId = tenantId();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT setting_key, setting_value FROM t_tenant_setting WHERE tenant_id = ?",
            tenantId
        );
        Map<String, String> map = new HashMap<>();
        // defaults
        map.put("dailyTarget", "3000");
        for (Map<String, Object> row : rows) {
            map.put(String.valueOf(row.get("setting_key")), String.valueOf(row.get("setting_value")));
        }
        return map;
    }

    @Transactional
    public Map<String, String> updateSettings(Map<String, String> settings) {
        if (settings == null || settings.isEmpty()) {
            throw new IllegalArgumentException("设置不能为空");
        }
        long tenantId = tenantId();
        for (Map.Entry<String, String> e : settings.entrySet()) {
            String key = e.getKey() == null ? "" : e.getKey().trim();
            if (key.isEmpty() || key.length() > 64) {
                throw new IllegalArgumentException("非法设置项: " + key);
            }
            String value = e.getValue() == null ? "" : e.getValue().trim();
            if (value.length() > 500) {
                throw new IllegalArgumentException("设置值过长: " + key);
            }
            if ("dailyTarget".equals(key)) {
                try {
                    double v = Double.parseDouble(value);
                    if (v < 0) {
                        throw new IllegalArgumentException("今日目标不能为负");
                    }
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("今日目标必须是数字");
                }
            }
            Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_tenant_setting WHERE tenant_id = ? AND setting_key = ?",
                Integer.class, tenantId, key
            );
            if (exists != null && exists > 0) {
                jdbcTemplate.update(
                    "UPDATE t_tenant_setting SET setting_value = ?, updated_at = CURRENT_TIMESTAMP WHERE tenant_id = ? AND setting_key = ?",
                    value, tenantId, key
                );
            } else {
                jdbcTemplate.update(
                    "INSERT INTO t_tenant_setting(tenant_id, setting_key, setting_value, updated_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)",
                    tenantId, key, value
                );
            }
        }
        recordLog("UPDATE_SETTINGS", "system", String.valueOf(tenantId), "更新租户设置: " + settings.keySet());
        return getSettings();
    }

    // ---------- private helpers ----------

    private Customer mapCustomer(java.sql.ResultSet rs) throws java.sql.SQLException {
        String verify = rs.getString("verify_code");
        // 列表/查询返回：无「查看校验码」权限时不返回明文（消费校验仍用 getCustomerById 内部逻辑，见 mask）
        if (!rolePermissionService.has(StaffRole.Perm.VIEW_VERIFY_CODE)) {
            verify = null;
        }
        return new Customer(
            String.valueOf(rs.getLong("id")),
            rs.getString("name"),
            rs.getString("phone"),
            verify,
            rs.getString("remark"),
            rs.getString("status"),
            rs.getBigDecimal("balance"),
            rs.getTimestamp("created_at").toLocalDateTime()
        );
    }

    /**
     * 内部读取（含校验码明文），不受 VIEW_VERIFY_CODE 影响。
     * 用于消费扣款等服务端校验。
     */
    private Customer mapCustomerInternal(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Customer(
            String.valueOf(rs.getLong("id")),
            rs.getString("name"),
            rs.getString("phone"),
            rs.getString("verify_code"),
            rs.getString("remark"),
            rs.getString("status"),
            rs.getBigDecimal("balance"),
            rs.getTimestamp("created_at").toLocalDateTime()
        );
    }

    private ServiceType mapService(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ServiceType(
            String.valueOf(rs.getLong("id")),
            rs.getString("name"),
            rs.getBigDecimal("price"),
            rs.getString("status"),
            rs.getTimestamp("created_at").toLocalDateTime()
        );
    }

    private AuditLog mapAudit(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new AuditLog(
            String.valueOf(rs.getLong("id")),
            rs.getString("action"),
            rs.getString("entity_type"),
            rs.getString("entity_id"),
            rs.getString("detail"),
            rs.getTimestamp("created_at").toLocalDateTime()
        );
    }

    private Customer getCustomerById(long tenantId, String id) {
        List<Customer> list = jdbcTemplate.query("""
                SELECT c.id, c.name, c.phone, c.verify_code, c.remark, c.status, c.created_at,
                       COALESCE(a.balance, 0) AS balance
                FROM t_customer c
                LEFT JOIN t_account a ON a.customer_id = c.id AND a.tenant_id = c.tenant_id
                WHERE c.tenant_id = ? AND c.id = ?
                """,
            (rs, i) -> mapCustomerInternal(rs),
            tenantId, parseId(id));
        if (list.isEmpty()) {
            throw new IllegalArgumentException("会员不存在");
        }
        return list.get(0);
    }

    /** API 出参脱敏校验码 */
    private Customer forApi(Customer c) {
        if (c != null && !rolePermissionService.has(StaffRole.Perm.VIEW_VERIFY_CODE)) {
            c.setVerifyCode(null);
        }
        return c;
    }

    private Employee getEmployeeById(long tenantId, String id) {
        List<Employee> list = jdbcTemplate.query("""
                SELECT id, name, status, created_at
                FROM t_employee WHERE tenant_id = ? AND id = ?
                """,
            (rs, i) -> new Employee(
                String.valueOf(rs.getLong("id")),
                rs.getString("name"),
                rs.getString("status"),
                rs.getTimestamp("created_at").toLocalDateTime()
            ),
            tenantId, parseId(id));
        if (list.isEmpty()) {
            throw new IllegalArgumentException("员工不存在");
        }
        return list.get(0);
    }

    private ServiceType getServiceById(long tenantId, String id) {
        List<ServiceType> list = jdbcTemplate.query("""
                SELECT id, name, price, status, created_at
                FROM t_service_type WHERE tenant_id = ? AND id = ?
                """,
            (rs, i) -> mapService(rs),
            tenantId, parseId(id));
        if (list.isEmpty()) {
            throw new IllegalArgumentException("服务类型不存在");
        }
        return list.get(0);
    }

    private RechargeRecord getRechargeById(long tenantId, String id) {
        List<RechargeRecord> list = jdbcTemplate.query("""
                SELECT id, customer_id, amount, remark, COALESCE(status,'normal') AS status, related_id, created_at
                FROM t_recharge_record WHERE tenant_id = ? AND id = ?
                """,
            (rs, i) -> new RechargeRecord(
                String.valueOf(rs.getLong("id")),
                String.valueOf(rs.getLong("customer_id")),
                rs.getBigDecimal("amount"),
                rs.getString("remark"),
                rs.getString("status"),
                rs.getObject("related_id") == null ? null : String.valueOf(rs.getLong("related_id")),
                rs.getTimestamp("created_at").toLocalDateTime()
            ),
            tenantId, parseId(id));
        if (list.isEmpty()) {
            throw new IllegalArgumentException("充值记录不存在");
        }
        return list.get(0);
    }

    private ConsumeRecord getConsumeById(long tenantId, String id) {
        List<ConsumeRecord> list = jdbcTemplate.query("""
                SELECT id, customer_id, employee_id, service_type_id, amount, remark,
                       COALESCE(status,'normal') AS status, related_id, created_at
                FROM t_consume_record WHERE tenant_id = ? AND id = ?
                """,
            (rs, i) -> new ConsumeRecord(
                String.valueOf(rs.getLong("id")),
                String.valueOf(rs.getLong("customer_id")),
                String.valueOf(rs.getLong("employee_id")),
                String.valueOf(rs.getLong("service_type_id")),
                rs.getBigDecimal("amount"),
                rs.getString("remark"),
                rs.getString("status"),
                rs.getObject("related_id") == null ? null : String.valueOf(rs.getLong("related_id")),
                rs.getTimestamp("created_at").toLocalDateTime()
            ),
            tenantId, parseId(id));
        if (list.isEmpty()) {
            throw new IllegalArgumentException("消费记录不存在");
        }
        return list.get(0);
    }

    private void ensurePhoneUnique(long tenantId, String phone, String currentId) {
        String sql = currentId == null
            ? "SELECT COUNT(1) FROM t_customer WHERE tenant_id = ? AND phone = ?"
            : "SELECT COUNT(1) FROM t_customer WHERE tenant_id = ? AND phone = ? AND id <> ?";
        Long count = currentId == null
            ? jdbcTemplate.queryForObject(sql, Long.class, tenantId, phone)
            : jdbcTemplate.queryForObject(sql, Long.class, tenantId, phone, parseId(currentId));
        if (count != null && count > 0) {
            throw new IllegalArgumentException("手机号必须唯一");
        }
    }

    private void ensureAccount(long tenantId, long customerId) {
        Integer exists = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM t_account WHERE customer_id = ?", Integer.class, customerId);
        if (exists == null || exists == 0) {
            jdbcTemplate.update(
                "INSERT INTO t_account(customer_id, tenant_id, balance, updated_at) VALUES (?, ?, 0, CURRENT_TIMESTAMP)",
                customerId, tenantId
            );
        }
    }

    private void creditBalance(long tenantId, long customerId, BigDecimal amount) {
        ensureAccount(tenantId, customerId);
        jdbcTemplate.update("""
                UPDATE t_account
                SET balance = balance + ?, updated_at = CURRENT_TIMESTAMP
                WHERE tenant_id = ? AND customer_id = ?
                """, amount, tenantId, customerId);
    }

    private ReentrantLock lockFor(long tenantId) {
        return tenantLocks.computeIfAbsent(tenantId, id -> new ReentrantLock());
    }

    private void recordLog(String action, String entityType, String entityId, String detail) {
        long id = idGenerator.nextId();
        jdbcTemplate.update("""
                INSERT INTO t_audit_log(id, tenant_id, action, entity_type, entity_id, detail, created_at)
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
            id, tenantId(), toZhAction(action), toZhEntityType(entityType), entityId, detail);
    }

    private String toZhAction(String action) {
        if (action == null) {
            return "未知操作";
        }
        return switch (action) {
            case "CREATE_CUSTOMER" -> "创建会员";
            case "UPDATE_CUSTOMER" -> "更新会员";
            case "DELETE_CUSTOMER" -> "停用会员";
            case "RESTORE_CUSTOMER" -> "恢复会员";
            case "CREATE_EMPLOYEE" -> "创建员工";
            case "UPDATE_EMPLOYEE" -> "更新员工";
            case "DISABLE_EMPLOYEE" -> "停用员工";
            case "RESTORE_EMPLOYEE" -> "恢复员工";
            case "CREATE_SERVICE" -> "创建服务";
            case "UPDATE_SERVICE" -> "更新服务";
            case "DISABLE_SERVICE" -> "停用服务";
            case "RESTORE_SERVICE" -> "恢复服务";
            case "CREATE_RECHARGE" -> "创建充值";
            case "CREATE_CONSUME" -> "创建消费";
            case "CONSUME_VERIFY_FAIL" -> "消费校验码失败";
            case "REVERSE_RECHARGE" -> "冲正充值";
            case "LOGIN_OK" -> "登录成功";
            case "LOGIN_FAIL" -> "登录失败";
            case "REVERSE_CONSUME" -> "冲正消费";
            case "UPDATE_SETTINGS" -> "更新设置";
            case "BACKUP" -> "数据备份";
            case "RESTORE" -> "数据恢复";
            case "INIT" -> "系统初始化";
            default -> action;
        };
    }

    private String toZhEntityType(String entityType) {
        if (entityType == null) {
            return "未知实体";
        }
        return switch (entityType) {
            case "customer" -> "会员";
            case "employee" -> "员工";
            case "service" -> "服务类型";
            case "recharge" -> "充值记录";
            case "consume" -> "消费记录";
            case "system" -> "系统";
            default -> entityType;
        };
    }

    private Map<String, Object> pageResult(List<?> items, int page, int size, long total, int totalPages) {
        Map<String, Object> data = new HashMap<>();
        data.put("items", items);
        data.put("page", page);
        data.put("size", size);
        data.put("total", total);
        data.put("totalPages", totalPages);
        return data;
    }

    private int safeSize(int size) {
        return size <= 0 ? 10 : Math.min(size, 100);
    }

    private long tenantId() {
        return TenantContext.getTenantId();
    }

    private long parseId(String id) {
        try {
            return Long.parseLong(id);
        } catch (Exception e) {
            throw new IllegalArgumentException("ID非法: " + id);
        }
    }

    private String defaultText(String text) {
        return text == null ? "" : text.trim();
    }

    private String safeKeyword(String keyword) {
        return keyword == null ? "" : keyword.trim();
    }

    private String nullToNormal(String status) {
        return status == null || status.isBlank() ? "normal" : status;
    }

    private void validateText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
    }

    private void validateAmount(BigDecimal value, String message) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private void validateAmountPositive(BigDecimal value, String message) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private void ensureActive(String status, String message) {
        if (!"active".equals(status)) {
            throw new IllegalArgumentException(message);
        }
    }

    private String normalizeVerifyCode(String verifyCode, String phone) {
        String v = (verifyCode == null || verifyCode.isBlank()) ? last4(phone) : verifyCode.trim();
        if (!v.matches("\\d{4}")) {
            throw new IllegalArgumentException("校验码必须是4位数字");
        }
        return v;
    }

    private String last4(String phone) {
        String p = phone == null ? "" : phone.trim();
        if (p.length() < 4) {
            throw new IllegalArgumentException("手机号格式错误");
        }
        return p.substring(p.length() - 4);
    }
}

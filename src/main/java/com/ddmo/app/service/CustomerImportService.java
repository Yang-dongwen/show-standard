package com.ddmo.app.service;

import com.ddmo.app.dto.CustomerImportResult;
import com.ddmo.app.dto.CustomerRequest;
import com.ddmo.app.model.Customer;
import com.ddmo.app.util.CsvSupport;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 会员 CSV 导入：模板下载内容 + 逐行创建（部分成功）。
 */
@Service
public class CustomerImportService {

    public static final String TEMPLATE_FILE_NAME = "customer-import-template.csv";
    public static final long MAX_FILE_BYTES = 2L * 1024 * 1024;
    public static final int MAX_ROWS = 1000;
    public static final int MAX_ERRORS_RETURNED = 50;

    private static final String COL_NAME = "会员姓名";
    private static final String COL_PHONE = "手机号";
    private static final String COL_VERIFY = "校验码";
    private static final String COL_BALANCE = "余额";
    private static final String COL_REMARK = "备注";
    private static final String COL_STATUS = "状态";

    private final BarbershopService barbershopService;

    public CustomerImportService(BarbershopService barbershopService) {
        this.barbershopService = barbershopService;
    }

    /** UTF-8 BOM + 表头 + 示例行（不含真实业务数据）。 */
    public String buildTemplateCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append(CsvSupport.escape(COL_NAME)).append(',')
            .append(CsvSupport.escape(COL_PHONE)).append(',')
            .append(CsvSupport.escape(COL_VERIFY)).append(',')
            .append(CsvSupport.escape(COL_BALANCE)).append(',')
            .append(CsvSupport.escape(COL_REMARK)).append(',')
            .append(CsvSupport.escape(COL_STATUS)).append('\n');
        sb.append(CsvSupport.escape("张三")).append(',')
            .append(CsvSupport.escape("13800000000")).append(',')
            .append(CsvSupport.escape("0000")).append(',')
            .append(CsvSupport.escape("100.00")).append(',')
            .append(CsvSupport.escape("示例：可删，从其他平台迁移时按此列填写")).append(',')
            .append(CsvSupport.escape("正常")).append('\n');
        return sb.toString();
    }

    public CustomerImportResult importCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请上传 CSV 文件");
        }
        String original = file.getOriginalFilename();
        if (original != null && !original.isBlank()) {
            String lower = original.toLowerCase(Locale.ROOT);
            if (!lower.endsWith(".csv")) {
                throw new IllegalArgumentException("仅支持 .csv 文件，请用 Excel「另存为 CSV UTF-8」");
            }
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("文件过大，请控制在 2MB 以内");
        }

        final byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            throw new IllegalArgumentException("读取上传文件失败: " + e.getMessage());
        }
        if (bytes.length == 0) {
            throw new IllegalArgumentException("文件为空");
        }

        List<String> lines = readLines(bytes);
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("文件为空");
        }

        List<String> headerCells = CsvSupport.parseLine(lines.get(0));
        Map<String, Integer> colIndex = mapHeader(headerCells);
        if (!colIndex.containsKey(COL_NAME) || !colIndex.containsKey(COL_PHONE)) {
            throw new IllegalArgumentException(
                "表头必须包含「会员姓名」「手机号」。请下载系统导入模板填写；"
                    + "若用 Excel 编辑，请「另存为 CSV UTF-8」或直接保存为 CSV（系统已兼容 GBK）后重试。"
                    + "当前首行: " + previewHeader(lines.get(0))
            );
        }

        CustomerImportResult result = new CustomerImportResult();
        Set<String> phonesInFile = new HashSet<>();
        int dataRows = 0;
        int success = 0;
        int failed = 0;

        for (int i = 1; i < lines.size(); i++) {
            int rowNum = i + 1; // 1-based 含表头，便于用户对照 Excel 行号
            String line = lines.get(i);
            if (line == null || line.isBlank()) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
                continue;
            }
            List<String> cells = CsvSupport.parseLine(line);
            if (isAllBlank(cells)) {
                continue;
            }

            dataRows++;
            if (dataRows > MAX_ROWS) {
                failed++;
                addErrorCapped(result, rowNum, "", "超过单次最多 " + MAX_ROWS + " 行，请拆分后导入");
                // 后续行同样计失败但不继续创建
                for (int j = i + 1; j < lines.size(); j++) {
                    String more = lines.get(j);
                    if (more == null || more.isBlank() || more.trim().startsWith("#")) {
                        continue;
                    }
                    if (isAllBlank(CsvSupport.parseLine(more))) {
                        continue;
                    }
                    dataRows++;
                    failed++;
                }
                break;
            }

            String name = cell(cells, colIndex, COL_NAME);
            String phone = cell(cells, colIndex, COL_PHONE);
            String verify = cell(cells, colIndex, COL_VERIFY);
            String balanceRaw = cell(cells, colIndex, COL_BALANCE);
            String remark = cell(cells, colIndex, COL_REMARK);
            String statusRaw = cell(cells, colIndex, COL_STATUS);

            try {
                if (phone.isBlank()) {
                    throw new IllegalArgumentException("手机号不能为空");
                }
                if (phonesInFile.contains(phone)) {
                    throw new IllegalArgumentException("文件内手机号重复");
                }
                phonesInFile.add(phone);

                CustomerRequest request = new CustomerRequest();
                request.setName(name);
                request.setPhone(phone);
                request.setVerifyCode(verify.isBlank() ? null : verify);
                request.setRemark(remark.isBlank() ? null : remark);
                request.setInitialRechargeAmount(parseBalance(balanceRaw));

                boolean inactive = isInactiveStatus(statusRaw);
                Customer created = barbershopService.createCustomerForImport(request);
                if (inactive && created != null) {
                    barbershopService.toggleCustomerStatus(created.getId());
                }
                success++;
            } catch (IllegalArgumentException ex) {
                failed++;
                addErrorCapped(result, rowNum, phone, ex.getMessage());
            } catch (Exception ex) {
                failed++;
                String msg = ex.getMessage() == null ? "导入失败" : ex.getMessage();
                if (msg.contains("UNIQUE") || msg.contains("unique") || msg.contains("约束")) {
                    msg = "手机号必须唯一";
                }
                addErrorCapped(result, rowNum, phone, msg);
            }
        }

        result.setTotal(dataRows);
        result.setSuccess(success);
        result.setFailed(failed);
        barbershopService.recordCustomerImportSummary(success, failed, dataRows);
        return result;
    }

    /**
     * 读取 CSV 行：兼容 UTF-8（含 BOM）与中文 Windows Excel 默认的 GBK/GB18030。
     */
    public static List<String> readLines(byte[] bytes) {
        Charset charset = detectCharset(bytes);
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(new ByteArrayInputStream(bytes), charset))) {
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                // 去掉 UTF-16/异常残留的 NUL，以及行尾多余空白
                if (line.indexOf('\u0000') >= 0) {
                    line = line.replace("\u0000", "");
                }
                lines.add(line);
            }
            return lines;
        } catch (Exception e) {
            throw new IllegalArgumentException("读取 CSV 失败: " + e.getMessage());
        }
    }

    /**
     * 编码探测：BOM → 试 UTF-8 表头 → 试 GB18030 表头 → 默认 UTF-8。
     */
    public static Charset detectCharset(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return StandardCharsets.UTF_8;
        }
        // UTF-8 BOM
        if (bytes.length >= 3
            && (bytes[0] & 0xFF) == 0xEF
            && (bytes[1] & 0xFF) == 0xBB
            && (bytes[2] & 0xFF) == 0xBF) {
            return StandardCharsets.UTF_8;
        }
        // UTF-16 LE BOM
        if (bytes.length >= 2
            && (bytes[0] & 0xFF) == 0xFF
            && (bytes[1] & 0xFF) == 0xFE) {
            return StandardCharsets.UTF_16LE;
        }
        // UTF-16 BE BOM
        if (bytes.length >= 2
            && (bytes[0] & 0xFF) == 0xFE
            && (bytes[1] & 0xFF) == 0xFF) {
            return StandardCharsets.UTF_16BE;
        }

        Charset gbk = Charset.forName("GB18030");
        if (headerLooksValid(bytes, StandardCharsets.UTF_8)) {
            return StandardCharsets.UTF_8;
        }
        if (headerLooksValid(bytes, gbk)) {
            return gbk;
        }
        // 无合法表头时：含高位字节更像本地 ANSI/GBK（Excel 另存常见）
        if (hasHighBytes(bytes)) {
            return gbk;
        }
        return StandardCharsets.UTF_8;
    }

    private static boolean headerLooksValid(byte[] bytes, Charset charset) {
        try {
            String first = firstLine(bytes, charset);
            if (first == null || first.isBlank()) {
                return false;
            }
            List<String> cells = CsvSupport.parseLine(first);
            Map<String, Integer> map = mapHeader(cells);
            return map.containsKey(COL_NAME) && map.containsKey(COL_PHONE);
        } catch (Exception e) {
            return false;
        }
    }

    private static String firstLine(byte[] bytes, Charset charset) {
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(new ByteArrayInputStream(bytes), charset))) {
            String line = reader.readLine();
            if (line != null && line.indexOf('\u0000') >= 0) {
                line = line.replace("\u0000", "");
            }
            return line;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean hasHighBytes(byte[] bytes) {
        int n = Math.min(bytes.length, 4096);
        for (int i = 0; i < n; i++) {
            if ((bytes[i] & 0xFF) >= 0x80) {
                return true;
            }
        }
        return false;
    }

    private static String previewHeader(String headerLine) {
        if (headerLine == null) {
            return "";
        }
        String s = headerLine.trim();
        if (s.length() > 80) {
            s = s.substring(0, 80) + "...";
        }
        return s;
    }

    private static Map<String, Integer> mapHeader(List<String> headerCells) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < headerCells.size(); i++) {
            String h = headerCells.get(i) == null ? "" : headerCells.get(i).trim();
            if (!h.isEmpty() && h.charAt(0) == '\uFEFF') {
                h = h.substring(1).trim();
            }
            // Excel 偶发全角空格 / 不可见字符
            h = h.replace('\u00A0', ' ').replace('\u3000', ' ').trim();
            if (!h.isEmpty()) {
                map.put(h, i);
            }
        }
        return map;
    }

    private static String cell(List<String> cells, Map<String, Integer> colIndex, String col) {
        Integer idx = colIndex.get(col);
        if (idx == null) {
            return "";
        }
        return CsvSupport.cell(cells, idx);
    }

    private static boolean isAllBlank(List<String> cells) {
        if (cells == null || cells.isEmpty()) {
            return true;
        }
        for (String c : cells) {
            if (c != null && !c.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static BigDecimal parseBalance(String raw) {
        if (raw == null || raw.isBlank()) {
            return BigDecimal.ZERO;
        }
        String s = raw.trim().replace(",", "");
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("余额格式不正确");
        }
    }

    private static boolean isInactiveStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        return "停用".equals(raw.trim())
            || "inactive".equals(s)
            || "disabled".equals(s)
            || "0".equals(s);
    }

    private static void addErrorCapped(CustomerImportResult result, int row, String phone, String message) {
        if (result.getErrors().size() < MAX_ERRORS_RETURNED) {
            result.addError(row, phone, message);
        }
    }
}

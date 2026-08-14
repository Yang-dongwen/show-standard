package com.ddmo.app.dto;

import java.util.ArrayList;
import java.util.List;

/** 会员 CSV 导入结果（部分成功）。 */
public class CustomerImportResult {

    private int total;
    private int success;
    private int failed;
    private final List<RowError> errors = new ArrayList<>();

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getSuccess() {
        return success;
    }

    public void setSuccess(int success) {
        this.success = success;
    }

    public int getFailed() {
        return failed;
    }

    public void setFailed(int failed) {
        this.failed = failed;
    }

    public List<RowError> getErrors() {
        return errors;
    }

    public void addError(int row, String phone, String message) {
        errors.add(new RowError(row, phone, message));
    }

    public static class RowError {
        private int row;
        private String phone;
        private String message;

        public RowError() {
        }

        public RowError(int row, String phone, String message) {
            this.row = row;
            this.phone = phone == null ? "" : phone;
            this.message = message;
        }

        public int getRow() {
            return row;
        }

        public void setRow(int row) {
            this.row = row;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}

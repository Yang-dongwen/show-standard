package com.ddmo.app.controller;

import com.ddmo.app.dto.ApiResponse;
import com.ddmo.app.service.BackupService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system/backup")
public class BackupController {

    private final BackupService backupService;

    public BackupController(BackupService backupService) {
        this.backupService = backupService;
    }

    /** 立即备份当前数据库到备份目录 */
    @PostMapping
    public ApiResponse<Map<String, Object>> create() {
        return ApiResponse.ok("备份成功", backupService.createBackup());
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.ok(backupService.listBackups());
    }

    /** 下载指定备份文件 */
    @GetMapping("/{fileName}")
    public ResponseEntity<Resource> download(@PathVariable String fileName) {
        Path file = backupService.resolveBackupFile(fileName);
        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFileName() + "\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(resource);
    }

    /** 从已有备份排队恢复（需重启） */
    @PostMapping("/restore")
    public ApiResponse<Map<String, Object>> restoreFromBackup(@RequestParam String fileName) {
        return ApiResponse.ok(backupService.scheduleRestoreFromBackup(fileName));
    }

    /** 上传 db 文件排队恢复（需重启） */
    @PostMapping("/restore-upload")
    public ApiResponse<Map<String, Object>> restoreUpload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请上传备份文件");
        }
        try {
            return ApiResponse.ok(backupService.scheduleRestoreFromUpload(
                file.getInputStream(),
                file.getOriginalFilename()
            ));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("读取上传文件失败: " + e.getMessage(), e);
        }
    }
}

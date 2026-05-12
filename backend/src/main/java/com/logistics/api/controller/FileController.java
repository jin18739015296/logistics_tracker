package com.logistics.api.controller;

import com.logistics.api.common.Result;
import com.logistics.api.service.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/file")
public class FileController {

    @Autowired
    private FileStorageService fileStorageService;

    private static final String[] ALLOWED_TYPES = {"image/jpeg", "image/png", "image/gif", "image/webp"};
    
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    @PostMapping("/upload")
    public ResponseEntity<Result<Map<String, Object>>> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "type", defaultValue = "other") String type) {
        
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Result.error(80001, "请选择要上传的文件"));
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            return ResponseEntity.badRequest().body(Result.error(80002, "文件大小不能超过10MB"));
        }

        String contentType = file.getContentType();
        boolean isAllowed = false;
        for (String allowedType : ALLOWED_TYPES) {
            if (allowedType.equals(contentType)) {
                isAllowed = true;
                break;
            }
        }
        if (!isAllowed) {
            return ResponseEntity.badRequest().body(Result.error(80003, "只支持 JPG、PNG、GIF、WebP 格式的图片"));
        }

        try {
            String fileUrl = fileStorageService.uploadFile(file, type, file.getOriginalFilename());
            
            log.info("文件上传成功: type={}, url={}", type, fileUrl);

            Map<String, Object> result = new HashMap<>();
            result.put("uri", fileUrl);
            result.put("originalName", file.getOriginalFilename());
            result.put("size", file.getSize());
            
            return ResponseEntity.ok(Result.success("上传成功", result));

        } catch (Exception e) {
            log.error("文件上传失败", e);
            return ResponseEntity.status(500).body(Result.error(80004, "文件上传失败: " + e.getMessage()));
        }
    }
}

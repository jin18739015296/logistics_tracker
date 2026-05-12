package com.logistics.api.controller;

import com.logistics.api.common.Result;
import com.logistics.api.service.CaptchaStorageService;
import com.logistics.api.util.CaptchaUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/captcha")
public class CaptchaController {

    @Autowired
    private CaptchaStorageService captchaStorageService;

    @GetMapping("/image")
    public ResponseEntity<Result<Map<String, Object>>> getCaptcha() {
        Map<String, String> captchaData = CaptchaUtil.generateCaptcha();
        
        if (captchaData == null || captchaData.get("image") == null) {
            return ResponseEntity.status(500).body(Result.error(10010, "验证码生成失败"));
        }

        String captchaId = UUID.randomUUID().toString();
        
        String code = captchaData.get("code").toLowerCase();
        captchaStorageService.store(captchaId, code);

        log.info("图形验证码已生成: captchaId={}", captchaId);

        Map<String, Object> result = new HashMap<>();
        result.put("captchaId", captchaId);
        result.put("image", captchaData.get("image"));
        
        return ResponseEntity.ok(Result.success("验证码生成成功", result));
    }

    @PostMapping("/verify")
    public ResponseEntity<Result<Map<String, Object>>> verifyCaptcha(@RequestBody Map<String, String> request) {
        String captchaId = request.get("captchaId");
        String code = request.get("code");

        if (captchaId == null || code == null) {
            return ResponseEntity.badRequest().body(Result.error(10011, "验证码ID和验证码不能为空"));
        }

        String savedCode = captchaStorageService.get(captchaId);

        if (savedCode == null) {
            return ResponseEntity.badRequest().body(Result.error(10012, "验证码已过期，请重新获取"));
        }

        boolean valid = savedCode.equals(code.toLowerCase());
        
        Map<String, Object> result = new HashMap<>();
        if (valid) {
            captchaStorageService.delete(captchaId);
            result.put("valid", true);
            log.info("图形验证码验证成功: captchaId={}", captchaId);
            return ResponseEntity.ok(Result.success("验证成功", result));
        } else {
            result.put("valid", false);
            log.warn("图形验证码验证失败: captchaId={}", captchaId);
            return ResponseEntity.badRequest().body(Result.error(10013, "验证码错误"));
        }
    }

    @GetMapping("/refresh")
    public ResponseEntity<Result<Map<String, Object>>> refreshCaptcha(@RequestParam(required = false) String oldCaptchaId) {
        if (oldCaptchaId != null) {
            captchaStorageService.delete(oldCaptchaId);
        }
        
        return getCaptcha();
    }
}

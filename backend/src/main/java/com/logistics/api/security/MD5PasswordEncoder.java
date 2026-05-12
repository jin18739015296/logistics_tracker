package com.logistics.api.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Slf4j
public class MD5PasswordEncoder implements PasswordEncoder {

    @Override
    public String encode(CharSequence rawPassword) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            // 使用 UTF-8 编码
            md.update(rawPassword.toString().getBytes(StandardCharsets.UTF_8));
            byte[] bytes = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            // 转换为小写，与数据库中的密码格式一致
            return sb.toString().toLowerCase();
        } catch (NoSuchAlgorithmException e) {
            log.error("MD5编码失败", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (encodedPassword == null) {
            return false;
        }
        // 将数据库中的密码也转为小写进行比较
        String encodedRaw = encode(rawPassword);
        boolean match = encodedRaw.equals(encodedPassword.toLowerCase());
        
        // 仅在密码不匹配时记录日志（用于安全审计）
        if (!match) {
            log.warn("密码验证失败");
        }
        
        return match;
    }
}

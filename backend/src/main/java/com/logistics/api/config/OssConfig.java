package com.logistics.api.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 阿里云 OSS 配置
 */
@Data
@Configuration
public class OssConfig {

    @Value("${aliyun.oss.endpoint:}")
    private String endpoint;

    @Value("${aliyun.oss.access-key-id:}")
    private String accessKeyId;

    @Value("${aliyun.oss.access-key-secret:}")
    private String accessKeySecret;

    @Value("${aliyun.oss.bucket-name:}")
    private String bucketName;

    @Value("${aliyun.oss.enabled:false}")
    private boolean enabled;

    /**
     * 创建 OSS 客户端
     */
    @Bean
    public OSS ossClient() {
        if (!enabled) {
            return null;
        }
        return new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
    }
}

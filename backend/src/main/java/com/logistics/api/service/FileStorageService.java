package com.logistics.api.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储服务接口
 * 支持阿里云 OSS 和本地存储两种模式
 */
public interface FileStorageService {

    /**
     * 上传文件
     * @param file     文件
     * @param type     文件类型（决定存储路径）
     * @param fileName 原始文件名
     * @return 文件访问 URL
     */
    String uploadFile(MultipartFile file, String type, String fileName);

    /**
     * 删除文件
     * @param fileUrl 文件URL
     */
    void deleteFile(String fileUrl);
}

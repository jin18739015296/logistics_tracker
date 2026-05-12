package com.logistics.api.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.logistics.api.service.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Slf4j
@Service
public class FileStorageServiceImpl implements FileStorageService {

    @Autowired
    private Environment env;

    @Value("${aliyun.oss.enabled:false}")
    private boolean ossEnabled;

    @Value("${aliyun.oss.endpoint:}")
    private String ossEndpoint;

    @Value("${aliyun.oss.access-key-id:}")
    private String ossAccessKeyId;

    @Value("${aliyun.oss.access-key-secret:}")
    private String ossAccessKeySecret;

    @Value("${aliyun.oss.bucket-name:}")
    private String ossBucketName;

    @Value("${file.upload.path:./uploads}")
    private String localUploadPath;

    @Override
    public String uploadFile(MultipartFile file, String type, String fileName) {
        log.info("上传文件, type: {}, fileName: {}", type, fileName);

        // 判断是否使用OSS
        if (isOssEnabled()) {
            return uploadToOss(file, type, fileName);
        } else {
            return uploadToLocal(file, type, fileName);
        }
    }

    @Override
    public void deleteFile(String fileUrl) {
        log.info("删除文件, fileUrl: {}", fileUrl);

        if (isOssEnabled() && fileUrl.contains(ossBucketName)) {
            deleteFromOss(fileUrl);
        } else {
            deleteFromLocal(fileUrl);
        }
    }

    private boolean isOssEnabled() {
        return ossEnabled;
    }

    private String uploadToOss(MultipartFile file, String type, String fileName) {
        OSS ossClient = new OSSClientBuilder().build(ossEndpoint, ossAccessKeyId, ossAccessKeySecret);
        try {
            String extension = getFileExtension(fileName);
            String newFileName = UUID.randomUUID().toString() + "." + extension;
            String objectName = type + "/" + newFileName;

            ossClient.putObject(ossBucketName, objectName, file.getInputStream());

            return "https://" + ossBucketName + "." + ossEndpoint + "/" + objectName;
        } catch (IOException e) {
            log.error("上传文件到OSS失败", e);
            throw new RuntimeException("文件上传失败", e);
        } finally {
            ossClient.shutdown();
        }
    }

    private String uploadToLocal(MultipartFile file, String type, String fileName) {
        try {
            String extension = getFileExtension(fileName);
            String newFileName = UUID.randomUUID().toString() + "." + extension;

            Path uploadPath = Paths.get(localUploadPath, type);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            Path filePath = uploadPath.resolve(newFileName);
            Files.copy(file.getInputStream(), filePath);

            return "/uploads/" + type + "/" + newFileName;
        } catch (IOException e) {
            log.error("上传文件到本地失败", e);
            throw new RuntimeException("文件上传失败", e);
        }
    }

    private void deleteFromOss(String fileUrl) {
        OSS ossClient = new OSSClientBuilder().build(ossEndpoint, ossAccessKeyId, ossAccessKeySecret);
        try {
            String objectName = fileUrl.substring(fileUrl.indexOf(ossBucketName) + ossBucketName.length() + 1);
            ossClient.deleteObject(ossBucketName, objectName);
        } finally {
            ossClient.shutdown();
        }
    }

    private void deleteFromLocal(String fileUrl) {
        try {
            String relativePath = fileUrl.startsWith("/") ? fileUrl.substring(1) : fileUrl;
            Path filePath = Paths.get(localUploadPath).getParent().resolve(relativePath);
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.error("删除本地文件失败", e);
        }
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.lastIndexOf(".") == -1) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1);
    }
}

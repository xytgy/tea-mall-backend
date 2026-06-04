package com.xytgy.teamallbackend.utils;


import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.xytgy.teamallbackend.common.ResultCode;
import com.xytgy.teamallbackend.security.SecurityUtils;
import com.xytgy.teamallbackend.exception.ServiceException;
import com.xytgy.teamallbackend.properties.AliyunOSSProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AliyunOSSUtils {

    private final AliyunOSSProperties aliyunOSSProperties;

    public String upload(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (StringUtils.hasText(originalFilename) && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String fileName = UUID.randomUUID().toString().replace("-", "") + extension;
        return uploadWithKey(file, fileName);
    }

    public String uploadTeaCircleImage(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (StringUtils.hasText(originalFilename) && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String key = "tea-circle/" + datePath + "/" + UUID.randomUUID().toString().replace("-", "") + extension;
        return uploadWithKey(file, key);
    }

    public String uploadWithKey(MultipartFile file, String objectKey) {
        if (SecurityUtils.getCurrentUserId() == null) {
            throw new ServiceException(ResultCode.UNAUTHORIZED, "未登录");
        }

        if (file.isEmpty()) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "上传文件不能为空");
        }

        // 检查文件类型，简单防范
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "仅支持上传图片文件");
        }

        if (!StringUtils.hasText(aliyunOSSProperties.getEndpoint())
                || !StringUtils.hasText(aliyunOSSProperties.getAccessKeyId())
                || !StringUtils.hasText(aliyunOSSProperties.getAccessKeySecret())
                || !StringUtils.hasText(aliyunOSSProperties.getBucketName())) {
            throw new ServiceException(ResultCode.ERROR, "OSS 配置不完整，请检查 application-dev.yaml");
        }

        String endpoint = aliyunOSSProperties.getEndpoint().trim();
        if (!endpoint.startsWith("http://") && !endpoint.startsWith("https://")) {
            endpoint = "https://" + endpoint;
        }
        OSS ossClient = new OSSClientBuilder().build(
                endpoint,
                aliyunOSSProperties.getAccessKeyId(),
                aliyunOSSProperties.getAccessKeySecret()
        );

        try {
            // 上传文件到指定的 Bucket
            ossClient.putObject(aliyunOSSProperties.getBucketName(), objectKey, file.getInputStream());
        } catch (IOException e) {
            log.error("文件上传到阿里云 OSS 失败: {}", e.getMessage());
            throw new ServiceException(ResultCode.ERROR, "文件上传失败");
        } finally {
            if (ossClient != null) {
                ossClient.shutdown();
            }
        }

        StringBuilder stringBuilder = new StringBuilder("https://");
        stringBuilder
                .append(aliyunOSSProperties.getBucketName())
                .append(".")
                .append(aliyunOSSProperties.getEndpoint())
                .append("/")
                .append(objectKey);

        log.info("文件上传成功，访问路径为: {}", stringBuilder.toString());

        return stringBuilder.toString();
    }

    public String uploadAvatar(InputStream inputStream, String originalFilename) {
        if (SecurityUtils.getCurrentUserId() == null) {
            throw new ServiceException(ResultCode.UNAUTHORIZED, "未登录");
        }
        if (inputStream == null || !StringUtils.hasText(originalFilename)) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "上传参数不完整");
        }
        if (!StringUtils.hasText(aliyunOSSProperties.getEndpoint())
                || !StringUtils.hasText(aliyunOSSProperties.getAccessKeyId())
                || !StringUtils.hasText(aliyunOSSProperties.getAccessKeySecret())
                || !StringUtils.hasText(aliyunOSSProperties.getBucketName())) {
            throw new ServiceException(ResultCode.ERROR, "OSS 配置不完整，请检查 application-dev.yaml");
        }
        String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        String fileName = UUID.randomUUID() + extension;
        String objectKey = "avatars/" + fileName;

        String endpoint = aliyunOSSProperties.getEndpoint().trim();
        if (!endpoint.startsWith("http://") && !endpoint.startsWith("https://")) {
            endpoint = "https://" + endpoint;
        }
        OSS ossClient = new OSSClientBuilder().build(
                endpoint,
                aliyunOSSProperties.getAccessKeyId(),
                aliyunOSSProperties.getAccessKeySecret()
        );
        try {
            ossClient.putObject(aliyunOSSProperties.getBucketName(), objectKey, inputStream);
            StringBuilder sb = new StringBuilder("https://");
            sb.append(aliyunOSSProperties.getBucketName())
              .append(".")
              .append(aliyunOSSProperties.getEndpoint())
              .append("/")
              .append(objectKey);
            log.info("头像上传成功，访问路径为: {}", sb);
            return sb.toString();
        } catch (Exception e) {
            log.error("头像上传到阿里云 OSS 失败: {}", e.getMessage());
            throw new ServiceException(ResultCode.ERROR, "头像上传失败");
        } finally {
            ossClient.shutdown();
        }
    }

}

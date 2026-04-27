package com.xytgy.teamallbackend.utils;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.UUID;

@Component
public class AliyunOssUtil {

    @Value("${app.aliyun.oss.endpoint}")
    private String endpoint;

    @Value("${app.aliyun.oss.accessKeyId}")
    private String accessKeyId;

    @Value("${app.aliyun.oss.accessKeySecret}")
    private String accessKeySecret;

    @Value("${app.aliyun.oss.bucketName}")
    private String bucketName;

    /**
     * 上传文件到阿里云 OSS
     *
     * @param inputStream 文件输入流
     * @param originalFilename 原始文件名
     * @return 上传后的文件访问 URL
     */
    public String upload(InputStream inputStream, String originalFilename) {
        // 生成唯一的文件名，防止覆盖
        String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        String fileName = UUID.randomUUID().toString() + extension;
        
        // 可选：按照日期或业务划分文件夹，这里放到 avatars 目录下
        String objectName = "avatars/" + fileName;

        // 创建OSSClient实例
        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);

        try {
            // 上传文件流
            ossClient.putObject(bucketName, objectName, inputStream);

            // 返回拼接好的文件访问 URL
            // 格式类似于: https://bucketName.endpoint/objectName
            String url = "https://" + bucketName + "." + endpoint + "/" + objectName;
            return url;
        } finally {
            if (ossClient != null) {
                ossClient.shutdown();
            }
        }
    }
}
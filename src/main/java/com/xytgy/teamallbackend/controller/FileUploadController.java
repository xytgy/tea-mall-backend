package com.xytgy.teamallbackend.controller;

import com.xytgy.teamallbackend.common.Result;
import com.xytgy.teamallbackend.common.ResultCode;
import com.xytgy.teamallbackend.common.UserContext;
import com.xytgy.teamallbackend.exception.ServiceException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@Tag(name = "文件上传")
@SecurityRequirement(name = "BearerAuth")
public class FileUploadController {

    // 可以在 application.yaml 中配置上传目录，默认在项目根目录的 uploads 文件夹下
    @Value("${upload.dir:uploads/images/}")
    private String uploadDir;

    @PostMapping("/upload")
    @Operation(summary = "通用图片文件上传接口")
    public Result<String> uploadImage(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        if (UserContext.getCurrentUserId() == null) {
            throw new ServiceException(ResultCode.UNAUTHORIZED, "未登录");
        }
        
        if (file.isEmpty()) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "上传文件不能为空");
        }

        try {
            // 获取原始文件名和后缀
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (StringUtils.hasText(originalFilename) && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }

            // 检查文件类型，简单防范
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                throw new ServiceException(ResultCode.BAD_REQUEST, "仅支持上传图片文件");
            }

            // 生成新的UUID文件名
            String newFilename = UUID.randomUUID().toString().replace("-", "") + extension;

            // 确保目录存在（使用绝对路径）
            File dir = new File(uploadDir).getAbsoluteFile();
            if (!dir.exists()) {
                dir.mkdirs();
            }

            // 保存文件
            File dest = new File(dir, newFilename);
            file.transferTo(dest);

            // 拼接完整可访问 URL (结合当前请求地址)
            String scheme = request.getScheme(); // http
            String serverName = request.getServerName(); // localhost
            int serverPort = request.getServerPort(); // 8080
            
            // 例如: http://localhost:8080/uploads/images/xxx.jpg
            String imageUrl = scheme + "://" + serverName + ":" + serverPort + "/uploads/images/" + newFilename;
            
            return Result.success("上传成功", imageUrl);

        } catch (IOException e) {
            e.printStackTrace();
            throw new ServiceException(ResultCode.ERROR, "文件上传失败");
        }
    }
}

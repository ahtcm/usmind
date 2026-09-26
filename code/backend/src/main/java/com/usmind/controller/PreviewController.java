package com.usmind.controller;

import com.usmind.common.BusinessException;
import com.usmind.common.Result;
import com.usmind.entity.FileResource;
import com.usmind.service.FileService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/preview")
public class PreviewController {

    private final FileService fileService;

    @Value("${file.upload-dir:C:/Users/25750/Desktop/usmind/uploads}")
    private String uploadDir;

    @Value("${preview.office-server:}")
    private String officeServer;

    public PreviewController(FileService fileService) {
        this.fileService = fileService;
    }

    @GetMapping("/{fileId}")
    public void preview(@PathVariable Long fileId, HttpServletResponse response) throws Exception {
        FileResource r = fileService.getById(fileId);
        String mime = r.getMimeType() == null ? "" : r.getMimeType().toLowerCase();
        String name = r.getOriginalName() == null ? "" : r.getOriginalName().toLowerCase();

        boolean isPdf = "application/pdf".equals(mime) || name.endsWith(".pdf");
        boolean isImage = mime.startsWith("image/")
                || name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                || name.endsWith(".gif") || name.endsWith(".webp") || name.endsWith(".bmp");

        if (isPdf || isImage) {
            Path path = Paths.get(uploadDir, r.getStoredName().replace("/", java.io.File.separator));
            if (!Files.exists(path)) {
                throw new BusinessException(404, "文件不存在");
            }
            response.setContentType(isPdf ? MediaType.APPLICATION_PDF_VALUE : mime);
            try (InputStream in = Files.newInputStream(path);
                 OutputStream out = response.getOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            }
            return;
        }

        // office 文档：返回预览方案提示（前端可跳转 officeServer 转换）
        throw new BusinessException(400,
                "该文件类型暂不支持在线预览，请下载后查看。文件地址: " + r.getUrl());
    }

    @GetMapping("/url")
    public Result<Map<String, Object>> previewUrl(@RequestParam("url") String url) {
        Map<String, Object> data = new HashMap<>();
        if (officeServer != null && !officeServer.isBlank()) {
            data.put("type", "office");
            data.put("previewUrl", officeServer + "?src=" + url);
        } else {
            data.put("type", "download");
            data.put("url", url);
            data.put("message", "未配置文档预览服务器(kkFileView/onlyoffice)，返回下载地址");
        }
        return Result.ok(data);
    }
}

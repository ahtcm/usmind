package com.usmind.controller;

import com.usmind.common.BusinessException;
import com.usmind.common.Result;
import com.usmind.common.SecurityUtil;
import com.usmind.entity.FileResource;
import com.usmind.service.FileService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@RestController
@RequestMapping("/api/file")
public class FileController {

    private final FileService fileService;
    private final com.usmind.mapper.SysUserMapper sysUserMapper;

    @Value("${file.upload-dir:C:/Users/25750/Desktop/usmind/uploads}")
    private String uploadDir;

    public FileController(FileService fileService, com.usmind.mapper.SysUserMapper sysUserMapper) {
        this.fileService = fileService;
        this.sysUserMapper = sysUserMapper;
    }

    @PostMapping("/upload")
    public Result<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                             @RequestParam(value = "mindMapId", required = false) Long mindMapId) throws Exception {
        return Result.ok(fileService.upload(SecurityUtil.currentUserId(), file, mindMapId));
    }

    @PostMapping("/upload-avatar")
    public Result<Map<String, Object>> uploadAvatar(@RequestParam("file") MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) throw new BusinessException(400, "请选择图片");
        String ct = file.getContentType();
        if (ct == null || !ct.startsWith("image/")) throw new BusinessException(400, "仅支持图片文件");
        if (file.getSize() > 2 * 1024 * 1024) throw new BusinessException(400, "头像图片不能超过 2MB");
        Long userId = SecurityUtil.currentUserId();
        Map<String, Object> data = fileService.upload(userId, file, null);
        // 上传成功即更新用户头像，无需再走"保存"
        try {
            com.usmind.entity.SysUser u = sysUserMapper.selectById(userId);
            if (u != null && data.get("url") != null) {
                u.setAvatar(String.valueOf(data.get("url")));
                sysUserMapper.updateById(u);
            }
        } catch (Exception e) {
            // 头像更新失败不影响上传结果
        }
        return Result.ok(data);
    }

    @PutMapping("/content")
    public Result<Void> updateContent(@RequestBody Map<String, String> body) {
        fileService.updateContentByUrl(body.get("url"), body.get("content"));
        return Result.ok();
    }

    /** 按URL删除资源（节点附件移除时调用）：仍被其他导图/头像引用则不删除，避免破坏其他引用 */
    @PostMapping("/delete-by-url")
    public Result<Map<String, Object>> deleteByUrl(@RequestBody Map<String, String> body) {
        return Result.ok(fileService.deleteByUrl(body.get("url")));
    }

    @GetMapping("/{id}")
    public void get(@PathVariable Long id, HttpServletResponse response) throws Exception {
        FileResource r = fileService.getById(id);
        String url = r.getUrl();
        if (url != null && url.startsWith("http")) {
            response.sendRedirect(url);
            return;
        }
        // 本地文件流
        Path path = Paths.get(uploadDir, r.getStoredName().replace("/", File.separator));
        if (!Files.exists(path)) {
            throw new BusinessException(404, "文件不存在");
        }
        response.setContentType(r.getMimeType() == null || r.getMimeType().isEmpty()
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE : r.getMimeType());
        response.setHeader("Content-Disposition",
                "inline; filename=\"" + URLEncoder.encode(r.getOriginalName(), StandardCharsets.UTF_8) + "\"");
        try (InputStream in = Files.newInputStream(path);
             OutputStream out = response.getOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        }
    }
}

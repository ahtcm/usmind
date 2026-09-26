package com.usmind.service;

import com.usmind.entity.FileResource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface FileService {
    Map<String, Object> upload(long userId, MultipartFile file, Long mindMapId) throws IOException;
    FileResource getById(Long id);
    Map<String, Object> pageFiles(int page, int size, String keyword);
    void deleteFile(Long id);
    void deleteBatch(List<Long> ids);
    /** 更新本地文本文件内容（按资源 URL 定位） */
    void updateContentByUrl(String url, String content);
}

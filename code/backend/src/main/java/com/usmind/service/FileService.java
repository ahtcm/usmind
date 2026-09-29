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
    /** 全量资源记录（管理后台孤儿统计用） */
    java.util.List<FileResource> pageAll();
    /** 删除资源记录并物理删除文件；force=true 时忽略引用检查 */
    void deleteFile(Long id, boolean force);
    void deleteBatch(List<Long> ids, boolean force);
    /** 按 URL 删除（用户端节点附件删除）：仍被其他导图/头像引用时不删除 */
    Map<String, Object> deleteByUrl(String url);
    /** 统计该 URL 在全库导图数据/头像中的引用数 */
    int countRefs(String url);
    /** 清理孤儿资源：不再被任何导图/头像引用的文件记录与物理文件 */
    Map<String, Object> orphanCleanup();
    /** 更新本地文本文件内容（按资源 URL 定位） */
    void updateContentByUrl(String url, String content);
}

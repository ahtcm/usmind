package com.usmind.controller;

import com.usmind.common.Result;
import com.usmind.common.SecurityUtil;
import com.usmind.entity.NodeComment;
import com.usmind.service.NodeCommentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 作者侧评论：编辑器内查看/回复评论、标记节点已读（未读节点在前端闪烁提示）。
 * 已登录 + 导图归属校验（NodeCommentService.requireOwned）。
 */
@RestController
@RequestMapping("/api/mindmaps/{id}/comments")
public class MindMapCommentController {

    private final NodeCommentService nodeCommentService;

    @Value("${ipguard.trusted-proxy:false}")
    private boolean trustedProxy;

    public MindMapCommentController(NodeCommentService nodeCommentService) {
        this.nodeCommentService = nodeCommentService;
    }

    private String clientIp(HttpServletRequest request) {
        if (trustedProxy) {
            String real = request.getHeader("X-Real-IP");
            if (real != null && !real.isBlank()) return real.trim();
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                int i = xff.indexOf(',');
                return (i > 0 ? xff.substring(0, i) : xff).trim();
            }
        }
        return request.getRemoteAddr() == null ? "" : request.getRemoteAddr();
    }

    /** 增量拉取评论 + 各节点未读状态（轮询用）：?afterId=上次最大评论 id */
    @GetMapping
    public Result<Map<String, Object>> list(@PathVariable Long id,
                                            @RequestParam(required = false) Long afterId) {
        long userId = SecurityUtil.currentUserId();
        List<NodeComment> comments = nodeCommentService.listMindMapComments(userId, id, afterId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("comments", comments);
        data.put("unread", nodeCommentService.unreadByNode(userId, id));
        data.put("total", nodeCommentService.countByMindMap(id));
        return Result.ok(data);
    }

    /** 作者回复评论：body {commentId, content} */
    @PostMapping("/reply")
    public Result<NodeComment> reply(@PathVariable Long id,
                                     @RequestBody Map<String, String> body,
                                     HttpServletRequest request) {
        long userId = SecurityUtil.currentUserId();
        Long commentId = null;
        String cid = body.get("commentId");
        if (cid != null && !cid.isBlank()) {
            try { commentId = Long.valueOf(cid); } catch (Exception ignored) {}
        }
        if (commentId == null) throw new com.usmind.common.BusinessException(400, "评论标识缺失");
        NodeComment c = nodeCommentService.authorReply(userId, id, commentId, body.get("content"), clientIp(request));
        return Result.ok(c);
    }

    /** 标记某节点已读（点击节点后调用，停止闪烁）：body {nodeId} */
    @PostMapping("/read")
    public Result<Void> read(@PathVariable Long id, @RequestBody Map<String, String> body) {
        long userId = SecurityUtil.currentUserId();
        nodeCommentService.markNodeRead(userId, id, body.get("nodeId"));
        return Result.ok();
    }
}

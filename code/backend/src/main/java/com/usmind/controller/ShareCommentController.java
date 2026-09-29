package com.usmind.controller;

import com.usmind.common.Result;
import com.usmind.entity.NodeComment;
import com.usmind.service.NodeCommentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 分享页评论：访客输入密码后可对节点评论/回复，评论接口独立限频（超频自动拉黑 IP）。
 * 密码校验在 Service 内完成（与分享访问同规则），该接口保持 permitAll。
 */
@RestController
@RequestMapping("/api/share/{token}/comments")
public class ShareCommentController {

    private final NodeCommentService nodeCommentService;

    @Value("${ipguard.trusted-proxy:false}")
    private boolean trustedProxy;

    public ShareCommentController(NodeCommentService nodeCommentService) {
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

    /** 发评论/回复：body {nodeId, parentId?, nick, content, password?} */
    @PostMapping
    public Result<NodeComment> add(@PathVariable String token,
                                   @RequestBody java.util.Map<String, String> body,
                                   HttpServletRequest request) {
        Long parentId = null;
        String pid = body.get("parentId");
        if (pid != null && !pid.isBlank()) {
            try { parentId = Long.valueOf(pid); } catch (Exception ignored) {}
        }
        NodeComment c = nodeCommentService.addShareComment(
                token, body.get("password"), body.get("nodeId"), parentId,
                body.get("nick"), body.get("content"), clientIp(request));
        return Result.ok(c);
    }

    /** 拉取全部评论（按 id 升序），分享页按节点分组展示 */
    @GetMapping
    public Result<List<NodeComment>> list(@PathVariable String token,
                                          @RequestParam(required = false) String password) {
        return Result.ok(nodeCommentService.listShareComments(token, password));
    }
}

package com.usmind.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.usmind.common.Result;
import com.usmind.entity.MindMap;
import com.usmind.entity.NodeComment;
import com.usmind.mapper.MindMapMapper;
import com.usmind.mapper.NodeCommentMapper;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 后台：节点评论管理（列表 / 关键字筛选 / 删除） */
@RestController
@RequestMapping("/api/admin/comments")
public class AdminCommentController {

    private final NodeCommentMapper commentMapper;
    private final MindMapMapper mindMapMapper;

    public AdminCommentController(NodeCommentMapper commentMapper, MindMapMapper mindMapMapper) {
        this.commentMapper = commentMapper;
        this.mindMapMapper = mindMapMapper;
    }

    @GetMapping
    public Result<Map<String, Object>> page(@RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "10") long size,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) String source) {
        LambdaQueryWrapper<NodeComment> qw = new LambdaQueryWrapper<NodeComment>()
                .orderByDesc(NodeComment::getCreatedAt);
        if (keyword != null && !keyword.isEmpty()) {
            qw.and(w -> w.like(NodeComment::getContent, keyword)
                    .or().like(NodeComment::getUserName, keyword));
        }
        if (source != null && !source.isEmpty()) {
            qw.eq(NodeComment::getSource, source);
        }
        Page<NodeComment> p = commentMapper.selectPage(new Page<>(page, size), qw);
        List<Map<String, Object>> list = new ArrayList<>();
        for (NodeComment c : p.getRecords()) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", c.getId());
            m.put("mindMapId", c.getMindMapId());
            m.put("nodeId", c.getNodeId());
            m.put("parentId", c.getParentId());
            m.put("userName", c.getUserName());
            m.put("content", c.getContent());
            m.put("isAuthor", c.getIsAuthor());
            m.put("userId", c.getUserId());
            m.put("source", c.getSource());
            m.put("ip", c.getIp());
            m.put("createdAt", c.getCreatedAt());
            if (c.getMindMapId() != null) {
                MindMap mm = mindMapMapper.selectById(c.getMindMapId());
                m.put("mindMapTitle", mm == null ? "" : mm.getName());
            } else {
                m.put("mindMapTitle", "");
            }
            list.add(m);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", p.getTotal());
        return Result.ok(data);
    }

    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        commentMapper.deleteById(id);
        return Result.ok();
    }
}

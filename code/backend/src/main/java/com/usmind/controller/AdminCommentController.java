package com.usmind.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.usmind.common.Result;
import com.usmind.entity.MindMap;
import com.usmind.entity.NodeComment;
import com.usmind.entity.OrgCollab;
import com.usmind.mapper.MindMapMapper;
import com.usmind.mapper.NodeCommentMapper;
import com.usmind.mapper.OrgCollabMapper;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台：节点评论管理（列表 / 内容模糊查询 / 脑图下拉筛选 / 组织筛选 / 单个与批量删除）。
 */
@RestController
@RequestMapping("/api/admin/comments")
public class AdminCommentController {

    private final NodeCommentMapper commentMapper;
    private final MindMapMapper mindMapMapper;
    private final OrgCollabMapper orgCollabMapper;

    public AdminCommentController(NodeCommentMapper commentMapper, MindMapMapper mindMapMapper,
                                  OrgCollabMapper orgCollabMapper) {
        this.commentMapper = commentMapper;
        this.mindMapMapper = mindMapMapper;
        this.orgCollabMapper = orgCollabMapper;
    }

    @GetMapping
    public Result<Map<String, Object>> page(@RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "10") long size,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) String source,
                                            @RequestParam(required = false) Long mindMapId,
                                            @RequestParam(required = false) Long orgId) {
        LambdaQueryWrapper<NodeComment> qw = new LambdaQueryWrapper<NodeComment>()
                .orderByDesc(NodeComment::getCreatedAt);
        if (keyword != null && !keyword.isEmpty()) {
            qw.and(w -> w.like(NodeComment::getContent, keyword)
                    .or().like(NodeComment::getUserName, keyword));
        }
        if (source != null && !source.isEmpty()) {
            qw.eq(NodeComment::getSource, source);
        }
        if (mindMapId != null) {
            qw.eq(NodeComment::getMindMapId, mindMapId);
        }
        if (orgId != null) {
            // 组织筛选：该组织协作的所有导图下评论
            List<OrgCollab> collabs = orgCollabMapper.selectList(new LambdaQueryWrapper<OrgCollab>()
                    .eq(OrgCollab::getOrgId, orgId));
            List<Long> mapIds = new ArrayList<>();
            for (OrgCollab oc : collabs) {
                if (oc.getMindMapId() != null && !mapIds.contains(oc.getMindMapId())) mapIds.add(oc.getMindMapId());
            }
            if (mapIds.isEmpty()) {
                Map<String, Object> empty = new HashMap<>();
                empty.put("list", new ArrayList<>());
                empty.put("total", 0L);
                return Result.ok(empty);
            }
            qw.in(NodeComment::getMindMapId, mapIds);
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

    /** 批量删除：body { ids: [] } */
    @PostMapping("/batch-delete")
    public Result<Void> batchDelete(@RequestBody Map<String, Object> body) {
        List<Long> ids = new ArrayList<>();
        Object raw = body.get("ids");
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                try { ids.add(Long.valueOf(String.valueOf(o))); } catch (Exception ignored) {}
            }
        }
        if (!ids.isEmpty()) commentMapper.deleteBatchIds(ids);
        return Result.ok();
    }
}

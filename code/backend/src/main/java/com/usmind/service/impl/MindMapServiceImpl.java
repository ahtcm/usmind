package com.usmind.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.usmind.common.BusinessException;
import com.usmind.dto.MindMapSaveRequest;
import com.usmind.entity.MindMap;
import com.usmind.mapper.MindMapMapper;
import com.usmind.service.MindMapService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

@Service
public class MindMapServiceImpl implements MindMapService {

    private final MindMapMapper mindMapMapper;
    private final com.usmind.mapper.OrgMemberMapper orgMemberMapper;
    private final com.usmind.mapper.OrgCollabMapper orgCollabMapper;
    private final com.usmind.mapper.SysUserMapper sysUserMapper;
    private final com.usmind.service.SettingsService settingsService;
    private final com.usmind.service.OrgService orgService;
    private final com.usmind.ws.MindMapWsHandler mindMapWsHandler;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MindMapServiceImpl(MindMapMapper mindMapMapper,
                              com.usmind.mapper.OrgMemberMapper orgMemberMapper,
                              com.usmind.mapper.OrgCollabMapper orgCollabMapper,
                              com.usmind.mapper.SysUserMapper sysUserMapper,
                              com.usmind.service.SettingsService settingsService,
                              com.usmind.service.OrgService orgService,
                              com.usmind.ws.MindMapWsHandler mindMapWsHandler) {
        this.mindMapMapper = mindMapMapper;
        this.orgMemberMapper = orgMemberMapper;
        this.orgCollabMapper = orgCollabMapper;
        this.sysUserMapper = sysUserMapper;
        this.settingsService = settingsService;
        this.orgService = orgService;
        this.mindMapWsHandler = mindMapWsHandler;
    }

    /** 是否拥有该导图的读写权限：属主本人，或所属机构协作成员 */
    private boolean canAccess(long userId, MindMap m) {
        if (m.getUserId().equals(userId)) return true;
        return orgService.canAccessMindMap(userId, m.getId());
    }

    private boolean isVip(Long userId) {
        com.usmind.entity.SysUser u = sysUserMapper.selectById(userId);
        if (u == null || u.getMemberLevel() == null || u.getMemberLevel() <= 0) return false;
        if (u.getMemberExpire() == null) return false;
        return u.getMemberExpire().isAfter(java.time.LocalDateTime.now());
    }

    /** 导图数据统一序列化为 JSON 字符串存储 */
    private String toJson(Object data) {
        if (data == null) return null;
        if (data instanceof String s) return s;
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            return String.valueOf(data);
        }
    }

    @Override
    public IPage<MindMap> page(long userId, long page, long size, String keyword) {
        Page<MindMap> p = new Page<>(page, size);
        LambdaQueryWrapper<MindMap> qw = new LambdaQueryWrapper<>();
        qw.eq(MindMap::getUserId, userId);
        if (StringUtils.hasText(keyword)) {
            qw.like(MindMap::getName, keyword);
        }
        qw.orderByDesc(MindMap::getUpdatedAt);
        // 列表不返回大字段 data
        Page<MindMap> result = mindMapMapper.selectPage(p, qw.select(MindMap.class, f ->
                !f.getColumn().equals("data")));
        // 合并本人参与的机构协作导图（排除已属自己的），标记 isCollab
        java.util.List<Long> collabIds = new java.util.ArrayList<>();
        try {
            java.util.List<com.usmind.entity.OrgCollab> cs = orgCollabOfUser(userId);
            for (com.usmind.entity.OrgCollab c : cs) {
                com.usmind.entity.MindMap m = mindMapMapper.selectById(c.getMindMapId());
                if (m != null && !m.getUserId().equals(userId)) collabIds.add(m.getId());
            }
        } catch (Exception e) { /* 机构服务异常不影响个人列表 */ }
        if (!collabIds.isEmpty()) {
            java.util.List<MindMap> collabs = mindMapMapper.selectList(new LambdaQueryWrapper<MindMap>()
                    .in(MindMap::getId, collabIds).select(MindMap.class, f -> !f.getColumn().equals("data")));
            // 空分页时 records 可能是不可变空列表，先包装为可变列表再合并
            java.util.List<MindMap> records = new java.util.ArrayList<>(result.getRecords());
            java.util.Set<Long> ownIds = new java.util.HashSet<>();
            for (MindMap m : records) ownIds.add(m.getId());
            for (MindMap m : collabs) {
                if (ownIds.contains(m.getId())) continue;
                m.setIsCollab(true);
                records.add(m);
                result.setTotal(result.getTotal() + 1);
            }
            result.setRecords(records);
        }
        return result;
    }

    private java.util.List<com.usmind.entity.OrgCollab> orgCollabOfUser(long userId) {
        // 用户所在机构的全部协作导图
        java.util.List<com.usmind.entity.OrgMember> ms = orgMemberMapper.selectList(
                new LambdaQueryWrapper<com.usmind.entity.OrgMember>().eq(com.usmind.entity.OrgMember::getUserId, userId));
        if (ms.isEmpty()) return java.util.Collections.emptyList();
        java.util.List<Long> orgIds = new java.util.ArrayList<>();
        for (com.usmind.entity.OrgMember m : ms) orgIds.add(m.getOrgId());
        return orgCollabMapper.selectList(new LambdaQueryWrapper<com.usmind.entity.OrgCollab>()
                .in(com.usmind.entity.OrgCollab::getOrgId, orgIds));
    }

    @Override
    public Long create(long userId, MindMapSaveRequest req) {
        // 画布个数限制：非会员 mindmapFreeCount / 会员 mindmapVipCount
        boolean vip = isVip(userId);
        int maxCount = settingsService.getInt(vip ? "mindmapVipCount" : "mindmapFreeCount", 200);
        Long count = mindMapMapper.selectCount(new LambdaQueryWrapper<MindMap>().eq(MindMap::getUserId, userId));
        if (count != null && count >= maxCount) {
            throw new BusinessException(400, "画布数量已达上限(" + maxCount + "个)" + (vip ? "" : "，升级会员可提升上限"));
        }
        MindMap m = new MindMap();
        m.setUserId(userId);
        m.setName(StringUtils.hasText(req.getName()) ? req.getName() : "未命名");
        m.setData(toJson(req.getData()));
        m.setLayout(StringUtils.hasText(req.getLayout()) ? req.getLayout() : "logicalStructure");
        m.setTheme(StringUtils.hasText(req.getTheme()) ? req.getTheme() : "晨曦白");
        m.setViewData(toJson(req.getViewData()));
        m.setManualStyles(req.getManualStyles() != null ? toJson(req.getManualStyles()) : null);
        m.setIsShared(0);
        // 协作配置默认：全局开关（后台配置 ownerLockDefault）
        String ownerLockDefault = settingsService.get("ownerLockDefault", "0");
        boolean lockOn = "1".equals(ownerLockDefault);
        m.setCollabConfig("{\"enableOwnerLock\":" + (lockOn ? "true" : "false")
                + ",\"approveTimeout1\":120,\"approveTimeout2\":180,\"grantMinutes\":10}");
        mindMapMapper.insert(m);
        return m.getId();
    }

    @Override
    public void updateCollabConfig(long userId, Long id, java.util.Map<String, Object> cfg) {
        MindMap m = mindMapMapper.selectById(id);
        if (m == null || !canAccess(userId, m)) {
            throw new BusinessException(404, "导图不存在");
        }
        if (cfg == null || cfg.isEmpty()) return;
        String cur = m.getCollabConfig();
        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
        java.util.Map<String, Object> merged;
        try {
            merged = new java.util.HashMap<>();
            if (cur != null && !cur.isBlank()) {
                merged.putAll(om.readValue(cur, java.util.Map.class));
            }
        } catch (Exception e) {
            merged = new java.util.HashMap<>();
        }
        if (cfg.containsKey("enableOwnerLock")) merged.put("enableOwnerLock", cfg.get("enableOwnerLock"));
        if (cfg.containsKey("approveTimeout1")) merged.put("approveTimeout1", cfg.get("approveTimeout1"));
        if (cfg.containsKey("approveTimeout2")) merged.put("approveTimeout2", cfg.get("approveTimeout2"));
        if (cfg.containsKey("grantMinutes")) merged.put("grantMinutes", cfg.get("grantMinutes"));
        try {
            m.setCollabConfig(om.writeValueAsString(merged));
        } catch (Exception e) {
            throw new BusinessException(500, "协作配置格式错误");
        }
        mindMapMapper.updateById(m);
    }

    @Override
    public MindMap get(long userId, Long id) {
        MindMap m = mindMapMapper.selectById(id);
        if (m == null || !canAccess(userId, m)) {
            throw new BusinessException(404, "导图不存在");
        }
        fillCreatorUid(m);
        return m;
    }

    /** 历史节点所有权兜底：无 creatorUid 的节点归导图创建者所有（仅内存返回，保存时自然持久化） */
    private void fillCreatorUid(MindMap m) {
        try {
            String data = m.getData();
            if (data == null || data.isBlank()) return;
            long owner = m.getUserId() == null ? 0 : m.getUserId();
            if (owner <= 0) return;
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = om.readTree(data);
            if (root == null) return;
            // 数据包装：{ root:{data,children}, _tabs, _activeTab }，从 root 字段进入
            com.fasterxml.jackson.databind.JsonNode treeRoot = root.path("root");
            if (treeRoot.isMissingNode() || treeRoot.isNull()) treeRoot = root;
            boolean changed = fillCreatorRec(treeRoot, owner);
            if (changed) m.setData(om.writeValueAsString(root));
        } catch (Exception ignored) {
        }
    }

    private boolean fillCreatorRec(com.fasterxml.jackson.databind.JsonNode n, long owner) {
        boolean changed = false;
        if (n.isObject()) {
            // 节点数据在 data 层（simple-mind-map 结构：{ data:{text,uid,...}, children:[...] }）
            com.fasterxml.jackson.databind.JsonNode dataNode = n.get("data");
            if (dataNode != null && dataNode.isObject() && !dataNode.has("creatorUid")) {
                ((com.fasterxml.jackson.databind.node.ObjectNode) dataNode).put("creatorUid", owner);
                changed = true;
            }
            com.fasterxml.jackson.databind.JsonNode children = n.get("children");
            if (children != null && children.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode c : children) {
                    if (fillCreatorRec(c, owner)) changed = true;
                }
            }
        }
        return changed;
    }

    @Override
    public int update(long userId, Long id, MindMapSaveRequest req) {
        MindMap m = mindMapMapper.selectById(id);
        if (m == null || !canAccess(userId, m)) {
            throw new BusinessException(404, "导图不存在");
        }
        int cur = m.getVersion() == null ? 0 : m.getVersion();
        boolean force = req.getForce() != null && req.getForce();
        // 并发合并条件：版本不一致，或显式携带冲突解决方式（409 后重提时 baseVersion 可能已等于最新版本，仍需按 resolve 合并而非整树覆盖）
        boolean resolveFlag = req.getResolveConflicts() != null && !req.getResolveConflicts().isBlank();
        boolean concurrent = resolveFlag || (req.getBaseVersion() != null && req.getBaseVersion() != cur);
        // force（"以我为准覆盖"）也走 uid 并集合并：保留对方新增节点不丢数据，仅同节点文本以客户端为准
        Object finalData = req.getData();
        if (concurrent || force) {
            // 并发保存：按 uid 并集合并——本地新增/对方新增都保留（不覆盖未冲突节点）；
            // 同节点内容不同 → 冲突：resolveConflicts=mine 用客户端、theirs 用服务端、否则抛 409 返回冲突列表由前端选择
            JsonNode serverRoot = rootOf(m.getData());
            JsonNode clientRoot = rootOf(req.getData());
            List<Map<String, Object>> conflicts = new java.util.ArrayList<>();
            if (serverRoot != null && clientRoot != null) {
                String resolve = force ? "mine" : req.getResolveConflicts();
                JsonNode merged = mergeTrees(serverRoot, clientRoot,
                        req.getDeletedUids(), resolve, conflicts);
                if (!conflicts.isEmpty() && !force) {
                    throw new com.usmind.common.ConflictException(
                            "导图已被其他成员修改，以下节点存在内容冲突，请选择以谁的为准", conflicts, cur);
                }
                finalData = buildMergedData(req.getData(), merged);
            }
        }
        // 无论并发与否，删除权限都必须校验（防止过期版本删除他人节点/根节点导致数据被破坏）
        validateDeletes(userId, m, req.getDeletedUids());
        if (req.getName() != null) m.setName(req.getName());
        if (finalData != null) m.setData(toJson(finalData));
        if (req.getLayout() != null) m.setLayout(req.getLayout());
        if (req.getTheme() != null) m.setTheme(req.getTheme());
        if (req.getViewData() != null) m.setViewData(toJson(req.getViewData()));
        if (req.getManualStyles() != null) m.setManualStyles(toJson(req.getManualStyles()));
        // 乐观锁：m.version 保持旧版本用于 WHERE 条件，DB 侧 version=version+1 自增
        int rows = mindMapMapper.updateByIdAndVersion(m);
        if (rows == 0) {
            // 乐观锁失败：版本已被他人推进（并发竞态）——重读最新版本、重新做并集合并，再尝试一次；
            // 仍失败则抛冲突由前端选择以谁的为准
            MindMap fresh = mindMapMapper.selectById(id);
            if (fresh == null) throw new BusinessException(404, "导图不存在");
            int cur2 = fresh.getVersion() == null ? 0 : fresh.getVersion();
            JsonNode serverRoot2 = rootOf(fresh.getData());
            JsonNode clientRoot2 = rootOf(req.getData());
            List<Map<String, Object>> conflicts2 = new java.util.ArrayList<>();
            String resolve2 = force ? "mine" : req.getResolveConflicts();
            Object finalData2 = req.getData();
            if (serverRoot2 != null && clientRoot2 != null) {
                JsonNode merged2 = mergeTrees(serverRoot2, clientRoot2,
                        req.getDeletedUids(), resolve2, conflicts2);
                if (!conflicts2.isEmpty() && !force) {
                    throw new com.usmind.common.ConflictException(
                            "导图已被其他成员修改，以下节点存在内容冲突，请选择以谁的为准", conflicts2, cur2);
                }
                finalData2 = buildMergedData(req.getData(), merged2);
            }
            validateDeletes(userId, fresh, req.getDeletedUids());
            if (req.getName() != null) fresh.setName(req.getName());
            if (finalData2 != null) fresh.setData(toJson(finalData2));
            if (req.getLayout() != null) fresh.setLayout(req.getLayout());
            if (req.getTheme() != null) fresh.setTheme(req.getTheme());
            if (req.getViewData() != null) fresh.setViewData(toJson(req.getViewData()));
            if (req.getManualStyles() != null) fresh.setManualStyles(toJson(req.getManualStyles()));
            rows = mindMapMapper.updateByIdAndVersion(fresh);
            if (rows == 0) {
                throw new com.usmind.common.ConflictException(
                        "并发保存繁忙，请稍后重试", new java.util.ArrayList<>(), cur2);
            }
            m = fresh;
            m.setVersion(cur2 + 1);
        } else {
            m.setVersion(cur + 1);
        }
        // 协作实时同步：广播变更通知（前端据此自动加载最新 / 弹更新提示）
        try {
            String updater = "";
            com.usmind.entity.SysUser u = sysUserMapper.selectById(userId);
            if (u != null && u.getNickname() != null) updater = u.getNickname();
            Map<String, Object> msg = new HashMap<>();
            msg.put("event", "mindmap_updated");
            msg.put("mindMapId", id);
            msg.put("version", m.getVersion());
            msg.put("updatedBy", userId);
            msg.put("updatedByName", updater);
            msg.put("ts", System.currentTimeMillis());
            mindMapWsHandler.broadcast(String.valueOf(id), objectMapper.writeValueAsString(msg));
        } catch (Exception e) {
            // 广播失败不影响保存
        }
        return m.getVersion();
    }

    // ==================== 保存合并（git 式 diff 合并） ====================

    /** 取导图数据的 root 节点（兼容 String / Object / 已有 root 包装） */
    private JsonNode rootOf(Object dataOrJson) {
        try {
            JsonNode n;
            if (dataOrJson instanceof String) {
                String raw = (String) dataOrJson;
                if (raw == null || raw.isBlank()) return null;
                n = objectMapper.readTree(raw);
            } else if (dataOrJson instanceof JsonNode) {
                n = (JsonNode) dataOrJson;
            } else if (dataOrJson != null) {
                n = objectMapper.valueToTree(dataOrJson);
            } else {
                return null;
            }
            if (n == null) return null;
            if (n.has("root")) return n.get("root");
            return n;
        } catch (Exception e) {
            return null;
        }
    }

    private String uidOf(JsonNode n) {
        if (n == null) return "";
        JsonNode d = n.path("data");
        if (d.isObject() && d.has("uid")) return d.path("uid").asText();
        return n.path("uid").asText("");
    }

    private String textOf(JsonNode n) {
        if (n == null) return "";
        JsonNode d = n.path("data");
        if (d.isObject() && d.has("text")) return d.path("text").asText("");
        return n.path("text").asText("");
    }

    private long creatorOf(JsonNode n) {
        if (n == null) return 0;
        JsonNode d = n.path("data");
        if (d.isObject() && d.has("creatorUid")) return d.path("creatorUid").asLong(0);
        return n.path("creatorUid").asLong(0);
    }

    private void setTextOf(JsonNode node, String text) {
        try {
            JsonNode d = node.get("data");
            if (d != null && d.isObject()) ((com.fasterxml.jackson.databind.node.ObjectNode) d).put("text", text == null ? "" : text);
            else ((com.fasterxml.jackson.databind.node.ObjectNode) node).put("text", text == null ? "" : text);
        } catch (Exception ignored) {
        }
    }

    /** 深度合并：以客户端树为骨架，对照服务端树做冲突检测 */
    private JsonNode mergeNodeRec(JsonNode clientNode, JsonNode serverNode,
                                  Set<String> deleted, String resolve,
                                  List<Map<String, Object>> conflicts) {
        if (clientNode == null) return null;
        com.fasterxml.jackson.databind.node.ObjectNode out;
        try {
            out = (com.fasterxml.jackson.databind.node.ObjectNode) clientNode.deepCopy();
        } catch (Exception e) {
            return clientNode;
        }
        String uid = uidOf(out);
        if (serverNode != null && !deleted.contains(uid)) {
            String ct = textOf(clientNode);
            String st = textOf(serverNode);
            boolean same = (ct == null ? "" : ct).equals(st == null ? "" : st);
            if (!same) {
                if ("mine".equals(resolve)) {
                    // 保留客户端的（out 已是客户端内容）
                } else if ("theirs".equals(resolve)) {
                    setTextOf(out, st);
                } else {
                    Map<String, Object> c = new HashMap<>();
                    c.put("uid", uid);
                    c.put("myText", ct == null ? "" : ct);
                    c.put("remoteText", st == null ? "" : st);
                    String name = (ct == null || ct.isBlank()) ? uid : (ct.length() > 40 ? ct.substring(0, 40) : ct);
                    c.put("name", name);
                    conflicts.add(c);
                }
            }
        }
        JsonNode childrenNode = out.get("children");
        if (childrenNode != null && childrenNode.isArray()) {
            JsonNode serverChildren = serverNode == null ? null : serverNode.get("children");
            Map<String, JsonNode> serverChildMap = new HashMap<>();
            if (serverChildren != null && serverChildren.isArray()) {
                for (JsonNode sc : serverChildren) serverChildMap.put(uidOf(sc), sc);
            }
            com.fasterxml.jackson.databind.node.ArrayNode newArr = objectMapper.createArrayNode();
            for (JsonNode cc : childrenNode) {
                JsonNode sc = serverChildMap.get(uidOf(cc));
                newArr.add(mergeNodeRec(cc, sc, deleted, resolve, conflicts));
            }
            out.set("children", newArr);
        }
        return out;
    }

    /** 收集树节点 uid 索引（node 本身） */
    private void collectNodes(JsonNode node, Map<String, JsonNode> map) {
        if (node == null) return;
        String uid = uidOf(node);
        if (!uid.isEmpty()) map.put(uid, node);
        JsonNode cs = node.get("children");
        if (cs != null && cs.isArray()) for (JsonNode c : cs) collectNodes(c, map);
    }

    /** 附加服务端有、客户端没有 且未被删除 的节点（对方新增），挂到对应父节点下 */
    private void attachServerOnly(JsonNode serverNode, String serverParentUid,
                                  Map<String, JsonNode> mergedMap, Set<String> deleted) {
        if (serverNode == null) return;
        String uid = uidOf(serverNode);
        if (!uid.isEmpty() && deleted.contains(uid)) return; // 我方已删除的子树整体跳过
        if (!uid.isEmpty() && !mergedMap.containsKey(uid)) {
            JsonNode parentNode = serverParentUid == null ? null : mergedMap.get(serverParentUid);
            if (parentNode != null) {
                JsonNode pc = parentNode.get("children");
                if (pc == null || !pc.isArray()) {
                    try { ((com.fasterxml.jackson.databind.node.ObjectNode) parentNode).set("children", objectMapper.createArrayNode()); } catch (Exception ignored) {}
                }
                try {
                    com.fasterxml.jackson.databind.node.ObjectNode copy = (com.fasterxml.jackson.databind.node.ObjectNode) serverNode.deepCopy();
                    ((com.fasterxml.jackson.databind.node.ArrayNode) parentNode.get("children")).add(copy);
                    mergedMap.put(uid, copy);
                } catch (Exception ignored) {}
            }
        }
        JsonNode cs = serverNode.get("children");
        if (cs != null && cs.isArray()) for (JsonNode c : cs) attachServerOnly(c, uid, mergedMap, deleted);
    }

    /** 并发合并入口 */
    private JsonNode mergeTrees(JsonNode serverRoot, JsonNode clientRoot,
                                java.util.List<String> deletedUids, String resolve,
                                List<Map<String, Object>> conflicts) {
        Set<String> deleted = deletedUids == null ? new HashSet<>() : new HashSet<>(deletedUids);
        // 服务端 uid 索引（冲突对照）
        Map<String, JsonNode> serverMap = new HashMap<>();
        collectNodes(serverRoot, serverMap);
        JsonNode merged = mergeNodeRec(clientRoot, serverRoot, deleted, resolve, conflicts);
        // 合并结果索引 + 附加对方新增节点
        Map<String, JsonNode> mergedMap = new HashMap<>();
        collectNodes(merged, mergedMap);
        attachServerOnly(serverRoot, null, mergedMap, deleted);
        return merged;
    }

    /** 以合并后的 root 重建导图 data（保留客户端 _tabs/_activeTab） */
    private Object buildMergedData(Object clientData, JsonNode mergedRoot) {
        try {
            JsonNode n;
            if (clientData instanceof String) n = objectMapper.readTree((String) clientData);
            else n = objectMapper.valueToTree(clientData);
            com.fasterxml.jackson.databind.node.ObjectNode out = objectMapper.createObjectNode();
            out.set("root", mergedRoot);
            if (n != null && n.has("_tabs")) out.set("_tabs", n.get("_tabs"));
            if (n != null && n.has("_activeTab")) out.set("_activeTab", n.get("_activeTab"));
            return out;
        } catch (Exception e) {
            return clientData;
        }
    }

    /** 删除权限校验：被删节点须为本人创建，或其双亲节点由本人创建；根节点（无父）仅导图创建者可删 */
    private void validateDeletes(long userId, MindMap m, java.util.List<String> deletedUids) {
        if (deletedUids == null || deletedUids.isEmpty()) return;
        JsonNode serverRoot = rootOf(m.getData());
        if (serverRoot == null) return;
        Map<String, JsonNode> nodeMap = new HashMap<>();
        Map<String, String> parentMap = new HashMap<>();
        collectWithParent(serverRoot, null, nodeMap, parentMap);
        long owner = m.getUserId() == null ? 0 : m.getUserId();
        for (String uid : deletedUids) {
            if (uid == null || uid.isBlank()) continue;
            JsonNode n = nodeMap.get(uid);
            if (n == null) continue; // 服务端无此节点（可能已被删）：放行
            long nodeCreator = creatorOf(n);
            String pUid = parentMap.get(uid);
            long parentCreator = 0;
            boolean isRoot = pUid == null || pUid.isEmpty();
            if (!isRoot) {
                JsonNode pn = nodeMap.get(pUid);
                if (pn != null) parentCreator = creatorOf(pn);
            }
            boolean ok = userId == nodeCreator || userId == parentCreator || (isRoot && userId == owner);
            if (!ok) {
                String t = textOf(n);
                if (t == null || t.isBlank()) t = uid;
                throw new BusinessException(403, "无权限删除节点：" + (t.length() > 30 ? t.substring(0, 30) : t)
                        + "（仅该节点创建者或其双亲节点创建者可删除）");
            }
        }
    }

    private void collectWithParent(JsonNode node, String parentUid,
                                   Map<String, JsonNode> nodeMap, Map<String, String> parentMap) {
        if (node == null) return;
        String uid = uidOf(node);
        if (!uid.isEmpty()) {
            nodeMap.put(uid, node);
            if (parentUid != null) parentMap.put(uid, parentUid);
        }
        JsonNode cs = node.get("children");
        if (cs != null && cs.isArray()) for (JsonNode c : cs) collectWithParent(c, uid.isEmpty() ? parentUid : uid, nodeMap, parentMap);
    }

    @Override
    public void delete(long userId, Long id) {
        MindMap m = mindMapMapper.selectById(id);
        if (m == null || !m.getUserId().equals(userId)) {
            throw new BusinessException(404, "导图不存在");
        }
        mindMapMapper.deleteById(id);
        // 删除协作关系
        orgCollabMapper.delete(new LambdaQueryWrapper<com.usmind.entity.OrgCollab>()
                .eq(com.usmind.entity.OrgCollab::getMindMapId, id));
    }

    @Override
    public void deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        for (Long id : ids) {
            MindMap m = mindMapMapper.selectById(id);
            if (m != null) mindMapMapper.deleteById(id);
        }
    }

    @Override
    public Map<String, Object> share(long userId, Long id, String password) {
        MindMap m = mindMapMapper.selectById(id);
        if (m == null || !m.getUserId().equals(userId)) {
            throw new BusinessException(404, "导图不存在");
        }
        boolean alreadyShared = StringUtils.hasText(m.getShareToken());
        if (!alreadyShared) {
            m.setShareToken(UUID.randomUUID().toString().replace("-", ""));
        }
        m.setIsShared(1);
        // password==null：复用分享且保持原密码；password==""：清除密码；否则设置新密码
        if (password == null) {
            if (!alreadyShared) {
                m.setSharePassword("");
                m.setSharePasswordPlain("");
            }
        } else if (password.isEmpty()) {
            m.setSharePassword("");
            m.setSharePasswordPlain("");
        } else {
            // 分享密码加盐哈希存储（BCrypt），明文仅用于作者端回显
            m.setSharePassword(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(password));
            m.setSharePasswordPlain(password);
        }
        mindMapMapper.updateById(m);
        Map<String, Object> data = new HashMap<>();
        data.put("shareToken", m.getShareToken());
        data.put("shareUrl", "/share/" + m.getShareToken());
        data.put("hasPassword", StringUtils.hasText(m.getSharePasswordPlain()) || StringUtils.hasText(m.getSharePassword()));
        data.put("sharePassword", m.getSharePasswordPlain() == null ? "" : m.getSharePasswordPlain());
        return data;
    }

    @Override
    public Map<String, Object> shareInfo(long userId, Long id) {
        MindMap m = mindMapMapper.selectById(id);
        if (m == null || !m.getUserId().equals(userId)) {
            throw new BusinessException(404, "导图不存在");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("isShared", Integer.valueOf(1).equals(m.getIsShared()) && StringUtils.hasText(m.getShareToken()));
        data.put("shareToken", m.getShareToken() == null ? "" : m.getShareToken());
        data.put("shareUrl", StringUtils.hasText(m.getShareToken()) ? "/share/" + m.getShareToken() : "");
        data.put("hasPassword", StringUtils.hasText(m.getSharePasswordPlain()) || StringUtils.hasText(m.getSharePassword()));
        data.put("sharePassword", m.getSharePasswordPlain() == null ? "" : m.getSharePasswordPlain());
        return data;
    }

    @Override
    public void unshare(long userId, Long id) {
        MindMap m = mindMapMapper.selectById(id);
        if (m == null || !m.getUserId().equals(userId)) {
            throw new BusinessException(404, "导图不存在");
        }
        m.setIsShared(0);
        m.setSharePassword("");
        mindMapMapper.updateById(m);
    }

    @Override
    public MindMap getByShareToken(String token, String password) {
        MindMap m = mindMapMapper.selectOne(new LambdaQueryWrapper<MindMap>()
                .eq(MindMap::getShareToken, token)
                .eq(MindMap::getIsShared, 1)
                .last("LIMIT 1"));
        if (m == null) throw new BusinessException(404, "分享不存在或已取消");
        String stored = m.getSharePassword();
        if (StringUtils.hasText(stored)) {
            boolean ok;
            if (stored.startsWith("$2")) {
                // 新格式：BCrypt 哈希校验
                ok = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
                        .matches(password == null ? "" : password, stored);
            } else {
                // 兼容存量明文数据：命中后自动升级为哈希
                ok = stored.equals(password);
                if (ok) {
                    m.setSharePassword(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
                            .encode(password == null ? "" : password));
                    mindMapMapper.updateById(m);
                }
            }
            if (!ok) {
                throw new BusinessException(403, "需要分享密码");
            }
        }
        m.setViewCount(m.getViewCount() == null ? 1 : m.getViewCount() + 1);
        mindMapMapper.updateById(m);
        return m;
    }
}

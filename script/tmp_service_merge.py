# -*- coding: utf-8 -*-
import io
p = r'C:\Users\25750\Desktop\usmind\code\backend\src\main\java\com\usmind\service\impl\MindMapServiceImpl.java'
s = io.open(p, encoding='utf-8').read()

old = '''    @Override
    public int update(long userId, Long id, MindMapSaveRequest req) {
        MindMap m = mindMapMapper.selectById(id);
        if (m == null || !canAccess(userId, m)) {
            throw new BusinessException(404, "导图不存在");
        }
        // 协作冲突检测（乐观锁）：前端传入 baseVersion，与当前版本不一致且未强制覆盖时返回 409
        int cur = m.getVersion() == null ? 0 : m.getVersion();
        boolean force = req.getForce() != null && req.getForce();
        if (!force && req.getBaseVersion() != null && req.getBaseVersion() != cur) {
            throw new BusinessException(409, "导图已被其他成员修改，请选择「加载最新」或「以我的为准覆盖」");
        }
        if (req.getName() != null) m.setName(req.getName());
        if (req.getData() != null) m.setData(toJson(req.getData()));
        if (req.getLayout() != null) m.setLayout(req.getLayout());
        if (req.getTheme() != null) m.setTheme(req.getTheme());
        if (req.getViewData() != null) m.setViewData(toJson(req.getViewData()));
        if (req.getManualStyles() != null) m.setManualStyles(toJson(req.getManualStyles()));
        m.setVersion(cur + 1);
        mindMapMapper.updateById(m);
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
    }'''

new = '''    @Override
    public int update(long userId, Long id, MindMapSaveRequest req) {
        MindMap m = mindMapMapper.selectById(id);
        if (m == null || !canAccess(userId, m)) {
            throw new BusinessException(404, "导图不存在");
        }
        int cur = m.getVersion() == null ? 0 : m.getVersion();
        boolean force = req.getForce() != null && req.getForce();
        boolean concurrent = !force && req.getBaseVersion() != null && req.getBaseVersion() != cur;
        Object finalData = req.getData();
        if (concurrent) {
            // 并发保存：按 uid 并集合并——本地新增/对方新增都保留（不覆盖未冲突节点）；
            // 同节点内容不同 → 冲突：resolveConflicts=mine 用客户端、theirs 用服务端、否则抛 409 返回冲突列表由前端选择
            JsonNode serverRoot = rootOf(m.getData());
            JsonNode clientRoot = rootOf(req.getData());
            List<Map<String, Object>> conflicts = new java.util.ArrayList<>();
            if (serverRoot != null && clientRoot != null) {
                JsonNode merged = mergeTrees(serverRoot, clientRoot,
                        req.getDeletedUids(), req.getResolveConflicts(), conflicts);
                if (!conflicts.isEmpty()) {
                    throw new com.usmind.common.ConflictException(
                            "导图已被其他成员修改，以下节点存在内容冲突，请选择以谁的为准", conflicts, cur);
                }
                finalData = buildMergedData(req.getData(), merged);
            }
        } else {
            // 非并发/强制覆盖：校验删除权限（被删节点须为本人创建，或其双亲节点由本人创建；根节点仅导图创建者可删）
            validateDeletes(userId, m, req.getDeletedUids());
        }
        if (req.getName() != null) m.setName(req.getName());
        if (finalData != null) m.setData(toJson(finalData));
        if (req.getLayout() != null) m.setLayout(req.getLayout());
        if (req.getTheme() != null) m.setTheme(req.getTheme());
        if (req.getViewData() != null) m.setViewData(toJson(req.getViewData()));
        if (req.getManualStyles() != null) m.setManualStyles(toJson(req.getManualStyles()));
        m.setVersion(cur + 1);
        mindMapMapper.updateById(m);
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
    }'''

assert s.count(old) == 1, 'update=' + str(s.count(old))
s = s.replace(old, new)

# 确保 import 了 JsonNode/Set/HashSet/List/HashMap
if 'import com.fasterxml.jackson.databind.JsonNode;' not in s:
    s = s.replace('import com.fasterxml.jackson.databind.ObjectMapper;',
                  'import com.fasterxml.jackson.databind.JsonNode;\nimport com.fasterxml.jackson.databind.ObjectMapper;')
if 'import java.util.Set;' not in s:
    s = s.replace('import java.util.List;', 'import java.util.List;\nimport java.util.Set;\nimport java.util.HashSet;')

io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('SERVICE MERGE OK')

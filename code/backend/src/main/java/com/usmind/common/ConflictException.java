package com.usmind.common;

import lombok.Getter;
import java.util.List;
import java.util.Map;

/** 保存并发冲突：附带冲突节点列表与服务端当前版本，前端据此弹出 diff 对比选择 */
@Getter
public class ConflictException extends BusinessException {
    private final List<Map<String, Object>> conflicts;
    private final int serverVersion;

    public ConflictException(String message, List<Map<String, Object>> conflicts, int serverVersion) {
        super(409, message);
        this.conflicts = conflicts;
        this.serverVersion = serverVersion;
    }
}

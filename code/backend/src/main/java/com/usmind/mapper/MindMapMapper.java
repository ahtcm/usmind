package com.usmind.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.usmind.entity.MindMap;

public interface MindMapMapper extends BaseMapper<MindMap> {
    /** 乐观锁保存：仅当数据库版本仍为 #{version} 时更新并 version+1（防止并发保存互相覆盖） */
    @org.apache.ibatis.annotations.Update("UPDATE mind_map SET name=#{name}, data=#{data}, layout=#{layout}, theme=#{theme}, view_data=#{viewData}, manual_styles=#{manualStyles}, collab_config=#{collabConfig}, version=version+1, updated_at=NOW() WHERE id=#{id} AND version=#{version}")
    int updateByIdAndVersion(MindMap m);
}

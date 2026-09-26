package com.usmind.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.usmind.entity.FileResource;
import org.apache.ibatis.annotations.Select;

public interface FileResourceMapper extends BaseMapper<FileResource> {

    @Select("SELECT COALESCE(SUM(size),0) FROM file_resource")
    Long sumSize();
}

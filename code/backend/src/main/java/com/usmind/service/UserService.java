package com.usmind.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.usmind.dto.UserCreateRequest;
import com.usmind.dto.UserUpdateRequest;
import com.usmind.entity.SysUser;

public interface UserService {
    IPage<SysUser> page(long page, long size, String keyword);
    Long create(UserCreateRequest req);
    void update(Long id, UserUpdateRequest req);
    void delete(Long id);
    void resetPassword(Long id, String password);
}

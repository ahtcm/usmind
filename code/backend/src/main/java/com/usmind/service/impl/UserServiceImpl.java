package com.usmind.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.usmind.common.BusinessException;
import com.usmind.dto.UserCreateRequest;
import com.usmind.dto.UserUpdateRequest;
import com.usmind.entity.SysUser;
import com.usmind.mapper.SysUserMapper;
import com.usmind.service.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class UserServiceImpl implements UserService {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(SysUserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public IPage<SysUser> page(long page, long size, String keyword) {
        Page<SysUser> p = new Page<>(page, size);
        LambdaQueryWrapper<SysUser> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(SysUser::getUsername, keyword)
                    .or().like(SysUser::getNickname, keyword));
        }
        qw.orderByDesc(SysUser::getId);
        Page<SysUser> result = userMapper.selectPage(p, qw);
        result.getRecords().forEach(u -> u.setPassword(null));
        return result;
    }

    @Override
    public Long create(UserCreateRequest req) {
        if (!StringUtils.hasText(req.getUsername()) || !StringUtils.hasText(req.getPassword())) {
            throw new BusinessException(400, "用户名和密码不能为空");
        }
        SysUser exist = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, req.getUsername()));
        if (exist != null) throw new BusinessException(400, "用户名已存在");
        SysUser u = new SysUser();
        u.setUsername(req.getUsername());
        u.setPassword(passwordEncoder.encode(req.getPassword()));
        u.setNickname(StringUtils.hasText(req.getNickname()) ? req.getNickname() : req.getUsername());
        u.setEmail(req.getEmail() == null ? "" : req.getEmail());
        u.setPhone(req.getPhone() == null ? "" : req.getPhone());
        u.setRole(StringUtils.hasText(req.getRole()) ? req.getRole() : "USER");
        u.setStatus(1);
        userMapper.insert(u);
        return u.getId();
    }

    @Override
    public void update(Long id, UserUpdateRequest req) {
        SysUser u = userMapper.selectById(id);
        if (u == null) throw new BusinessException(404, "用户不存在");
        if (req.getNickname() != null) u.setNickname(req.getNickname());
        if (req.getEmail() != null) u.setEmail(req.getEmail());
        if (req.getPhone() != null) u.setPhone(req.getPhone());
        if (req.getRole() != null) u.setRole(req.getRole());
        if (req.getStatus() != null) u.setStatus(req.getStatus());
        userMapper.updateById(u);
    }

    @Override
    public void delete(Long id) {
        SysUser u = userMapper.selectById(id);
        if (u == null) throw new BusinessException(404, "用户不存在");
        if ("ADMIN".equals(u.getRole())) {
            Long adminCount = userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getRole, "ADMIN"));
            if (adminCount == null || adminCount <= 1) throw new BusinessException(400, "至少保留一个管理员账号");
        }
        userMapper.deleteById(id);
    }

    @Override
    public void deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        for (Long id : ids) {
            delete(id);
        }
    }

    @Override
    public void resetPassword(Long id, String password) {
        SysUser u = userMapper.selectById(id);
        if (u == null) throw new BusinessException(404, "用户不存在");
        if (!StringUtils.hasText(password)) throw new BusinessException(400, "密码不能为空");
        u.setPassword(passwordEncoder.encode(password));
        userMapper.updateById(u);
    }

    @Override
    public void grantMember(Long id, int level, int days) {
        SysUser u = userMapper.selectById(id);
        if (u == null) return;
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime base = u.getMemberExpire() != null && u.getMemberExpire().isAfter(now)
                ? u.getMemberExpire() : now;
        u.setMemberLevel(level);
        u.setMemberExpire(base.plusDays(days));
        userMapper.updateById(u);
    }

    @Override
    public void cancelMember(Long id) {
        SysUser u = userMapper.selectById(id);
        if (u == null) return;
        u.setMemberLevel(0);
        u.setMemberExpire(null);
        userMapper.updateById(u);
    }
}

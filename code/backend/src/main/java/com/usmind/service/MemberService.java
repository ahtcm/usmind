package com.usmind.service;

import java.util.List;
import java.util.Map;

public interface MemberService {
    Map<String, Object> memberInfo(Long userId);

    boolean isVip(Long userId);

    List<Map<String, Object>> plans();

    Map<String, Object> featureConfig();

    /** 开通会员（支付回调或后台手动开通调用） */
    void grant(Long userId, Integer level, int days);

    void cancel(Long userId);

    /** 兑换码兑换会员：校验码并开通，成功返回用户会员信息 */
    Map<String, Object> redeem(Long userId, String code);
}

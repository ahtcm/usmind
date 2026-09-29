package com.usmind.service;

import java.util.List;
import java.util.Map;

public interface OrgService {

    /** 创建机构（owner 自动成为成员；是否需会员由后台 orgCreateNeedMember 控制） */
    Map<String, Object> create(long userId, String name);

    /** 我的机构列表 */
    List<Map<String, Object>> myOrgs(long userId);

    /** 机构详情（成员 + 协作导图），仅成员可看 */
    Map<String, Object> detail(long userId, Long orgId);

    /** 通过邀请码加入机构 */
    Map<String, Object> join(long userId, String inviteCode);

    /** 踢出成员（仅机构 owner，不能踢自己/owner） */
    void kick(long userId, Long orgId, Long targetUserId);

    /** 退出机构；owner 退出 = 解散机构 */
    void exit(long userId, Long orgId);

    /** 将导图加入机构协作（发起协作需会员开关 orgInviteNeedMember，导图属主才可发起） */
    void addCollab(long userId, Long orgId, Long mindMapId);

    /** 机构协作导图列表 */
    List<Map<String, Object>> collabList(long userId, Long orgId);

    /** 移出协作（导图属主或机构 owner 可操作） */
    void removeCollab(long userId, Long orgId, Long mindMapId);

    /** 用户是否为机构协作导图的合法访问者（供导图读写权限校验） */
    boolean canAccessMindMap(long userId, Long mindMapId);

    /** 管理后台解散机构（物理删除机构/成员/协作关系） */
    void disband(Long orgId);
}

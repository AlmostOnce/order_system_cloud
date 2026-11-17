package com.hbue.ordering.coupon.service;

import java.util.List;

/** 管理员额外的数据范围校验；角色由 Controller 拦截，范围事实由 user-service 提供。 */
public interface CouponAuthorizationService {
    /** 仅允许具备总经理角色且拥有平台级数据范围的操作者管理平台券。 */
    void requirePlatformScope(Long operatorId);

    /** 确认受限窗口 ID 均能从商品服务读取，避免创建引用不存在窗口的不可用券。 */
    void requireExistingWindows(List<Long> windowIds);
}

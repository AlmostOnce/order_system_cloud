package com.hbue.ordering.coupon.service.impl;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.coupon.service.CouponAuthorizationService;
import com.hbue.ordering.product.api.client.WindowInternalClient;
import com.hbue.ordering.user.api.client.UserScopeInternalClient;
import com.hbue.ordering.user.api.vo.UserScopeVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 调用 user-service 的可信范围接口；依赖异常一律按拒绝管理操作处理。
 */
@Service
public class CouponAuthorizationServiceImpl implements CouponAuthorizationService {
    /**
     * user-service 查询管理数据范围所需的服务间共享令牌。
     */
    private final String internalToken;
    /**
     * 仅用于读取授权事实，不接受请求体声明的角色或范围。
     */
    private final UserScopeInternalClient scopeClient;
    /**
     * 复用商品服务内部窗口查询，不直接读取 product_db。
     */
    private final WindowInternalClient windowClient;

    /**
     * 注入共享令牌和 user-service 内部客户端。
     */
    public CouponAuthorizationServiceImpl(
            @Value("${internal.scope.token}") String internalToken,
            UserScopeInternalClient scopeClient,
            WindowInternalClient windowClient
    ) {
        this.internalToken = internalToken;
        this.scopeClient = scopeClient;
        this.windowClient = windowClient;
    }

    /**
     * 范围服务失败、身份不匹配或未授予平台范围时均拒绝，绝不把故障当作全量授权。
     */
    @Override
    public void requirePlatformScope(Long operatorId) {
        ApiResponse<UserScopeVO> response = scopeClient.getScopes(operatorId, internalToken);
        if (response == null
                || !CommonErrorCode.SUCCESS.code().equals(response.getCode())
                || response.getData() == null) {
            throw new BusinessException(
                    CommonErrorCode.SERVICE_UNAVAILABLE,
                    "暂时无法确认优惠券管理范围，请稍后重试"
            );
        }
        UserScopeVO scope = response.getData();
        if (!Objects.equals(operatorId, scope.getUserId())) {
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "用户数据范围响应身份不匹配");
        }
        if (!scope.isPlatform()) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "优惠券管理仅限具备平台数据范围的总经理");
        }
    }

    /**
     * 商品服务不可用时 fail closed；不存在的窗口由当前管理请求直接拒绝。
     */
    @Override
    public void requireExistingWindows(List<Long> windowIds) {
        if (windowIds == null || windowIds.isEmpty()) {
            return;
        }
        ApiResponse<Map<Long, String>> response = windowClient.getWindowNames(windowIds, internalToken);
        if (response == null
                || !CommonErrorCode.SUCCESS.code().equals(response.getCode())
                || response.getData() == null) {
            throw new BusinessException(
                    CommonErrorCode.SERVICE_UNAVAILABLE,
                    "暂时无法确认优惠券适用窗口，请稍后重试"
            );
        }
        if (!response.getData().keySet().containsAll(windowIds)) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "优惠券适用窗口不存在");
        }
    }
}

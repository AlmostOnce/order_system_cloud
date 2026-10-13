package com.hbue.ordering.user.controller;

import com.hbue.ordering.common.core.scope.DataScopeType;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.common.web.security.InternalScopeTokenVerifier;
import com.hbue.ordering.user.api.vo.UserScopeVO;
import com.hbue.ordering.user.model.UserDataScopeDO;
import com.hbue.ordering.user.service.UserDataScopeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户数据范围内部查询接口。
 *
 * <p>供其他业务服务查询某用户的数据范围，使用共享密钥鉴权，
 * 不暴露给外部客户端。范围事实只由 user-service 提供，
 * 各服务不得自行推导或采信客户端传入的范围参数。</p>
 *
 * @author order-system
 * @date 2026-10-13
 */
@RestController
@RequestMapping("/internal/users")
public class UserScopeInternalController {

    /** 数据范围业务服务。 */
    private final UserDataScopeService userDataScopeService;

    /** 内部调用令牌校验器。 */
    private final InternalScopeTokenVerifier internalScopeTokenVerifier;

    /**
     * 创建数据范围内部查询接口。
     *
     * @param userDataScopeService 数据范围业务服务
     * @param internalScopeTokenVerifier 内部调用令牌校验器
     */
    public UserScopeInternalController(
            UserDataScopeService userDataScopeService,
            InternalScopeTokenVerifier internalScopeTokenVerifier
    ) {
        this.userDataScopeService = userDataScopeService;
        this.internalScopeTokenVerifier = internalScopeTokenVerifier;
    }

    /**
     * 查询指定用户的数据范围。
     *
     * @param userId 用户 ID
     * @param internalToken 服务间共享密钥
     * @return 用户数据范围
     */
    @GetMapping("/{userId}/scopes")
    public ApiResponse<UserScopeVO> getScopes(
            @PathVariable("userId") Long userId,
            @RequestHeader(value = "X-Internal-Token", required = false)
            String internalToken
    ) {
        // 内部调用必须携带正确的共享密钥，失败时统一返回 401。
        internalScopeTokenVerifier.verify(internalToken);

        // 汇总用户持有的全部范围。
        List<Long> windowIds = new ArrayList<>();
        List<Long> restaurantIds = new ArrayList<>();
        boolean platform = false;
        for (UserDataScopeDO scope : userDataScopeService.listByUserId(userId)) {
            DataScopeType scopeType = DataScopeType.findByCode(scope.getScopeType());
            if (scopeType == null) {
                continue;
            }
            switch (scopeType) {
                case WINDOW -> windowIds.add(scope.getScopeRef());
                case RESTAURANT -> restaurantIds.add(scope.getScopeRef());
                case PLATFORM -> platform = true;
            }
        }

        UserScopeVO userScopeVO = UserScopeVO.builder()
                .userId(userId)
                .platform(platform)
                .windowIds(windowIds)
                .restaurantIds(restaurantIds)
                .build();
        return ApiResponse.success(userScopeVO);
    }
}

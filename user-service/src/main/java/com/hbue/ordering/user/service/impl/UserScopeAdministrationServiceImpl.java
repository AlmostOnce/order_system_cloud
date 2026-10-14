package com.hbue.ordering.user.service.impl;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.core.scope.DataScopeType;
import com.hbue.ordering.common.core.scope.UserScopeSnapshot;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.common.web.audit.ManagementAuditLogger;
import com.hbue.ordering.product.api.client.WindowInternalClient;
import com.hbue.ordering.product.api.vo.RestaurantInfoVO;
import com.hbue.ordering.product.api.vo.WindowInfoVO;
import com.hbue.ordering.user.dto.request.UserScopeSaveRequest;
import com.hbue.ordering.user.model.UserDataScopeDO;
import com.hbue.ordering.user.service.UserDataScopeService;
import com.hbue.ordering.user.service.UserScopeAdministrationService;
import com.hbue.ordering.user.service.UserService;
import com.hbue.ordering.user.vo.UserScopeAdminVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 用户数据范围授予业务实现。
 *
 * <p>授予者的可授范围完全由其在 user-service 的范围快照决定，实现
 * "只能授予自己已具备的范围"：全平台范围可授予任意范围，餐厅范围可授予
 * 本餐厅及其下属档口。越界、对本人操作返回 403；引用对象不存在返回 400；
 * 目标用户不存在返回 404；product-service 不可用返回 503。范围写入后记录变更前后
 * 快照用于审计；本服务不改变目标用户的角色，也不把客户端提供的范围当作授权依据。</p>
 *
 * @author order-system
 * @date 2026-10-13
 */
@Service
public class UserScopeAdministrationServiceImpl
        implements UserScopeAdministrationService {

    /** 覆盖式授予的审计动作。 */
    private static final String ACTION_SCOPE_REPLACE = "USER_SCOPE_REPLACE";

    /** 撤销单条范围的审计动作。 */
    private static final String ACTION_SCOPE_REVOKE = "USER_SCOPE_REVOKE";

    /** 目标类型：用户。 */
    private static final String TARGET_TYPE_USER = "USER";

    /** 目标类型：数据范围记录。 */
    private static final String TARGET_TYPE_SCOPE = "USER_SCOPE";

    /** 全平台范围的引用值，按约定固定为 0。 */
    private static final long PLATFORM_SCOPE_REF = 0L;

    /** 用户数据范围业务服务。 */
    private final UserDataScopeService userDataScopeService;

    /** 用户业务服务，用于确认目标用户存在。 */
    private final UserService userService;

    /** 档口与餐厅内部查询客户端。 */
    private final WindowInternalClient windowInternalClient;

    /** 管理操作审计记录器。 */
    private final ManagementAuditLogger auditLogger;

    /** 服务间共享密钥。 */
    private final String internalScopeToken;

    /**
     * 创建用户数据范围授予业务服务。
     *
     * @param userDataScopeService 用户数据范围业务服务
     * @param userService 用户业务服务
     * @param windowInternalClient 档口与餐厅内部查询客户端
     * @param auditLogger 管理操作审计记录器
     * @param internalScopeToken 服务间共享密钥
     */
    public UserScopeAdministrationServiceImpl(
            UserDataScopeService userDataScopeService,
            UserService userService,
            WindowInternalClient windowInternalClient,
            ManagementAuditLogger auditLogger,
            @Value("${internal.scope.token:}") String internalScopeToken
    ) {
        this.userDataScopeService = userDataScopeService;
        this.userService = userService;
        this.windowInternalClient = windowInternalClient;
        this.auditLogger = auditLogger;
        this.internalScopeToken = internalScopeToken;
    }

    /**
     * 查询指定用户持有的全部数据范围。
     *
     * @param operatorId 操作者用户 ID
     * @param targetUserId 目标用户 ID
     * @return 目标用户的数据范围列表
     */
    @Override
    public List<UserScopeAdminVO> listScopes(
            Long operatorId,
            Long targetUserId
    ) {
        requireOperator(operatorId);
        requireTargetUser(targetUserId);
        return toAdminVOList(
                userDataScopeService.listByUserId(targetUserId)
        );
    }

    /**
     * 覆盖式设置指定用户的数据范围。
     *
     * @param operatorId 操作者用户 ID
     * @param targetUserId 目标用户 ID
     * @param request 新的范围列表
     * @return 写入后的数据范围列表
     */
    @Override
    public List<UserScopeAdminVO> replaceScopes(
            Long operatorId,
            Long targetUserId,
            UserScopeSaveRequest request
    ) {
        requireOperator(operatorId);
        rejectSelfOperation(operatorId, targetUserId);
        requireTargetUser(targetUserId);

        // 空请求按清空范围处理；操作者和目标用户先完成身份及资源校验。
        List<UserScopeSaveRequest.ScopeItem> items =
                request == null || request.getScopes() == null
                        ? List.of()
                        : request.getScopes();

        // 授予者的可授范围完全来自其自身范围快照，不信任客户端传入的范围参数。
        UserScopeSnapshot operatorScopes =
                userDataScopeService.loadSnapshot(operatorId);

        LocalDateTime now = LocalDateTime.now();
        List<UserDataScopeDO> newScopes = new ArrayList<>();
        Set<String> seenKeys = new LinkedHashSet<>();
        for (UserScopeSaveRequest.ScopeItem item : items) {
            UserDataScopeDO scope = buildScope(
                    operatorScopes,
                    item,
                    targetUserId,
                    now
            );
            // 同一请求内的重复范围先去重，避免触发唯一索引冲突。
            String key = scope.getScopeType() + ":" + scope.getScopeRef();
            if (seenKeys.add(key)) {
                newScopes.add(scope);
            }
        }

        // 先保存变更前快照，再在 user-service 内替换并重新读取最终状态用于审计。
        List<UserDataScopeDO> before =
                userDataScopeService.listByUserId(targetUserId);
        userDataScopeService.replaceScopes(targetUserId, newScopes);
        List<UserDataScopeDO> after =
                userDataScopeService.listByUserId(targetUserId);

        auditLogger.record(
                operatorId,
                ACTION_SCOPE_REPLACE,
                TARGET_TYPE_USER,
                targetUserId,
                describeScopes(before),
                describeScopes(after),
                true
        );
        return toAdminVOList(after);
    }

    /**
     * 撤销指定用户的一条数据范围。
     *
     * @param operatorId 操作者用户 ID
     * @param targetUserId 目标用户 ID
     * @param scopeId 范围记录 ID
     */
    @Override
    public void revokeScope(
            Long operatorId,
            Long targetUserId,
            Long scopeId
    ) {
        requireOperator(operatorId);
        rejectSelfOperation(operatorId, targetUserId);
        requireTargetUser(targetUserId);
        if (scopeId == null) {
            throw new BusinessException(
                    CommonErrorCode.BAD_REQUEST,
                    "范围记录 ID 不能为空"
            );
        }

        // 先从目标用户自己的记录中定位范围，避免仅凭客户端 scopeId 删除任意记录。
        UserDataScopeDO target = null;
        for (UserDataScopeDO scope
                : userDataScopeService.listByUserId(targetUserId)) {
            if (scopeId.equals(scope.getScopeId())) {
                target = scope;
                break;
            }
        }
        if (target == null) {
            throw new BusinessException(
                    CommonErrorCode.NOT_FOUND,
                    "数据范围不存在"
            );
        }

        requireWithinOperatorScope(
                userDataScopeService.loadSnapshot(operatorId),
                target
        );

        userDataScopeService.revokeScope(targetUserId, scopeId);
        auditLogger.record(
                operatorId,
                ACTION_SCOPE_REVOKE,
                TARGET_TYPE_SCOPE,
                scopeId,
                describeScope(target),
                null,
                true
        );
    }

    /**
     * 根据请求构建一条数据范围，并校验其落在授予者的可授范围内。
     *
     * @param operatorScopes 授予者的范围快照
     * @param item 请求中的范围条目
     * @param targetUserId 目标用户 ID
     * @param now 写入时间
     * @return 数据范围持久化对象
     */
    private UserDataScopeDO buildScope(
            UserScopeSnapshot operatorScopes,
            UserScopeSaveRequest.ScopeItem item,
            Long targetUserId,
            LocalDateTime now
    ) {
        DataScopeType scopeType = DataScopeType.findByCode(
                item.getScopeType()
        );
        if (scopeType == null) {
            throw new BusinessException(
                    CommonErrorCode.BAD_REQUEST,
                    "未知的数据范围类型：" + item.getScopeType()
            );
        }

        long scopeRef = resolveScopeRef(operatorScopes, scopeType,
                item.getScopeRef());

        UserDataScopeDO scope = new UserDataScopeDO();
        scope.setUserId(targetUserId);
        scope.setScopeType(scopeType.getCode());
        scope.setScopeRef(scopeRef);
        scope.setCreatedAt(now);
        scope.setUpdatedAt(now);
        return scope;
    }

    /**
     * 解析并校验范围引用，同时确认授予者具备该范围。
     *
     * @param operatorScopes 授予者的范围快照
     * @param scopeType 范围类型
     * @param scopeRef 请求中的范围引用
     * @return 规范化后的范围引用
     */
    private long resolveScopeRef(
            UserScopeSnapshot operatorScopes,
            DataScopeType scopeType,
            Long scopeRef
    ) {
        // 不同范围类型的引用含义不同，分别校验存在性和授予者的数据边界。
        switch (scopeType) {
            case PLATFORM -> {
                // 全平台范围是授权链的根，只有本身持有全平台范围的授予者才能继续授予。
                if (!operatorScopes.isPlatformScope()) {
                    throw denied();
                }
                return PLATFORM_SCOPE_REF;
            }
            case RESTAURANT -> {
                Long restaurantId = requirePositive(scopeRef, "餐厅 ID");
                requireRestaurantExists(restaurantId);
                if (!operatorScopes.coversRestaurant(restaurantId)) {
                    throw denied();
                }
                return restaurantId;
            }
            case WINDOW -> {
                Long windowId = requirePositive(scopeRef, "档口 ID");
                Long restaurantId = resolveWindowRestaurant(windowId);
                if (restaurantId == null) {
                    throw new BusinessException(
                            CommonErrorCode.BAD_REQUEST,
                            "档口不存在：" + windowId
                    );
                }
                // 直管档口，或档口所属餐厅落在授予者范围内，均视为有权。
                if (!operatorScopes.coversWindow(windowId)
                        && !operatorScopes.coversRestaurant(restaurantId)) {
                    throw denied();
                }
                return windowId;
            }
            default -> throw new BusinessException(
                    CommonErrorCode.BAD_REQUEST,
                    "不支持的数据范围类型"
            );
        }
    }

    /**
     * 校验待撤销的范围仍在授予者的可授范围内。
     *
     * @param operatorScopes 授予者的范围快照
     * @param scope 待撤销的范围记录
     */
    private void requireWithinOperatorScope(
            UserScopeSnapshot operatorScopes,
            UserDataScopeDO scope
    ) {
        DataScopeType scopeType = DataScopeType.findByCode(
                scope.getScopeType()
        );
        if (scopeType == null) {
            throw new BusinessException(
                    CommonErrorCode.BAD_REQUEST,
                    "未知的数据范围类型：" + scope.getScopeType()
            );
        }

        switch (scopeType) {
            case PLATFORM -> {
                if (!operatorScopes.isPlatformScope()) {
                    throw denied();
                }
            }
            case RESTAURANT -> {
                if (!operatorScopes.coversRestaurant(scope.getScopeRef())) {
                    throw denied();
                }
            }
            case WINDOW -> {
                Long windowId = scope.getScopeRef();
                boolean covered = operatorScopes.coversWindow(windowId);
                if (!covered) {
                    Long restaurantId = resolveWindowRestaurant(windowId);
                    covered = restaurantId != null
                            && operatorScopes.coversRestaurant(restaurantId);
                }
                if (!covered) {
                    throw denied();
                }
            }
        }
    }

    /**
     * 校验餐厅真实存在。
     *
     * @param restaurantId 餐厅 ID
     */
    private void requireRestaurantExists(Long restaurantId) {
        RestaurantInfoVO restaurant = unwrap(
                windowInternalClient.getRestaurant(
                        restaurantId,
                        internalScopeToken
                )
        );
        if (restaurant == null || restaurant.getRestaurantId() == null) {
            throw new BusinessException(
                    CommonErrorCode.BAD_REQUEST,
                    "餐厅不存在：" + restaurantId
            );
        }
    }

    /**
     * 查询档口所属餐厅。
     *
     * @param windowId 档口 ID
     * @return 所属餐厅 ID；档口不存在时返回 null
     */
    private Long resolveWindowRestaurant(Long windowId) {
        WindowInfoVO window = unwrap(
                windowInternalClient.getWindow(windowId, internalScopeToken)
        );
        return window == null ? null : window.getRestaurantId();
    }

    /**
     * 解包内部接口响应，失败时统一按服务不可用处理。
     *
     * @param response 内部接口响应
     * @param <T> 数据类型
     * @return 响应数据；成功但对象不存在时返回 null
     */
    private <T> T unwrap(ApiResponse<T> response) {
        if (response == null
                || !CommonErrorCode.SUCCESS.code().equals(response.getCode())) {
            throw new BusinessException(
                    CommonErrorCode.SERVICE_UNAVAILABLE,
                    "商品服务暂时不可用"
            );
        }
        return response.getData();
    }

    /**
     * 校验操作者身份存在。
     *
     * @param operatorId 操作者用户 ID
     */
    private void requireOperator(Long operatorId) {
        if (operatorId == null) {
            throw new BusinessException(
                    CommonErrorCode.UNAUTHORIZED,
                    "未登录或登录已过期"
            );
        }
    }

    /**
     * 校验目标用户存在。
     *
     * @param targetUserId 目标用户 ID
     */
    private void requireTargetUser(Long targetUserId) {
        if (targetUserId == null) {
            throw new BusinessException(
                    CommonErrorCode.BAD_REQUEST,
                    "用户 ID 不能为空"
            );
        }
        // 目标用户不存在时抛出 404。
        userService.getUserById(targetUserId);
    }

    /**
     * 拒绝对自己授予或撤销范围。
     *
     * @param operatorId 操作者用户 ID
     * @param targetUserId 目标用户 ID
     */
    private void rejectSelfOperation(Long operatorId, Long targetUserId) {
        if (operatorId != null && operatorId.equals(targetUserId)) {
            throw new BusinessException(
                    CommonErrorCode.FORBIDDEN,
                    "不得对本人授予或撤销数据范围"
            );
        }
    }

    /**
     * 校验范围引用为正整数。
     *
     * @param value 范围引用
     * @param label 字段名称
     * @return 校验通过的范围引用
     */
    private Long requirePositive(Long value, String label) {
        if (value == null || value <= 0) {
            throw new BusinessException(
                    CommonErrorCode.BAD_REQUEST,
                    label + "必须为正整数"
            );
        }
        return value;
    }

    /**
     * 构造越界授权异常。
     *
     * @return 403 业务异常
     */
    private BusinessException denied() {
        return new BusinessException(
                CommonErrorCode.FORBIDDEN,
                "不得授予超出自身范围的数据范围"
        );
    }

    /**
     * 将数据范围持久化对象转换为管理视图对象。
     *
     * @param scopes 数据范围列表
     * @return 管理视图对象列表
     */
    private List<UserScopeAdminVO> toAdminVOList(
            List<UserDataScopeDO> scopes
    ) {
        List<UserScopeAdminVO> result = new ArrayList<>();
        if (scopes == null) {
            return result;
        }
        for (UserDataScopeDO scope : scopes) {
            result.add(UserScopeAdminVO.builder()
                    .scopeId(scope.getScopeId())
                    .scopeType(scope.getScopeType())
                    .scopeRef(scope.getScopeRef())
                    .build());
        }
        return result;
    }

    /**
     * 将范围列表压缩为审计用的可读字符串。
     *
     * @param scopes 数据范围列表
     * @return 形如 [RESTAURANT:1,WINDOW:2] 的字符串
     */
    private String describeScopes(List<UserDataScopeDO> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < scopes.size(); index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(describeScope(scopes.get(index)));
        }
        return builder.append(']').toString();
    }

    /**
     * 将单条范围压缩为审计用的可读字符串。
     *
     * @param scope 数据范围
     * @return 形如 WINDOW:2 的字符串
     */
    private String describeScope(UserDataScopeDO scope) {
        return scope.getScopeType() + ":" + scope.getScopeRef();
    }
}

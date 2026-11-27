package com.hbue.ordering.user.service.impl;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.core.role.RoleCode;
import com.hbue.ordering.common.core.scope.UserScopeSnapshot;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.product.api.client.WindowInternalClient;
import com.hbue.ordering.user.mapper.UserMapper;
import com.hbue.ordering.user.model.UserDO;
import com.hbue.ordering.user.service.UserDataScopeService;
import com.hbue.ordering.user.service.UserDirectoryService;
import com.hbue.ordering.user.vo.AdminUserPageVO;
import com.hbue.ordering.user.vo.AdminUserSummaryVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 管理端账号检索业务实现。
 *
 * <p>只做读取与范围收敛，不修改角色、状态或数据范围。安全边界有三层：
 * 控制器上的 {@code @PreAuthorize} 限定角色；本实现按操作者的范围快照决定
 * 候选账号集合，绝不接受客户端传入的范围参数；响应只返回脱敏字段，不返回
 * 内部主键、密码哈希或完整邮箱。</p>
 *
 * @author order-system
 * @date 2026-11-28
 */
@Service
public class UserDirectoryServiceImpl implements UserDirectoryService {

    /**
     * 默认每页条数，与前端首屏请求保持一致。
     */
    private static final int DEFAULT_PAGE_SIZE = 20;

    /**
     * 单页最大条数，防止一次拉取过多账号。
     */
    private static final int MAX_PAGE_SIZE = 50;

    /**
     * 合法游标格式：正整数，长度不足以溢出 long。
     */
    private static final String CURSOR_PATTERN = "[1-9][0-9]{0,18}";

    /**
     * 账号状态：启用。
     */
    private static final Integer STATUS_ENABLED = 1;

    /**
     * 账号状态：禁用。
     */
    private static final Integer STATUS_DISABLED = 0;

    /**
     * 账号数据访问接口。
     */
    private final UserMapper userMapper;

    /**
     * 用户数据范围业务服务，用于读取操作者的范围快照。
     */
    private final UserDataScopeService userDataScopeService;

    /**
     * 档口与餐厅内部查询客户端，用于把餐厅范围展开成档口集合。
     */
    private final WindowInternalClient windowInternalClient;

    /**
     * 服务间共享密钥。
     */
    private final String internalScopeToken;

    /**
     * 创建管理端账号检索业务实现。
     *
     * @param userMapper           账号数据访问接口
     * @param userDataScopeService 用户数据范围业务服务
     * @param windowInternalClient 档口与餐厅内部查询客户端
     * @param internalScopeToken   服务间共享密钥
     */
    public UserDirectoryServiceImpl(
            UserMapper userMapper,
            UserDataScopeService userDataScopeService,
            WindowInternalClient windowInternalClient,
            @Value("${internal.scope.token:}") String internalScopeToken
    ) {
        this.userMapper = userMapper;
        this.userDataScopeService = userDataScopeService;
        this.windowInternalClient = windowInternalClient;
        this.internalScopeToken = internalScopeToken;
    }

    /**
     * 系统管理员检索账号目录。
     *
     * @param operatorId 操作者用户 ID
     * @param keyword    关键词，可为空
     * @param roleCode   角色编码筛选，可为空
     * @param status     状态筛选：1 启用、0 禁用，可为空
     * @param cursor     上一页游标，首屏为空
     * @param size       每页条数
     * @return 游标分页的账号摘要
     */
    @Override
    public AdminUserPageVO listDirectory(
            Long operatorId,
            String keyword,
            String roleCode,
            Integer status,
            String cursor,
            int size
    ) {
        requireOperator(operatorId);
        String normalizedRole = normalizeRoleCode(roleCode);
        requireValidStatus(status);
        Long beforeId = parseCursor(cursor);
        int limit = normalizePageSize(size);

        String trimmedKeyword = trimToNull(keyword);
        List<UserDO> rows = userMapper.selectAdminDirectoryPage(
                likePattern(trimmedKeyword),
                trimmedKeyword,
                normalizedRole,
                status,
                beforeId,
                limit + 1
        );
        return toPage(rows, limit);
    }

    /**
     * 业务角色检索可授予范围的候选账号。
     *
     * <p>持有全平台范围的总经理检索全平台账号；餐厅经理只检索已持有本餐厅或
     * 其下属档口范围的账号。餐厅范围会先经 product-service 展开为档口集合，
     * 这样仅持有档口范围的档口负责人也能被经理检索到。</p>
     *
     * @param operatorId 操作者用户 ID
     * @param keyword    关键词，可为空
     * @param cursor     上一页游标，首屏为空
     * @param size       每页条数
     * @return 游标分页的候选账号摘要
     */
    @Override
    public AdminUserPageVO listScopeCandidates(
            Long operatorId,
            String keyword,
            String cursor,
            int size
    ) {
        requireOperator(operatorId);
        Long beforeId = parseCursor(cursor);
        int limit = normalizePageSize(size);
        String trimmedKeyword = trimToNull(keyword);
        String pattern = likePattern(trimmedKeyword);

        UserScopeSnapshot operatorScopes =
                userDataScopeService.loadSnapshot(operatorId);

        // 全平台范围：可检索全平台账号，不附加范围过滤。
        if (operatorScopes.isPlatformScope()) {
            List<UserDO> rows = userMapper.selectAdminDirectoryPage(
                    pattern,
                    trimmedKeyword,
                    null,
                    null,
                    beforeId,
                    limit + 1
            );
            return toPage(rows, limit);
        }

        Set<Long> restaurantIds = new LinkedHashSet<>(
                operatorScopes.getRestaurantIds()
        );
        Set<Long> windowIds = new LinkedHashSet<>(
                operatorScopes.getWindowIds()
        );
        // 餐厅范围需要展开到档口：档口负责人通常只持有 WINDOW 范围。
        if (!restaurantIds.isEmpty()) {
            windowIds.addAll(resolveRestaurantWindowIds(
                    new ArrayList<>(restaurantIds)
            ));
        }
        // 没有任何餐厅或档口范围的经理不存在可授予对象，直接返回空页。
        if (restaurantIds.isEmpty() && windowIds.isEmpty()) {
            return new AdminUserPageVO(List.of(), null, false);
        }

        List<UserDO> rows = userMapper.selectScopedUserCandidates(
                new ArrayList<>(restaurantIds),
                new ArrayList<>(windowIds),
                pattern,
                trimmedKeyword,
                beforeId,
                limit + 1
        );
        return toPage(rows, limit);
    }

    /**
     * 通过 product-service 把餐厅集合展开为档口集合。
     *
     * @param restaurantIds 餐厅 ID 列表，保证非空
     * @return 档口 ID 列表；餐厅下暂无档口时返回空列表
     */
    private List<Long> resolveRestaurantWindowIds(List<Long> restaurantIds) {
        ApiResponse<List<Long>> response =
                windowInternalClient.getWindowIdsByRestaurants(
                        restaurantIds,
                        internalScopeToken
                );
        if (response == null
                || !CommonErrorCode.SUCCESS.code().equals(response.getCode())) {
            // 范围展开依赖商品服务，远程失败时失败关闭，不返回可能越权的候选。
            throw new BusinessException(
                    CommonErrorCode.SERVICE_UNAVAILABLE,
                    "商品服务暂时不可用"
            );
        }
        List<Long> data = response.getData();
        return data == null ? List.of() : data;
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
     * 规范化并校验角色编码。
     *
     * @param roleCode 角色编码，可为空
     * @return 去空格后的合法角色编码；空输入返回 null 表示不限角色
     */
    private String normalizeRoleCode(String roleCode) {
        String normalized = trimToNull(roleCode);
        if (normalized == null) {
            return null;
        }
        if (RoleCode.findByCode(normalized) == null) {
            throw new BusinessException(
                    CommonErrorCode.BAD_REQUEST,
                    "角色编码不合法：" + normalized
            );
        }
        return normalized;
    }

    /**
     * 校验账号状态取值。
     *
     * @param status 状态值，可为空
     */
    private void requireValidStatus(Integer status) {
        if (status == null) {
            return;
        }
        if (!STATUS_ENABLED.equals(status) && !STATUS_DISABLED.equals(status)) {
            throw new BusinessException(
                    CommonErrorCode.BAD_REQUEST,
                    "账号状态只能是 0（禁用）或 1（启用）"
            );
        }
    }

    /**
     * 解析游标为内部主键。
     *
     * @param cursor 上一页返回的 nextCursor
     * @return 游标主键；首屏为空时返回 null
     */
    private Long parseCursor(String cursor) {
        String normalized = trimToNull(cursor);
        if (normalized == null) {
            return null;
        }
        if (!normalized.matches(CURSOR_PATTERN)) {
            throw new BusinessException(
                    CommonErrorCode.BAD_REQUEST,
                    "用户游标无效"
            );
        }
        return Long.valueOf(normalized);
    }

    /**
     * 规范化每页条数。
     *
     * @param size 请求条数
     * @return 落在允许区间内的条数
     */
    private int normalizePageSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    /**
     * 把关键词转换为 LIKE 匹配串。
     *
     * <p>先转义转义符本身，再转义 {@code %} 与 {@code _}，防止用户输入被当作
     * 通配符扩大检索范围；SQL 中使用 {@code ESCAPE '!'} 声明转义符。</p>
     *
     * @param keyword 已去空格的关键词，可为 null
     * @return 形如 %keyword% 的匹配串；关键词为空时返回 null
     */
    private String likePattern(String keyword) {
        if (keyword == null) {
            return null;
        }
        String escaped = keyword
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }

    /**
     * 去掉首尾空白，空串转为 null。
     *
     * @param value 原始字符串
     * @return 去空格后的字符串或 null
     */
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 将数据库记录转换为分页结果。
     *
     * <p>查询时已多取一条用于判断是否还有下一页；对外只返回请求条数，
     * 游标取当前页末条的内部主键。</p>
     *
     * @param rows  数据库返回的记录，最多 limit + 1 条
     * @param limit 请求条数
     * @return 游标分页结果
     */
    private AdminUserPageVO toPage(List<UserDO> rows, int limit) {
        if (rows == null || rows.isEmpty()) {
            return new AdminUserPageVO(List.of(), null, false);
        }
        boolean hasMore = rows.size() > limit;
        List<UserDO> visible = hasMore ? rows.subList(0, limit) : rows;
        List<AdminUserSummaryVO> items = new ArrayList<>(visible.size());
        for (UserDO row : visible) {
            items.add(toSummary(row));
        }
        String nextCursor = hasMore
                ? String.valueOf(visible.get(visible.size() - 1).getUserId())
                : null;
        return new AdminUserPageVO(items, nextCursor, hasMore);
    }

    /**
     * 将数据库记录转换为脱敏摘要。
     *
     * @param user 用户持久化对象
     * @return 账号摘要
     */
    private AdminUserSummaryVO toSummary(UserDO user) {
        return new AdminUserSummaryVO(
                user.getUserUuid(),
                user.getUserNo(),
                user.getUsername(),
                user.getRoleCode(),
                user.getStatus(),
                maskEmail(user.getEmail()),
                user.getEmailVerifiedAt() != null,
                user.getCreatedAt()
        );
    }

    /**
     * 对邮箱做脱敏处理。
     *
     * <p>保留邮箱域名以帮助管理员区分账号来源，本地部分最多保留前两个字符。
     * 邮箱为空（历史账号或已注销）时返回 null，不做任何伪造。</p>
     *
     * @param email 完整邮箱
     * @return 形如 ab***@example.com 的脱敏邮箱；无法解析时返回 ***
     */
    private String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String local = email.substring(0, at);
        String domain = email.substring(at);
        String visible = local.length() <= 2
                ? local.substring(0, 1)
                : local.substring(0, 2);
        return visible + "***" + domain;
    }
}

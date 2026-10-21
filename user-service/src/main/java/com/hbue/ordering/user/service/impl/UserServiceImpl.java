package com.hbue.ordering.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.core.role.RoleCode;
import com.hbue.ordering.common.web.audit.ManagementAuditLogger;
import com.hbue.ordering.user.api.dto.UserPasswordVerifyCommand;
import com.hbue.ordering.user.api.dto.UserRegisterCommand;
import com.hbue.ordering.user.api.vo.UserBasicVO;
import com.hbue.ordering.user.mapper.UserMapper;
import com.hbue.ordering.user.model.UserDO;
import com.hbue.ordering.user.service.UserDataScopeService;
import com.hbue.ordering.user.service.UserService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户业务服务实现。
 *
 * <p>负责 user-service 内部的用户账户读写、密码校验、角色及账号状态维护和
 * 初始管理账号引导。登录密码使用 BCrypt 哈希保存；角色权限只描述可执行的
 * 操作类别，具体数据范围由 {@link UserDataScopeService} 单独维护。管理变更
 * 通过审计记录器留痕，且不跨服务直接访问其他服务的数据库。</p>
 *
 * @author order-system
 * @date 2026-09-20
 */
@Service
public class UserServiceImpl
        extends ServiceImpl<UserMapper, UserDO>
        implements UserService {

    /**
     * 用户正常状态。
     */
    private static final Integer USER_STATUS_ENABLED = 1;

    /**
     * 用户停用状态。
     */
    private static final Integer USER_STATUS_DISABLED = 0;

    /**
     * 默认用户角色。
     */
    private static final String DEFAULT_ROLE_CODE = "CUSTOMER";

    /**
     * 系统管理员角色。
     */
    private static final String SYSTEM_ADMIN_ROLE_CODE = "SYSTEM_ADMIN";

    /**
     * 总经理角色。
     */
    private static final String GENERAL_MANAGER_ROLE_CODE = "GENERAL_MANAGER";

    /**
     * 默认账户余额。
     */
    private static final BigDecimal DEFAULT_BALANCE = BigDecimal.ZERO;

    /**
     * 角色分配的审计动作。
     */
    private static final String ACTION_ROLE_ASSIGN = "USER_ROLE_ASSIGN";

    /**
     * 角色分配的审计目标类型。
     */
    private static final String TARGET_TYPE_USER = "USER";

    /**
     * 账号状态变更的审计动作。
     */
    private static final String ACTION_STATUS_UPDATE = "USER_STATUS_UPDATE";

    private final PasswordEncoder passwordEncoder;

    /**
     * 用户数据范围业务服务，用于为初始总经理写入全平台范围。
     */
    private final UserDataScopeService userDataScopeService;

    /**
     * 管理操作审计记录器。
     */
    private final ManagementAuditLogger auditLogger;

    /**
     * 创建用户业务服务。
     *
     * @param passwordEncoder 密码加密器
     * @param userDataScopeService 用户数据范围业务服务
     * @param auditLogger 管理操作审计记录器
     */
    public UserServiceImpl(
            PasswordEncoder passwordEncoder,
            UserDataScopeService userDataScopeService,
            ManagementAuditLogger auditLogger
    ) {
        this.passwordEncoder = passwordEncoder;
        this.userDataScopeService = userDataScopeService;
        this.auditLogger = auditLogger;
    }

    /**
     * 根据用户 ID 查询用户基础信息。
     *
     * @param userId 用户 ID
     * @return 用户基础信息
     * @throws BusinessException 用户不存在、已停用或已删除时抛出
     */
    @Override
    public UserBasicVO getUserById(Long userId) {
        // 查询正常且未删除的用户。
        UserDO userDO = baseMapper.selectAvailableById(
                userId,
                USER_STATUS_ENABLED
        );

        // 用户不存在、已禁用或已删除时统一抛出业务异常。
        if (userDO == null) {
            throw new BusinessException(
                    CommonErrorCode.NOT_FOUND,
                    "用户不存在"
            );
        }

        // 只返回允许对外暴露的用户字段。
        return buildUserBasicVO(userDO);
    }

    /**
     * 注册用户。
     *
     * @param command 用户注册命令
     * @return 注册成功的用户基础信息
     * @throws BusinessException 用户名或手机号重复时抛出；并发重复由数据库唯一约束兜底
     */
    @Override
    public UserBasicVO register(UserRegisterCommand command) {
        // 校验用户名或手机号是否已经注册。
        UserDO existingUser = baseMapper.selectByUsernameOrPhone(
                command.getUsername(),
                command.getPhone()
        );

        // 用户名或手机号重复时拒绝注册。
        if (existingUser != null) {
            throw new BusinessException(
                    CommonErrorCode.BAD_REQUEST,
                    "用户名或手机号已存在"
            );
        }

        // 组装用户持久化对象。
        UserDO userDO = new UserDO();
        userDO.setUsername(command.getUsername());
        userDO.setPhone(command.getPhone());
        userDO.setRoleCode(DEFAULT_ROLE_CODE);
        userDO.setBalance(DEFAULT_BALANCE);

        // 密码必须经过 BCrypt 加密后才能保存。
        userDO.setPasswordHash(
                passwordEncoder.encode(command.getPassword())
        );

        // 设置用户初始状态和版本号。
        userDO.setStatus(USER_STATUS_ENABLED);
        userDO.setVersion(0);

        // 设置创建时间和更新时间。
        LocalDateTime now = LocalDateTime.now();
        userDO.setCreatedAt(now);
        userDO.setUpdatedAt(now);

        try {
            // 保存用户数据。
            save(userDO);
        } catch (DuplicateKeyException exception) {
            // 防止并发注册绕过前置重复校验。
            throw new BusinessException(
                    CommonErrorCode.BAD_REQUEST,
                    "用户名或手机号已存在"
            );
        }

        // 返回注册成功后的用户基础信息。
        return buildUserBasicVO(userDO);
    }


    /**
     * 校验用户账号密码。
     *
     * @param command 用户密码校验命令
     * @return 校验成功的用户基础信息
     * @throws BusinessException 用户不存在、不可用或密码不匹配时统一抛出认证失败
     */
    @Override
    public UserBasicVO authenticate(
            UserPasswordVerifyCommand command
    ) {
        // 根据用户名查询用户。
        UserDO userDO = baseMapper.selectByUsernameForLogin(
                command.getUsername()
        );

        // 用户不存在、已禁用或已删除时统一返回认证失败。
        if (userDO == null
                || !Integer.valueOf(1).equals(userDO.getStatus())
                || userDO.getDeletedAt() != null) {
            throw new BusinessException(
                    CommonErrorCode.UNAUTHORIZED,
                    "用户名或密码错误"
            );
        }

        // 使用 BCrypt 校验明文密码和数据库密码哈希。
        boolean passwordMatched = passwordEncoder.matches(
                command.getPassword(),
                userDO.getPasswordHash()
        );

        // 密码不匹配时返回统一认证失败信息。
        if (!passwordMatched) {
            throw new BusinessException(
                    CommonErrorCode.UNAUTHORIZED,
                    "用户名或密码错误"
            );
        }

        // 认证成功后只返回安全的用户基础信息。
        return buildUserBasicVO(userDO);
    }


    /**
     * 安全引导系统管理员账号。
     *
     * @param username 管理员用户名
     * @param email 管理员邮箱
     * @param rawPassword 管理员明文密码
     * @return 是否实际创建了新账号
     * @throws BusinessException 检查到用户名或邮箱被其他账号占用时抛出
     */
    @Override
    public boolean bootstrapSystemAdmin(
            String username,
            String email,
            String rawPassword
    ) {
        return createBootstrapAccount(
                username,
                email,
                rawPassword,
                SYSTEM_ADMIN_ROLE_CODE
        ) != null;
    }

    /**
     * 安全引导初始总经理账号，并写入其全平台数据范围。
     *
     * <p>账号与范围在同一本地事务内提交：写 user_data_scopes 失败时整体回滚，
     * 不会留下"有总经理但没有范围"的半个状态。</p>
     *
     * @param username 总经理用户名
     * @param email 总经理邮箱
     * @param rawPassword 总经理明文密码
     * @return 是否实际创建了新账号
     * @throws BusinessException 账号冲突或创建全平台范围失败时抛出，事务会回滚账号创建
     */
    @Override
    @Transactional
    public boolean bootstrapGeneralManager(
            String username,
            String email,
            String rawPassword
    ) {
        UserDO createdUser = createBootstrapAccount(
                username,
                email,
                rawPassword,
                GENERAL_MANAGER_ROLE_CODE
        );
        if (createdUser == null) {
            return false;
        }

        // 初始总经理是数据范围授予链的根，必须同时具备全平台范围。
        userDataScopeService.grantPlatformScope(createdUser.getUserId());
        return true;
    }

    /**
     * 修改指定用户的角色编码。
     *
     * @param operatorId 操作者用户 ID
     * @param targetUserId 目标用户 ID
     * @param roleCode 目标角色编码
     * @return 变更后的用户基础信息
     * @throws BusinessException 操作者无效、目标用户不存在、角色非法或试图修改本人角色时抛出
     */
    @Override
    public UserBasicVO assignRole(
            Long operatorId,
            Long targetUserId,
            String roleCode
    ) {
        if (operatorId == null) {
            throw new BusinessException(
                    CommonErrorCode.UNAUTHORIZED,
                    "未登录或登录已过期"
            );
        }
        if (targetUserId == null) {
            throw new BusinessException(
                    CommonErrorCode.BAD_REQUEST,
                    "用户 ID 不能为空"
            );
        }

        // 硬约束①：禁止修改本人角色编码，避免技术角色自我提权。
        if (operatorId.equals(targetUserId)) {
            throw new BusinessException(
                    CommonErrorCode.FORBIDDEN,
                    "禁止修改本人角色编码"
            );
        }

        // 角色白名单校验：只接受 RoleCode 中已定义的编码。
        RoleCode targetRole = RoleCode.findByCode(roleCode);
        if (targetRole == null) {
            throw new BusinessException(
                    CommonErrorCode.BAD_REQUEST,
                    "角色编码不合法：" + roleCode
            );
        }

        // 只操作启用且未删除的账号。
        UserDO targetUser = baseMapper.selectAvailableById(
                targetUserId,
                USER_STATUS_ENABLED
        );
        if (targetUser == null) {
            throw new BusinessException(
                    CommonErrorCode.NOT_FOUND,
                    "用户不存在"
            );
        }

        String beforeRoleCode = targetUser.getRoleCode();
        if (!targetRole.getCode().equals(beforeRoleCode)) {
            // 只写 role_code，不触碰任何业务数据范围。
            UpdateWrapper<UserDO> update = new UpdateWrapper<>();
            update.eq("user_id", targetUserId)
                    .set("role_code", targetRole.getCode())
                    .set("updated_at", LocalDateTime.now());
            update(null, update);
            targetUser.setRoleCode(targetRole.getCode());
        }

        auditLogger.record(
                operatorId,
                ACTION_ROLE_ASSIGN,
                TARGET_TYPE_USER,
                targetUserId,
                beforeRoleCode,
                targetRole.getCode(),
                true
        );

        return buildUserBasicVO(targetUser);
    }

    /**
     * 启用或禁用指定账号。
     *
     * @param operatorId 操作者用户 ID
     * @param targetUserId 目标用户 ID
     * @param status 目标状态：1 启用，0 禁用
     * @throws BusinessException 状态值非法、用户不存在或禁用最后一个启用的系统管理员时抛出
     */
    @Override
    public void updateUserStatus(
            Long operatorId,
            Long targetUserId,
            Integer status
    ) {
        if (operatorId == null) {
            throw new BusinessException(
                    CommonErrorCode.UNAUTHORIZED,
                    "未登录或登录已过期"
            );
        }
        if (targetUserId == null) {
            throw new BusinessException(
                    CommonErrorCode.BAD_REQUEST,
                    "用户 ID 不能为空"
            );
        }
        if (!USER_STATUS_ENABLED.equals(status)
                && !USER_STATUS_DISABLED.equals(status)) {
            throw new BusinessException(
                    CommonErrorCode.BAD_REQUEST,
                    "账号状态只能是 0（禁用）或 1（启用）"
            );
        }

        // 账号状态变更需要读到已停用账号，因此按"未删除"而不按"启用"查询。
        UserDO targetUser = baseMapper.selectNotDeletedById(targetUserId);
        if (targetUser == null) {
            throw new BusinessException(
                    CommonErrorCode.NOT_FOUND,
                    "用户不存在"
            );
        }

        Integer beforeStatus = targetUser.getStatus();
        if (status.equals(beforeStatus)) {
            // 目标状态与当前一致：幂等返回，不写库也不审计。
            return;
        }

        // 硬约束③：禁止禁用最后一个启用的系统管理员，避免"无人可管理"。
        if (USER_STATUS_DISABLED.equals(status)
                && SYSTEM_ADMIN_ROLE_CODE.equals(targetUser.getRoleCode())) {
            Long enabledAdmins = baseMapper.selectCount(
                    new QueryWrapper<UserDO>()
                            .eq("role_code", SYSTEM_ADMIN_ROLE_CODE)
                            .eq("status", USER_STATUS_ENABLED)
                            .isNull("deleted_at")
            );
            if (enabledAdmins != null && enabledAdmins <= 1) {
                throw new BusinessException(
                        CommonErrorCode.CONFLICT,
                        "禁止禁用最后一个启用的系统管理员"
                );
            }
        }

        // 只有通过最后一个管理员保护后才更新状态，并记录操作者及变更前后值。
        UpdateWrapper<UserDO> update = new UpdateWrapper<>();
        update.eq("user_id", targetUserId)
                .set("status", status)
                .set("updated_at", LocalDateTime.now());
        update(null, update);

        auditLogger.record(
                operatorId,
                ACTION_STATUS_UPDATE,
                TARGET_TYPE_USER,
                targetUserId,
                String.valueOf(beforeStatus),
                String.valueOf(status),
                true
        );
    }

    /**
     * 按固定角色安全创建引导账号。
     *
     * <p>三种结果：目标账号已是启用且未删除的目标角色时返回 {@code null} 表示跳过；
     * 用户名被其他账号占用、邮箱被任何其他账号占用、或目标角色处于禁用/删除状态时
     * 抛出冲突；其余情况创建账号并返回持久化对象。</p>
     *
     * @param username 引导用户名
     * @param email 引导邮箱
     * @param rawPassword 引导明文密码
     * @param roleCode 固定角色编码
     * @return 新建的用户持久化对象；已存在目标角色时返回 null
     */
    private UserDO createBootstrapAccount(
            String username,
            String email,
            String rawPassword,
            String roleCode
    ) {
        // 参数为空时不做任何写库动作。
        if (username == null
                || username.isBlank()
                || email == null
                || email.isBlank()
                || rawPassword == null
                || rawPassword.isBlank()) {
            return null;
        }

        // 先按用户名精确查重：命中时区分"已是目标角色"与"被其他角色占用"。
        UserDO existingByUsername = baseMapper.selectByUsername(username);
        if (existingByUsername != null) {
            boolean sameActiveRole = roleCode.equals(existingByUsername.getRoleCode())
                    && USER_STATUS_ENABLED.equals(existingByUsername.getStatus())
                    && existingByUsername.getDeletedAt() == null;
            if (sameActiveRole) {
                // 已存在启用且未删除的目标账号：跳过且不覆盖密码。
                return null;
            }
            // 禁止通过引导把已被占用的账号自动提权。
            throw new BusinessException(
                    CommonErrorCode.CONFLICT,
                    "引导账号的用户名已被其他账号占用"
            );
        }

        // 再按邮箱精确查重：邮箱被其他账号占用时显式报错。
        // 这里不能静默跳过——邮箱被占会导致引导账号建不出来，
        // 而数据范围授予链的信任根缺失却无人察觉。
        UserDO existingByEmail = baseMapper.selectByEmail(email);
        if (existingByEmail != null) {
            throw new BusinessException(
                    CommonErrorCode.CONFLICT,
                    "引导账号的邮箱已被其他账号占用"
            );
        }

        // 组装引导账号，除角色外与普通注册保持一致。
        UserDO userDO = new UserDO();
        userDO.setUsername(username);
        userDO.setEmail(email);
        userDO.setRoleCode(roleCode);
        userDO.setBalance(DEFAULT_BALANCE);
        userDO.setPasswordHash(passwordEncoder.encode(rawPassword));
        userDO.setStatus(USER_STATUS_ENABLED);
        userDO.setVersion(0);

        LocalDateTime now = LocalDateTime.now();
        userDO.setCreatedAt(now);
        userDO.setUpdatedAt(now);

        try {
            // 保存引导账号。
            save(userDO);
        } catch (DuplicateKeyException exception) {
            // 并发引导时由唯一索引兜底，视为账号已存在。
            return null;
        }

        return userDO;
    }

    /**
     * 将用户持久化对象转换为用户基础信息。
     *
     * @param userDO 用户持久化对象
     * @return 用户基础信息
     */
    private UserBasicVO buildUserBasicVO(UserDO userDO) {
        // 只返回用户 ID、用户名和角色编码。
        return UserBasicVO.builder()
                .userId(userDO.getUserId())
                .username(userDO.getUsername())
                .roleCode(userDO.getRoleCode())
                .build();
    }
}

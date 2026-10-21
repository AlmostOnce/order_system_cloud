package com.hbue.ordering.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hbue.ordering.user.api.dto.UserPasswordVerifyCommand;
import com.hbue.ordering.user.api.dto.UserRegisterCommand;
import com.hbue.ordering.user.api.vo.UserBasicVO;
import com.hbue.ordering.user.model.UserDO;

/**
 * 用户业务服务契约。
 *
 * <p>负责维护 user-service 所属的用户账户数据，包括注册、账号认证、角色和
 * 账号状态管理及启动期引导。密码哈希只在本服务持久化；跨服务调用通过 API
 * 契约传输必要的用户信息，不允许其他服务直接访问 users 表。</p>
 *
 * @author order-system
 * @date 2026-09-20
 */
public interface UserService extends IService<UserDO> {

    /**
     * 根据用户 ID 查询用户基础信息。
     *
     * @param userId 用户 ID
     * @return 用户基础信息
     * @throws com.hbue.ordering.common.core.exception.BusinessException 用户不存在、已停用或已删除时抛出
     */
    UserBasicVO getUserById(Long userId);

    /**
     * 注册用户。
     *
     * @param command 用户注册命令
     * @return 注册成功的用户基础信息
     * @throws com.hbue.ordering.common.core.exception.BusinessException 用户名或手机号冲突时抛出
     */
    UserBasicVO register(UserRegisterCommand command);

    /**
     * 校验用户账号密码。
     *
     * @param command 用户密码校验命令
     * @return 校验成功的用户基础信息
     * @throws com.hbue.ordering.common.core.exception.BusinessException 用户不存在、状态不可用或密码不匹配时统一抛出认证失败
     */
    UserBasicVO authenticate(UserPasswordVerifyCommand command);

    /**
     * 安全引导系统管理员账号。
     *
     * <p>已存在启用且未删除的 {@code SYSTEM_ADMIN} 时跳过且不做任何修改，
     * 避免环境变量覆盖已有账号；用户名或邮箱被其他账号占用时拒绝引导。</p>
     *
     * @param username 管理员用户名
     * @param email 管理员邮箱
     * @param rawPassword 管理员明文密码
     * @return 是否实际创建了新账号
     * @throws com.hbue.ordering.common.core.exception.BusinessException 用户名或邮箱被其他账号占用时抛出
     */
    boolean bootstrapSystemAdmin(
            String username,
            String email,
            String rawPassword
    );

    /**
     * 安全引导初始总经理账号，并写入其全平台数据范围。
     *
     * <p>总经理是数据范围授予链的根（见角色权限设计 §3.5），必须带外产生。
     * 账号与其 {@code PLATFORM} 范围在同一本地事务内提交，避免出现
     * "有总经理但没有范围"的半个状态。</p>
     *
     * @param username 总经理用户名
     * @param email 总经理邮箱
     * @param rawPassword 总经理明文密码
     * @return 是否实际创建了新账号
     * @throws com.hbue.ordering.common.core.exception.BusinessException 账号冲突或全平台范围写入失败时抛出，事务内账号创建随之回滚
     */
    boolean bootstrapGeneralManager(
            String username,
            String email,
            String rawPassword
    );

    /**
     * 修改指定用户的角色编码。
     *
     * <p>角色分配是技术角色的职责，只写 {@code users.role_code}，不涉及数据范围。
     * 受硬约束①保护：禁止修改本人角色编码（自我提权），对外表现为 403；
     * 目标角色必须是 {@link com.hbue.ordering.common.core.role.RoleCode}
     * 中已定义的编码，否则返回 400；目标用户不存在返回 404。</p>
     *
     * @param operatorId 操作者用户 ID
     * @param targetUserId 目标用户 ID
     * @param roleCode 目标角色编码
     * @return 变更后的用户基础信息
     * @throws com.hbue.ordering.common.core.exception.BusinessException 操作者无效、修改本人角色、目标用户不存在或角色编码无效时抛出
     */
    UserBasicVO assignRole(
            Long operatorId,
            Long targetUserId,
            String roleCode
    );

    /**
     * 启用或禁用指定账号。
     *
     * <p>账号生命周期是技术角色的职责（角色权限设计 §3.3），只改
     * {@code users.status}，不触碰业务数据。受硬约束③保护：禁止禁用
     * **最后一个处于启用状态的** {@code SYSTEM_ADMIN}（对外表现为 409），
     * 避免系统进入"无人可管理"的死局。目标用户不存在返回 404，
     * 目标状态不是 0/1 返回 400。</p>
     *
     * @param operatorId 操作者用户 ID
     * @param targetUserId 目标用户 ID
     * @param status 目标状态：1 启用，0 禁用
     * @throws com.hbue.ordering.common.core.exception.BusinessException 目标状态无效、目标用户不存在或禁用最后一个启用的系统管理员时抛出
     */
    void updateUserStatus(
            Long operatorId,
            Long targetUserId,
            Integer status
    );
}

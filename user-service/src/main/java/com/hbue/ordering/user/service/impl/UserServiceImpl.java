package com.hbue.ordering.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.user.api.dto.UserPasswordVerifyCommand;
import com.hbue.ordering.user.api.dto.UserRegisterCommand;
import com.hbue.ordering.user.api.vo.UserBasicVO;
import com.hbue.ordering.user.mapper.UserMapper;
import com.hbue.ordering.user.model.UserDO;
import com.hbue.ordering.user.service.UserService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户业务服务实现。
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
     * 默认用户角色。
     */
    private static final String DEFAULT_ROLE_CODE = "CUSTOMER";

    /**
     * 默认账户余额。
     */
    private static final BigDecimal DEFAULT_BALANCE = BigDecimal.ZERO;

    private final PasswordEncoder passwordEncoder;

    /**
     * 创建用户业务服务。
     *
     * @param passwordEncoder 密码加密器
     */
    public UserServiceImpl(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 根据用户 ID 查询用户基础信息。
     *
     * @param userId 用户 ID
     * @return 用户基础信息
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
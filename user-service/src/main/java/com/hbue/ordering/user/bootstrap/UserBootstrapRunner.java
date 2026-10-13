package com.hbue.ordering.user.bootstrap;

import com.hbue.ordering.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 用户账号安全引导。
 *
 * <p>服务启动时读取显式环境变量，在总开关
 * {@code USER_ADMIN_BOOTSTRAP_ENABLED=true} 时创建两个带外账号：</p>
 *
 * <ul>
 *     <li>{@code SYSTEM_ADMIN}（前缀 {@code USER_ADMIN_BOOTSTRAP_*}）：管账号与角色；</li>
 *     <li>{@code GENERAL_MANAGER}（前缀 {@code USER_MANAGER_BOOTSTRAP_*}）：
 *         数据范围授予链的根，创建时一并写入全平台范围。</li>
 * </ul>
 *
 * <p>两个账号各自独立判断：已存在启用且未删除的目标账号时跳过且不覆盖密码。
 * 开关关闭时完全不执行，也不要求提供任何引导配置。普通注册仍固定为
 * {@code CUSTOMER}，不受本引导影响。</p>
 *
 * @author order-system
 * @date 2026-10-13
 */
@Component
public class UserBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UserBootstrapRunner.class);

    /** 引导密码最小长度，避免弱口令账号进入系统。 */
    private static final int MIN_PASSWORD_LENGTH = 12;

    /** 引导密码最大长度。 */
    private static final int MAX_PASSWORD_LENGTH = 64;

    /** 用户名最小长度，沿用 users.username 的可用长度下界。 */
    private static final int MIN_USERNAME_LENGTH = 2;

    /** 用户名最大长度，沿用用户表上限。 */
    private static final int MAX_USERNAME_LENGTH = 50;

    /** 中国大陆 11 位手机号格式。 */
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("^1[3-9]\\d{9}$");

    /** 系统管理员引导变量前缀。 */
    private static final String ADMIN_PREFIX = "USER_ADMIN_BOOTSTRAP";

    /** 初始总经理引导变量前缀。 */
    private static final String MANAGER_PREFIX = "USER_MANAGER_BOOTSTRAP";

    private final UserService userService;

    /** 引导总开关。 */
    private final boolean enabled;

    /** 系统管理员引导用户名。 */
    private final String adminUsername;

    /** 系统管理员引导手机号。 */
    private final String adminPhone;

    /** 系统管理员引导明文密码。 */
    private final String adminPassword;

    /** 初始总经理引导用户名。 */
    private final String managerUsername;

    /** 初始总经理引导手机号。 */
    private final String managerPhone;

    /** 初始总经理引导明文密码。 */
    private final String managerPassword;

    /**
     * 创建用户引导组件。
     *
     * @param userService 用户业务服务
     * @param enabled 引导总开关
     * @param adminUsername 系统管理员用户名环境变量
     * @param adminPhone 系统管理员手机号环境变量
     * @param adminPassword 系统管理员密码环境变量
     * @param managerUsername 初始总经理用户名环境变量
     * @param managerPhone 初始总经理手机号环境变量
     * @param managerPassword 初始总经理密码环境变量
     */
    public UserBootstrapRunner(
            UserService userService,
            @Value("${USER_ADMIN_BOOTSTRAP_ENABLED:false}") boolean enabled,
            @Value("${USER_ADMIN_BOOTSTRAP_USERNAME:}") String adminUsername,
            @Value("${USER_ADMIN_BOOTSTRAP_PHONE:}") String adminPhone,
            @Value("${USER_ADMIN_BOOTSTRAP_PASSWORD:}") String adminPassword,
            @Value("${USER_MANAGER_BOOTSTRAP_USERNAME:}") String managerUsername,
            @Value("${USER_MANAGER_BOOTSTRAP_PHONE:}") String managerPhone,
            @Value("${USER_MANAGER_BOOTSTRAP_PASSWORD:}") String managerPassword
    ) {
        this.userService = userService;
        this.enabled = enabled;
        this.adminUsername = adminUsername;
        this.adminPhone = adminPhone;
        this.adminPassword = adminPassword;
        this.managerUsername = managerUsername;
        this.managerPhone = managerPhone;
        this.managerPassword = managerPassword;
    }

    /**
     * 应用启动完成后执行用户引导。
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        // 未开启时不执行任何动作，也不要求提供引导配置。
        if (!enabled) {
            log.info("未开启 USER_ADMIN_BOOTSTRAP_ENABLED，跳过用户账号引导");
            return;
        }

        // 配置缺失或格式不合法时启动失败，且日志不包含任何凭据。
        validateConfig(ADMIN_PREFIX, adminUsername, adminPhone, adminPassword);
        validateConfig(MANAGER_PREFIX, managerUsername, managerPhone, managerPassword);

        bootstrapSystemAdmin();
        bootstrapGeneralManager();
    }

    /**
     * 引导系统管理员账号。
     */
    private void bootstrapSystemAdmin() {
        boolean created = userService.bootstrapSystemAdmin(
                adminUsername,
                adminPhone,
                adminPassword
        );
        if (created) {
            log.info("系统管理员账号 [{}] 引导创建成功", adminUsername);
        } else {
            log.info("系统管理员账号 [{}] 已存在，本次引导未做任何修改", adminUsername);
        }
    }

    /**
     * 引导初始总经理账号及其全平台数据范围。
     */
    private void bootstrapGeneralManager() {
        boolean created = userService.bootstrapGeneralManager(
                managerUsername,
                managerPhone,
                managerPassword
        );
        if (created) {
            log.info("初始总经理账号 [{}] 及其全平台范围引导创建成功", managerUsername);
        } else {
            log.info("初始总经理账号 [{}] 已存在，本次引导未做任何修改", managerUsername);
        }
    }

    /**
     * 校验一组引导配置是否齐全且格式合法。
     *
     * @param prefix 变量前缀
     * @param username 引导用户名
     * @param phone 引导手机号
     * @param password 引导明文密码
     */
    private void validateConfig(
            String prefix,
            String username,
            String phone,
            String password
    ) {
        requirePresent(prefix, "USERNAME", username);
        requirePresent(prefix, "PHONE", phone);
        requirePresent(prefix, "PASSWORD", password);

        int usernameLength = username.length();
        if (usernameLength < MIN_USERNAME_LENGTH
                || usernameLength > MAX_USERNAME_LENGTH) {
            throw new IllegalStateException(
                    prefix + "_USERNAME 长度必须在 "
                            + MIN_USERNAME_LENGTH + "-"
                            + MAX_USERNAME_LENGTH + " 个字符之间"
            );
        }

        if (!PHONE_PATTERN.matcher(phone).matches()) {
            throw new IllegalStateException(
                    prefix + "_PHONE 必须是 11 位中国大陆手机号"
            );
        }

        int passwordLength = password.length();
        if (passwordLength < MIN_PASSWORD_LENGTH
                || passwordLength > MAX_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    prefix + "_PASSWORD 长度必须在 "
                            + MIN_PASSWORD_LENGTH + "-"
                            + MAX_PASSWORD_LENGTH + " 个字符之间"
            );
        }
    }

    /**
     * 校验引导变量已提供。
     *
     * @param prefix 变量前缀
     * @param name 变量名称（不含前缀）
     * @param value 变量值
     */
    private void requirePresent(String prefix, String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "开启用户引导时必须提供 " + prefix + "_" + name
            );
        }
    }
}

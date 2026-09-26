package com.hbue.ordering.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hbue.ordering.user.api.dto.UserPasswordVerifyCommand;
import com.hbue.ordering.user.api.dto.UserRegisterCommand;
import com.hbue.ordering.user.api.vo.UserBasicVO;
import com.hbue.ordering.user.model.UserDO;

/**
 * 用户业务服务。
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
     */
    UserBasicVO getUserById(Long userId);

    /**
     * 注册用户。
     *
     * @param command 用户注册命令
     * @return 注册成功的用户基础信息
     */
    UserBasicVO register(UserRegisterCommand command);

    /**
     * 校验用户账号密码。
     *
     * @param command 用户密码校验命令
     * @return 校验成功的用户基础信息
     */
    UserBasicVO authenticate(UserPasswordVerifyCommand command);
}
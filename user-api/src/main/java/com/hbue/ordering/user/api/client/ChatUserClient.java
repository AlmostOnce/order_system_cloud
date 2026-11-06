package com.hbue.ordering.user.api.client;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.user.api.vo.UserBasicVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
/** 聊天读取当前账号基础资料的内部契约。角色从用户服务回源获取，不信任客户端传入的商家身份。 */
@FeignClient(name="user-service", contextId="notificationChatUser")
public interface ChatUserClient {
    /** 复用现有基础资料内部接口，查询实时角色、公开身份和邮箱验证状态；显式携带内部密钥。 */
    @GetMapping("/internal/users/{id}/basic")
    ApiResponse<UserBasicVO> basic(@PathVariable("id") Long id, @RequestHeader("X-Internal-Token") String token);
}

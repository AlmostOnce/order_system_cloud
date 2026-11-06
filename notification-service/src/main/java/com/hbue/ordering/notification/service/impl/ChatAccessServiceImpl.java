package com.hbue.ordering.notification.service.impl;
import com.hbue.ordering.notification.service.ChatAccessService;
import com.hbue.ordering.order.api.client.OrderChatClient;
import com.hbue.ordering.order.api.vo.OrderChatContextVO;
import com.hbue.ordering.user.api.client.ChatUserClient;
import com.hbue.ordering.user.api.client.UserScopeInternalClient;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.response.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.List;
/**
 * 聊天授权事实查询。每次回源用户服务确认当前角色与直接窗口授权，不复用平台管理范围替代店主身份。
 * 订单服务提供归属、窗口及完成时间；任一依赖故障都拒绝本次受保护操作，不降级放行。
 */
@Service
public class ChatAccessServiceImpl implements ChatAccessService {
    private final OrderChatClient orders;
    private final ChatUserClient users;
    private final UserScopeInternalClient scopes;
    private final String token;
    /** 注入订单事实、用户资料和直接经营范围客户端；内部密钥只从服务配置获取，不能由外部请求覆盖。 */
    public ChatAccessServiceImpl(OrderChatClient orders,ChatUserClient users,UserScopeInternalClient scopes,@Value("${internal.scope.token}") String token){
        this.orders=orders;
        this.users=users;
        this.scopes=scopes;
        this.token=token;
    }
    /**
     * 统一处理内部服务响应。只接受明确成功且有数据的事实；网络错误、降级响应和空结果都不能解释为授权成功。
     */
    private <T> T result(java.util.function.Supplier<ApiResponse<T>> call) {
        try {
            var response=call.get();
            if(response!=null && "COMMON_000".equals(response.getCode()) && response.getData()!=null)return response.getData();
        }
        catch(RuntimeException e){
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE,"聊天权限暂时无法确认");
        }
        throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE,"聊天权限暂时无法确认");
    }
    /**
     * 回源用户服务解析实际角色与窗口范围。顾客只能访问本人订单；店主只使用直接窗口授权，其他管理角色拒绝。
     */
    public Actor actor(long id){
        var u=result(()->users.basic(id,token));
        if(u.getUserId()==null || u.getUserId()!=id || !u.isEmailVerified()) throw new BusinessException(CommonErrorCode.FORBIDDEN,"当前账号不能使用聊天");
        if("CUSTOMER".equals(u.getRoleCode()))return new Actor(id,u.getUserUuid(),u.getUsername(),u.getRoleCode(),List.of());
        if(!"STALL_MANAGER".equals(u.getRoleCode()))throw new BusinessException(CommonErrorCode.FORBIDDEN,"仅顾客与窗口店主可使用聊天");
        // 只采用直接窗口列表，故意不展开餐厅范围和全平台权限。
        var s=result(()->scopes.getScopes(id,token));
        if(s.getUserId()==null || s.getUserId()!=id)throw new BusinessException(CommonErrorCode.FORBIDDEN,"无法确认经营范围");
        return new Actor(id,u.getUserUuid(),u.getUsername(),u.getRoleCode(),s.getWindowIds()==null?List.of():List.copyOf(s.getWindowIds()));
    }
    /**
     * 从订单服务读取真实沟通上下文。内部共享密钥不来自客户端，失败时不使用本地猜测的支付状态。
     */
    public OrderChatContextVO order(long id){
        return result(()->orders.context(id,token));
    }
    /**
     * 校验订单顾客归属或店主直接窗口权限。资源不存在与越权统一为不可见，避免借会话标识枚举他人订单。
     */
    public void check(Actor a,OrderChatContextVO o){
        boolean allowed=a.customer()? Long.valueOf(a.id()).equals(o.customerId()):"STALL_MANAGER".equals(a.role()) && a.windows().contains(o.windowId());
        if(!allowed)throw new BusinessException(CommonErrorCode.NOT_FOUND,"会话不存在");
    }
    /**
     * 判断订单是否仍允许发送。已支付、制作中、待取货开放；已完成按UTC+8完成时间加24小时，其他状态只读。
     */
    public boolean canSend(OrderChatContextVO o){
        if(List.of("已支付","制作中","待取货").contains(o.status()))return true;
        // 完成时间来自订单核销，不使用打开页面或最后一次聊天的时间续期。
        return "已完成".equals(o.status()) && o.completedAt()!=null &&
        LocalDateTime.now(ZoneId.of("Asia/Shanghai")).isBefore(o.completedAt().plusHours(24));
    }
}

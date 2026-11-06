package com.hbue.ordering.notification.service;
import com.hbue.ordering.order.api.vo.OrderChatContextVO;
import java.util.List;
/**
 * 聊天权限与订单状态判定边界。存储服务依赖此接口，避免Mapper承担远程授权或业务状态判断。
 */
public interface ChatAccessService {
    /** 由用户服务事实构建的操作者快照；windows只包含直接授权的窗口，不能把前端传入的范围放入此对象。 */
    record Actor(long id,String uuid,String name,String role,List<Long> windows) {
        /** 顾客走订单归属校验；非顾客仍需明确验证店主角色及窗口范围。 */
        public boolean customer(){
            return "CUSTOMER".equals(role);
        }
    }
    /**
     * 回源用户服务解析实际角色与窗口范围。顾客只能访问本人订单；店主只使用直接窗口授权，其他管理角色拒绝。
     */
    Actor actor(long userId);
    /**
     * 从订单服务读取真实沟通上下文。内部共享密钥不来自客户端，失败时不使用本地猜测的支付状态。
     */
    OrderChatContextVO order(long orderId);
    /**
     * 校验订单顾客归属或店主直接窗口权限。资源不存在与越权统一为不可见，避免借会话标识枚举他人订单。
     */
    void check(Actor actor,OrderChatContextVO order);
    /**
     * 判断订单是否仍允许发送。已支付、制作中、待取货开放；已完成按UTC+8完成时间加24小时，其他状态只读。
     */
    boolean canSend(OrderChatContextVO order);
}

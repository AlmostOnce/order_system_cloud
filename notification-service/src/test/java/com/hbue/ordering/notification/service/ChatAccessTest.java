package com.hbue.ordering.notification.service;
import com.hbue.ordering.notification.service.impl.ChatAccessServiceImpl;
import com.hbue.ordering.order.api.vo.OrderChatContextVO;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
/** 聊天权限纯逻辑测试：验证订单状态、完成期限、顾客归属与直接窗口授权，不依赖真实远程服务。 */
class ChatAccessTest {
    final ChatAccessService service=new ChatAccessServiceImpl(null,null,null,"test");
    OrderChatContextVO order(String status,int hours){
        return new OrderChatContextVO(1L,"O1",7L,3L,"店铺",status,LocalDateTime.now(ZoneId.of("Asia/Shanghai")).minusHours(hours));
    }
    /** 允许支付后沟通与完成后24小时内沟通，拒绝待支付、取消和超时订单。 */
    @Test void paidAndRecentCompletionCanSend(){
        assertTrue(service.canSend(order("已支付",0)));
        assertTrue(service.canSend(order("已完成",23)));
        assertFalse(service.canSend(order("已完成",25)));
        assertFalse(service.canSend(order("待支付",0)));
        assertFalse(service.canSend(order("已取消",0)));
    }
    /** 知道会话或订单编号不等于拥有访问权限，其他顾客必须被拒绝。 */
    @Test void otherCustomerCannotRead(){
        assertThrows(RuntimeException.class,()->service.check(new ChatAccessService.Actor(8,"uuid","顾客","CUSTOMER",List.of()),order("已支付",0)));
    }
    /** 店主必须有订单窗口的直接授权，不能用无关窗口或其他管理角色替代。 */
    @Test void merchantMustHaveExplicitWindow(){
        assertThrows(RuntimeException.class,()->service.check(new ChatAccessService.Actor(8,"uuid","店主","STALL_MANAGER",List.of(4L)),order("已支付",0)));
        assertThrows(RuntimeException.class,()->service.check(new ChatAccessService.Actor(8,"uuid","管理员","ADMIN",List.of(3L)),order("已支付",0)));
        assertDoesNotThrow(()->service.check(new ChatAccessService.Actor(8,"uuid","店主","STALL_MANAGER",List.of(3L)),order("已支付",0)));
    }
}

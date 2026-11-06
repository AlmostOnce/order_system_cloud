package com.hbue.ordering.order.controller;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.order.dto.request.*;
import com.hbue.ordering.order.service.OrderRefundService;
import com.hbue.ordering.order.vo.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
/** 学生退款与管理审批共享订单模块；角色来自已验签JWT，所有数据范围由Service再次验证。 */
@RestController @Validated @RequestMapping("/orders")
public class OrderRefundController {
    private final OrderRefundService refunds;
    public OrderRefundController(OrderRefundService refunds) {this.refunds=refunds;}
    /** 学生身份从已验签主体取得，只接收明细/数量/原因及稳定幂等键，不采信客户端用户号或金额。 */
    @PostMapping("/{orderId}/refunds") @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<RefundVO> apply(@AuthenticationPrincipal Jwt jwt,@PathVariable("orderId") @Positive Long orderId,
            @RequestHeader("Idempotency-Key") String key,@Valid @RequestBody RefundApplyRequest request) {
        return ApiResponse.success(refunds.apply(user(jwt),orderId,key,request));
    }
    /** 本人退款历史使用主键游标，Service在SQL前校验订单与游标归属。 */
    @GetMapping("/{orderId}/refunds") @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<RefundPageVO> list(@AuthenticationPrincipal Jwt jwt,@PathVariable("orderId") @Positive Long orderId,
            @RequestParam(name="cursor",required=false) String cursor) {return ApiResponse.success(refunds.listOwn(user(jwt),orderId,cursor));}
    /** 升级争议不允许填写审批人，权限内经理从管理队列处理。 */
    @PostMapping("/{orderId}/refunds/{refundId}/appeal") @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<RefundVO> appeal(@AuthenticationPrincipal Jwt jwt,@PathVariable("orderId") @Positive Long orderId,
            @PathVariable("refundId") @Positive Long refundId) {return ApiResponse.success(refunds.appeal(user(jwt),orderId,refundId));}
    /** 撤回只针对尚未批准的本人申请，不能逆转已经开始的资金入账。 */
    @PostMapping("/{orderId}/refunds/{refundId}/withdraw") @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<RefundVO> withdraw(@AuthenticationPrincipal Jwt jwt,@PathVariable("orderId") @Positive Long orderId,
            @PathVariable("refundId") @Positive Long refundId) {return ApiResponse.success(refunds.withdraw(user(jwt),orderId,refundId));}
    /** 店主仅看到待审批；经理看到介入争议，总经理可不指定窗口查看全部争议。 */
    @GetMapping("/refunds/manage") @PreAuthorize("hasAnyRole('STALL_MANAGER','RESTAURANT_MANAGER','GENERAL_MANAGER')")
    public ApiResponse<RefundPageVO> queue(@AuthenticationPrincipal Jwt jwt,@RequestParam(name="windowId",required=false) Long windowId,
            @RequestParam(name="cursor",required=false) String cursor) {
        boolean manager=role(jwt,"RESTAURANT_MANAGER") || role(jwt,"GENERAL_MANAGER");
        return ApiResponse.success(refunds.queue(user(jwt),role(jwt,"GENERAL_MANAGER"),manager,windowId,cursor));
    }
    /** 角色门禁和数据范围同时生效；管理操作仅提供API，不接入学生页面。 */
    @PostMapping("/refunds/{refundId}/decision") @PreAuthorize("hasAnyRole('STALL_MANAGER','RESTAURANT_MANAGER','GENERAL_MANAGER')")
    public ApiResponse<RefundVO> decide(@AuthenticationPrincipal Jwt jwt,@PathVariable("refundId") @Positive Long refundId,
            @Valid @RequestBody RefundDecisionRequest request) {
        return ApiResponse.success(refunds.decide(user(jwt),role(jwt,"RESTAURANT_MANAGER") || role(jwt,"GENERAL_MANAGER"),role(jwt,"GENERAL_MANAGER"),refundId,request));
    }
    /** 公共JWT转换器已把外部UUID转换为内部数字主体，不能直接使用客户端请求字段。 */
    private Long user(Jwt jwt) {return Long.valueOf(jwt.getSubject());}
    /** roles来自验签后的JWT，与现有资源服务器ROLE_映射保持一致。 */
    private boolean role(Jwt jwt,String role) {return jwt.getClaimAsStringList("roles")!=null && jwt.getClaimAsStringList("roles").contains(role);}
}

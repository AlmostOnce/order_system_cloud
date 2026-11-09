package com.hbue.ordering.notification.controller;
import com.hbue.ordering.notification.service.ChatService;
import com.hbue.ordering.notification.vo.*;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
/**
 * 聊天HTTP入口：创建会话、会话分页、历史补拉、阅读进度及未读数量。
 * 接口只从已认证JWT读取操作者；聊天开关关闭时不注册路由，迁移前不会查询新表。
 */
@RestController
@RequestMapping("/chat")
@ConditionalOnProperty(name="notification.chat.enabled",havingValue="true")
public class ChatController {
    private final ChatService chats;
    /** 注入聊天业务接口，控制器只负责请求绑定及认证身份提取，不直接操作数据库。 */
    public ChatController(ChatService chats){
        this.chats=chats;
    }
    /** 创建请求只接受订单主键；顾客身份和经营范围由认证上下文确定，不能由请求体指定。 */
    public record OpenRequest(Long orderId){
    }
    /** 阅读水位为本页实际展示到的服务端消息序号，不是消息条数或设备时间。 */
    public record ReadRequest(Long lastReadSeq){
    }
    /**
     * 显式点击联系商家后获取或创建会话。先验证真实订单与用户归属，再依赖订单唯一索引解决并发；支付本身不触发创建。
     */
    @PostMapping("/conversations")
    public ApiResponse<ChatConversationVO> open(@AuthenticationPrincipal Jwt jwt,@RequestBody OpenRequest body){
        if(body.orderId()==null)throw new BusinessException(CommonErrorCode.BAD_REQUEST,"订单编号不能为空");
        return ApiResponse.success(chats.open(Long.parseLong(jwt.getSubject()),body.orderId()));
    }
    /**
     * 按当前用户可访问范围查询游标页。游标属于其他用户时拒绝；额外取一条判断下一页，避免加载全部记录。
     */
    @GetMapping("/conversations")
    public ApiResponse<ChatPageVO<ChatConversationVO>> page(@AuthenticationPrincipal Jwt jwt,@RequestParam(name="cursor",required=false) String cursor,@RequestParam(name="size",defaultValue="20") int size){
        return ApiResponse.success(chats.page(Long.parseLong(jwt.getSubject()),cursor,size));
    }
    /**
     * 读取授权会话及当前可发送状态。历史只读与是否能继续发送分别判断，订单完成不删除已有聊天。
     */
    @GetMapping("/conversations/{uuid}")
    public ApiResponse<ChatConversationVO> detail(@AuthenticationPrincipal Jwt jwt,@PathVariable("uuid") String uuid){
        return ApiResponse.success(chats.detail(Long.parseLong(jwt.getSubject()),uuid));
    }
    /** 查询已授权会话历史；beforeSeq与afterSeq互斥，不传时读取最近一页。每页默认30条，上限50条。 */
    @GetMapping("/conversations/{uuid}/messages")
    public ApiResponse<ChatPageVO<ChatMessageVO>> history(@AuthenticationPrincipal Jwt jwt,@PathVariable("uuid") String uuid,
    @RequestParam(name="beforeSeq",required=false) Long before,@RequestParam(name="afterSeq",required=false) Long after,@RequestParam(name="size",defaultValue="30") int size){
        return ApiResponse.success(chats.messages(Long.parseLong(jwt.getSubject()),uuid,before,after,size));
    }
    /**
     * 提交本人实际阅读位置。水位不能超过已提交最大序号，SQL用GREATEST保证并发请求不会把已读位置回退。
     */
    @PostMapping("/conversations/{uuid}/read")
    public ApiResponse<Void> read(@AuthenticationPrincipal Jwt jwt,@PathVariable("uuid") String uuid,@RequestBody ReadRequest body){
        if(body.lastReadSeq()==null)throw new BusinessException(CommonErrorCode.BAD_REQUEST,"已读位置不能为空");
        chats.read(Long.parseLong(jwt.getSubject()),uuid,body.lastReadSeq());
        return ApiResponse.success(null);
    }
    /**
     * 查询本人当前可见会话的未读总量。店主无直接窗口授权时返回零，不把平台或餐厅范围展开成私人会话。
     */
    @GetMapping("/unread-count")
    public ApiResponse<Long> unread(@AuthenticationPrincipal Jwt jwt){
        return ApiResponse.success(chats.unread(Long.parseLong(jwt.getSubject())));
    }
    /** 撤回只接收资源标识，操作者来自JWT，不接受客户端发送人或撤回时间。 */
    @PostMapping("/conversations/{uuid}/messages/{messageUuid}/revoke")
    public ApiResponse<ChatMessageVO> revoke(@AuthenticationPrincipal Jwt jwt,@PathVariable("uuid") String uuid,@PathVariable("messageUuid") String messageUuid){
        return ApiResponse.success(chats.revoke(Long.parseLong(jwt.getSubject()),uuid,messageUuid));
    }
    /** 撤回状态批量查询体；Service限制50条并逐个校验UUID格式。 */
    public record MessageIdsRequest(java.util.List<String> messageUuids) {}
    /** 批量状态查询仍校验会话权限，本地UUID列表不代表访问授权。 */
    @PostMapping("/conversations/{uuid}/messages/revoked")
    public ApiResponse<java.util.List<ChatMessageVO>> revoked(@AuthenticationPrincipal Jwt jwt,@PathVariable("uuid") String uuid,@RequestBody MessageIdsRequest body){
        return ApiResponse.success(chats.revoked(Long.parseLong(jwt.getSubject()),uuid,body.messageUuids()));
    }
}

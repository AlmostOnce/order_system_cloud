package com.hbue.ordering.notification.service.impl;
import com.hbue.ordering.notification.service.*;
import com.hbue.ordering.notification.mapper.*;
import com.hbue.ordering.notification.model.*;
import com.hbue.ordering.notification.dto.ChatSendRequest;
import com.hbue.ordering.notification.vo.*;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.redis.RedisOperationsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.util.*;
/**
 * 订单聊天业务实现。负责会话创建、消息幂等、会话内序号、阅读水位和事务事件；所有用户身份来自认证结果。
 * 消息、会话摘要和outbox在同一个本地事务内提交，WebSocket成功不等于对方已读。
 */
@Service
public class ChatServiceImpl implements ChatService {
    private final ChatConversationMapper conversations;
    private final ChatMessageMapper messages;
    private final ChatOutboxMapper outbox;
    private final ChatAccessService access;
    private final RedisOperationsService redis;
    /** 注入会话、消息和事件Mapper、授权服务及公共Redis组件；由Spring事务代理统一保护发送与创建操作。 */
    public ChatServiceImpl(ChatConversationMapper c,ChatMessageMapper m,ChatOutboxMapper o,ChatAccessService a,RedisOperationsService r){
        conversations=c;
        messages=m;
        outbox=o;
        access=a;
        redis=r;
    }
    /**
     * 校验公开标识为规范UUID v4。先拒绝空值和错误格式，避免把任意客户端字符串当作可信资源标识。
     */
    private void uuid(String id){
        if(id==null || !id.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))bad("会话或消息标识不正确");
    }
    /**
     * 将输入校验失败转换为项目统一业务异常，交由HTTP或WebSocket边界输出安全提示。
     */
    private void bad(String text){
        throw new BusinessException(CommonErrorCode.BAD_REQUEST,text);
    }
    /**
     * 读取会话并再次核验当前用户权限。Mapper查到数据不表示用户有权查看，必须经过角色及订单归属检查。
     */
    private ChatConversationDO require(long uid,String uuid){
        uuid(uuid);
        var row=conversations.find(uuid);
        if(row==null)throw new BusinessException(CommonErrorCode.NOT_FOUND,"会话不存在");
        access.check(access.actor(uid),access.order(row.getOrderId()));
        return row;
    }
    /**
     * 将内部会话映射成公开摘要，并查询当前用户阅读水位之后的对方消息数；不把自己的发送计入未读。
     */
    private ChatConversationVO view(long uid,ChatConversationDO c,boolean canSend,boolean receipt){
        return new ChatConversationVO(c.getConversationUuid(),c.getOrderId(),c.getOrderNo(),c.getWindowName(),c.getLastContent(),
        c.getLastMessageAt()==null?c.getCreatedAt():c.getLastMessageAt(),c.getLastSeq(),messages.unread(c.getId(),uid),canSend,
        // 列表不额外查对方阅读水位；详情随已有chat.changed补查更新，无需逐消息查数据库。
        receipt?messages.peerReadSeq(c.getId(),c.getCustomerId(),uid==c.getCustomerId()):null);
    }
    /**
     * 在当前业务事务内写入轻量outbox。此处不直接推Socket，防止事务回滚后客户端收到不存在的消息。
     */
    private void event(String uuid){
        var e=new ChatOutboxDO();
        e.setEventUuid(UUID.randomUUID().toString());
        e.setConversationUuid(uuid);
        e.setAttempts(0L);
        outbox.insert(e);
    }
    /**
     * 显式点击联系商家后获取或创建会话。先验证真实订单与用户归属，再依赖订单唯一索引解决并发；支付本身不触发创建。
     */
    @Transactional
    public ChatConversationVO open(long uid,long orderId){
        if(orderId<=0)bad("订单编号不正确");
        var actor=access.actor(uid);
        var order=access.order(orderId);
        access.check(actor,order);
        // 支付后仍不预建；只有此显式入口执行，重复点击优先返回原会话。
        var old=conversations.byOrder(orderId);
        if(old!=null)return view(uid,old,access.canSend(order),false);
        if(!actor.customer())throw new BusinessException(CommonErrorCode.FORBIDDEN,"请等待顾客发起会话");
        if(!access.canSend(order))throw new BusinessException(CommonErrorCode.CONFLICT,"该订单暂不支持发起沟通");
        var c=new ChatConversationDO();
        c.setConversationUuid(UUID.randomUUID().toString());
        c.setOrderId(orderId);
        c.setOrderNo(order.orderNo());
        c.setCustomerId(order.customerId());
        c.setWindowId(order.windowId());
        c.setWindowName(order.windowName()==null?"店铺":order.windowName());
        // 并发插入由订单唯一键兜底，随后必须当前读，不能沿用之前的快照。
        conversations.create(c);
        var saved=conversations.byOrderCurrent(orderId);
        event(saved.getConversationUuid());
        return view(uid,saved,true,false);
    }
    /**
     * 读取授权会话及当前可发送状态。历史只读与是否能继续发送分别判断，订单完成不删除已有聊天。
     */
    public ChatConversationVO detail(long uid,String uuid){
        var c=require(uid,uuid);
        return view(uid,c,access.canSend(access.order(c.getOrderId())),true);
    }
    /**
     * 按当前用户可访问范围查询游标页。游标属于其他用户时拒绝；额外取一条判断下一页，避免加载全部记录。
     */
    public ChatPageVO<ChatConversationVO> page(long uid,String cursor,int size){
        if(size<1||size>50)bad("每页数量应为1至50");
        if(cursor!=null)require(uid,cursor);
        var actor=access.actor(uid);
        if(!actor.customer()&&actor.windows().isEmpty())return new ChatPageVO<>(List.of(),null,false);
        var rows=conversations.page(uid,actor.customer(),actor.windows(),cursor,size+1);
        boolean more=rows.size()>size;
        var items=rows.stream().limit(size).map(c->view(uid,c,false,false)).toList();
        return new ChatPageVO<>(items,more?items.getLast().conversationUuid():null,more);
    }
    /**
     * 按seq查询历史或断线后的增量。before和after互斥；首屏倒序取最近消息，增量按升序避免跳过中间缺口。
     */
    public ChatPageVO<ChatMessageVO> messages(long uid,String uuid,Long before,Long after,int size){
        if(size<1||size>50||(before!=null&&before<=0)||(after!=null&&after<0)||(before!=null&&after!=null))bad("消息分页参数不正确");
        var c=require(uid,uuid);
        var rows=messages.page(c.getId(),before,after,size+1);
        boolean more=rows.size()>size;
        var items=rows.stream().limit(size).map(ChatMessageVO::from).toList();
        return new ChatPageVO<>(items,more?Long.toString(items.getLast().seq()):null,more);
    }
    /**
     * 在会话行锁保护下发送纯文字。先检查已提交的幂等记录，再检查可发送期限和账号限流；重复重试不再次扣配额或增加seq。
     */
    @Transactional
    public ChatMessageVO send(long uid,ChatSendRequest cmd){
        if(cmd==null)bad("消息不能为空");
        uuid(cmd.conversationUuid());
        uuid(cmd.clientMessageId());
        String content=cmd.content();
        if(content==null||content.isBlank()||content.codePointCount(0,content.length())>500)bad("请输入1至500字的消息");
        var actor=access.actor(uid);
        var c=conversations.lock(cmd.conversationUuid());
        if(c==null)throw new BusinessException(CommonErrorCode.NOT_FOUND,"会话不存在");
        var order=access.order(c.getOrderId());
        access.check(actor,order);
        // 先查幂等再查期限：已成功但ACK丢失的重试，不应因为刚过24小时而丢失原结果。
        var old=messages.duplicate(c.getId(),uid,cmd.clientMessageId());
        if(old!=null){
            if(!old.getContent().equals(content))throw new BusinessException(CommonErrorCode.CONFLICT,"重试消息内容不能更改");
            return ChatMessageVO.from(old);
        }
        if(!access.canSend(order))throw new BusinessException(CommonErrorCode.CONFLICT,"沟通期限已结束，当前会话只读");
        // 账号限流覆盖全部设备连接，计数和TTL通过公共Redis组件原子维护。
        try {
            Long second=redis.increment("chat:send:second:"+uid,Duration.ofSeconds(1));
            Long minute=redis.increment("chat:send:minute:"+uid,Duration.ofMinutes(1));
            if(second==null||minute==null)throw new IllegalStateException();
            if(second>2||minute>30)throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS,"发送太快，请稍后再试");
        }
        catch(BusinessException e){
            throw e;
        }
        catch(RuntimeException e){
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE,"暂时无法发送，请稍后重试");
        }
        var m=new ChatMessageDO();
        m.setMessageUuid(UUID.randomUUID().toString());
        m.setConversationId(c.getId());
        m.setSeq(c.getLastSeq()+1);
        m.setSenderId(uid);
        m.setSenderUuid(actor.uuid());
        m.setSenderName(actor.name());
        m.setSenderSide(actor.customer()?"CUSTOMER":"MERCHANT");
        m.setClientMessageId(cmd.clientMessageId());
        m.setContent(content);
        m.setCreatedAt(java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai")));
        // 三次写入同属本地事务，任意失败都回滚，推送任务看不到未提交消息。
        messages.insert(m);
        conversations.advance(c.getId(),m.getSeq(),content);
        event(c.getConversationUuid());
        return ChatMessageVO.from(m);
    }
    /**
     * 提交本人实际阅读位置。水位不能超过已提交最大序号，SQL用GREATEST保证并发请求不会把已读位置回退。
     */
    @Transactional
    public void read(long uid,String uuid,long seq){
        var c=require(uid,uuid);
        if(seq<0||seq>c.getLastSeq())bad("已读位置不正确");
        messages.read(c.getId(),uid,seq);
        event(uuid);
    }
    /**
     * 查询本人当前可见会话的未读总量。店主无直接窗口授权时返回零，不把平台或餐厅范围展开成私人会话。
     */
    public long unread(long uid){
        var a=access.actor(uid);
        return !a.customer()&&a.windows().isEmpty()?0:conversations.unreadTotal(uid,a.customer(),a.windows());
    }
    /**
     * 供在线推送筛选接收者。资源不存在、授权撤销或依赖异常都返回false，不向该连接泄露聊天内容。
     */
    public boolean canRead(long uid,String uuid){
        try{
            require(uid,uuid);
            return true;
        }
        catch(RuntimeException e){
            return false;
        }
    }
    /** 与发送共用会话锁，撤回标记、摘要和Outbox同事务提交，失败全部回滚。 */
    @Transactional
    public ChatMessageVO revoke(long uid,String conversationUuid,String messageUuid){
        uuid(conversationUuid); uuid(messageUuid);
        var c=conversations.lock(conversationUuid);
        if(c==null)throw new BusinessException(CommonErrorCode.NOT_FOUND,"会话不存在");
        access.check(access.actor(uid),access.order(c.getOrderId()));
        var m=messages.findMessage(c.getId(),messageUuid);
        if(m==null||m.getSenderId()!=uid)throw new BusinessException(CommonErrorCode.FORBIDDEN,"只能撤回本人消息");
        // 重复请求不产生重复事件，VO始终屏蔽原文，发送重试仍可核验幂等内容。
        if(m.getRevokedAt()!=null)return ChatMessageVO.from(m);
        var now=java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai"));
        if(messages.revoke(m.getId(),uid,now)!=1)throw new BusinessException(CommonErrorCode.CONFLICT,"消息已超过十分钟，无法撤回");
        m.setRevokedAt(now); conversations.revokePreview(c.getId(),m.getSeq()); event(conversationUuid);
        return ChatMessageVO.from(m);
    }
    /** 按已加载UUID补查，避免只刷新最近一页而漏掉较早历史的撤回变化。 */
    public List<ChatMessageVO> revoked(long uid,String conversationUuid,List<String> ids){
        var c=require(uid,conversationUuid);
        if(ids==null||ids.size()>50)bad("每次最多查询50条消息");
        for(String id:ids)uuid(id);
        if(ids.isEmpty())return List.of();
        return messages.revoked(c.getId(),ids).stream().map(ChatMessageVO::from).toList();
    }
}

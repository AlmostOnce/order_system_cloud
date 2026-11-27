package com.hbue.ordering.support.assembler;

import com.hbue.ordering.support.model.SupportTicketDO;
import com.hbue.ordering.support.model.SupportTicketMessageDO;
import com.hbue.ordering.support.service.SupportImageService;
import com.hbue.ordering.support.support.SupportCategory;
import com.hbue.ordering.support.support.SupportSenderType;
import com.hbue.ordering.support.support.SupportStatus;
import com.hbue.ordering.support.support.SupportType;
import com.hbue.ordering.support.vo.SupportManagementTicketVO;
import com.hbue.ordering.support.vo.SupportTicketDetailVO;
import com.hbue.ordering.support.vo.SupportTicketMessageVO;
import com.hbue.ordering.support.vo.SupportTicketSummaryVO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 工单持久化对象到接口视图的转换器（Assembler）。
 *
 * <p>集中处理类型、分类、状态的中文映射与图片地址派生，避免学生端和管理端返回字段口径不一致。
 * 列表描述截断到 60 字，详情返回完整正文；内部备注是否返回由查询 SQL 决定，转换器不做二次过滤。</p>
 *
 * <p>本类不承载业务规则、不访问数据库，放在独立的 assembler 包而不是 service.impl，
 * 以免被误认为某个服务接口的实现。</p>
 *
 * @author order-system
 * @date 2026-11-23
 */
@Component
public class SupportTicketViewAssembler {

    /**
     * 列表描述最大展示长度。
     */
    private static final int SUMMARY_DESCRIPTION_LENGTH = 60;

    /**
     * 图片键与展示地址转换服务。
     */
    private final SupportImageService imageService;

    /**
     * 创建转换器。
     *
     * @param imageService 图片键与展示地址转换服务
     */
    public SupportTicketViewAssembler(SupportImageService imageService) {
        this.imageService = imageService;
    }

    /**
     * 转换学生列表项。
     *
     * @param ticket 工单
     * @return 列表项
     */
    public SupportTicketSummaryVO toSummary(SupportTicketDO ticket) {
        SupportType type = SupportType.findByCode(ticket.getType());
        SupportStatus status = SupportStatus.findByCode(ticket.getStatus());
        return new SupportTicketSummaryVO(
                ticket.getTicketId(),
                displayNo(ticket.getTicketId()),
                ticket.getType(),
                type == null ? ticket.getType() : type.getLabel(),
                SupportCategory.labelOf(ticket.getType(), ticket.getCategory()),
                ticket.getStatus(),
                status == null ? ticket.getStatus() : status.getLabel(),
                ticket.getOrderNo(),
                ticket.getWindowName(),
                truncate(ticket.getDescription()),
                Integer.valueOf(1).equals(ticket.getCustomerUnread()),
                ticket.getCreatedAt(),
                ticket.getLastReplyAt()
        );
    }

    /**
     * 转换学生工单详情。
     *
     * @param ticket   工单
     * @param messages 学生可见时间线
     * @return 详情视图
     */
    public SupportTicketDetailVO toDetail(SupportTicketDO ticket, List<SupportTicketMessageDO> messages) {
        SupportType type = SupportType.findByCode(ticket.getType());
        SupportStatus status = SupportStatus.findByCode(ticket.getStatus());
        return new SupportTicketDetailVO(
                ticket.getTicketId(),
                displayNo(ticket.getTicketId()),
                ticket.getType(),
                type == null ? ticket.getType() : type.getLabel(),
                ticket.getCategory(),
                SupportCategory.labelOf(ticket.getType(), ticket.getCategory()),
                ticket.getStatus(),
                status == null ? ticket.getStatus() : status.getLabel(),
                ticket.getOrderId(),
                ticket.getOrderNo(),
                ticket.getWindowId(),
                ticket.getWindowName(),
                ticket.getDescription(),
                imageService.toDisplayUrls(imageService.parseKeys(ticket.getImageFileKeys())),
                ticket.getDeviceInfo(),
                Integer.valueOf(1).equals(ticket.getCustomerUnread()),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt(),
                ticket.getLastReplyAt(),
                ticket.getClosedAt(),
                toMessages(messages)
        );
    }

    /**
     * 转换管理端工单视图。
     *
     * @param ticket   工单
     * @param messages 完整时间线；队列列表传空列表
     * @return 管理端视图
     */
    public SupportManagementTicketVO toManagement(SupportTicketDO ticket,
                                                  List<SupportTicketMessageDO> messages) {
        SupportType type = SupportType.findByCode(ticket.getType());
        SupportStatus status = SupportStatus.findByCode(ticket.getStatus());
        return new SupportManagementTicketVO(
                ticket.getTicketId(),
                displayNo(ticket.getTicketId()),
                ticket.getType(),
                type == null ? ticket.getType() : type.getLabel(),
                ticket.getCategory(),
                SupportCategory.labelOf(ticket.getType(), ticket.getCategory()),
                ticket.getStatus(),
                status == null ? ticket.getStatus() : status.getLabel(),
                ticket.getCustomerId(),
                ticket.getOrderId(),
                ticket.getOrderNo(),
                ticket.getWindowId(),
                ticket.getWindowName(),
                ticket.getRestaurantId(),
                ticket.getDescription(),
                imageService.toDisplayUrls(imageService.parseKeys(ticket.getImageFileKeys())),
                ticket.getDeviceInfo(),
                Integer.valueOf(1).equals(ticket.getCustomerUnread()),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt(),
                ticket.getLastReplyAt(),
                ticket.getClosedAt(),
                toMessages(messages)
        );
    }

    /**
     * 转换单条学生可见消息，用于补充说明的幂等返回。
     *
     * @param message 消息
     * @return 消息视图
     */
    public SupportTicketMessageVO toMessage(SupportTicketMessageDO message) {
        return new SupportTicketMessageVO(
                message.getMessageId(),
                message.getSenderType(),
                senderLabel(message.getSenderType()),
                message.getContent(),
                imageService.toDisplayUrls(imageService.parseKeys(message.getImageFileKeys())),
                Integer.valueOf(1).equals(message.getInternalNote()),
                message.getCreatedAt()
        );
    }

    /**
     * 批量转换时间线。
     *
     * @param messages 消息列表
     * @return 消息视图列表
     */
    public List<SupportTicketMessageVO> toMessages(List<SupportTicketMessageDO> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<SupportTicketMessageVO> views = new ArrayList<>(messages.size());
        for (SupportTicketMessageDO message : messages) {
            views.add(toMessage(message));
        }
        return List.copyOf(views);
    }

    /**
     * 由公开标识生成 8 位大写展示编号。
     *
     * @param ticketId 公开工单标识
     * @return 展示编号；标识异常时原样返回，避免详情因展示字段失败
     */
    public String displayNo(String ticketId) {
        if (ticketId == null || ticketId.isBlank()) {
            return "";
        }
        String compact = ticketId.replace("-", "");
        return compact.length() <= 8 ? compact.toUpperCase() : compact.substring(0, 8).toUpperCase();
    }

    /**
     * 生成发送方中文名称。
     *
     * @param senderType 发送方编码
     * @return 中文名称
     */
    private String senderLabel(String senderType) {
        for (SupportSenderType type : SupportSenderType.values()) {
            if (type.getCode().equals(senderType)) {
                return type.getLabel();
            }
        }
        return "系统";
    }

    /**
     * 截断列表描述。
     *
     * @param description 完整描述
     * @return 最多 60 字，超出时追加省略号
     */
    private String truncate(String description) {
        if (description == null) {
            return "";
        }
        return description.length() <= SUMMARY_DESCRIPTION_LENGTH
                ? description
                : description.substring(0, SUMMARY_DESCRIPTION_LENGTH) + "…";
    }
}

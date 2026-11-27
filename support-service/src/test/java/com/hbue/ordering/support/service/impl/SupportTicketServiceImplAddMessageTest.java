package com.hbue.ordering.support.service.impl;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.support.assembler.SupportTicketViewAssembler;
import com.hbue.ordering.support.client.SupportOrderClient;
import com.hbue.ordering.support.client.SupportWindowClient;
import com.hbue.ordering.support.dto.AddSupportMessageRequest;
import com.hbue.ordering.support.mapper.SupportTicketMapper;
import com.hbue.ordering.support.mapper.SupportTicketMessageMapper;
import com.hbue.ordering.support.model.SupportTicketDO;
import com.hbue.ordering.support.model.SupportTicketMessageDO;
import com.hbue.ordering.support.service.SupportImageService;
import com.hbue.ordering.support.support.SupportStatus;
import com.hbue.ordering.support.vo.SupportTicketDetailVO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 学生补充说明幂等测试：验证同一幂等键命中时只有内容完全一致才按重放处理。
 *
 * @author order-system
 * @date 2026-11-27
 */
class SupportTicketServiceImplAddMessageTest {

    /**
     * 测试用学生内部编号。
     */
    private static final Long CUSTOMER_ID = 5L;

    /**
     * 测试用公开工单标识。
     */
    private static final String TICKET_ID = "T-20261127";

    /**
     * 测试用幂等键。
     */
    private static final String REQUEST_KEY = "request_key_0001";

    /**
     * 测试用详情视图；记录类型不可用 Mockito 伪造，直接构造真实对象比对引用。
     */
    private static final SupportTicketDetailVO DETAIL = new SupportTicketDetailVO(
            TICKET_ID, "FB20261127001", "SOFTWARE", "软件问题", "OTHER", "其他",
            SupportStatus.PENDING.getCode(), "待受理", null, null, null, null,
            "测试描述", List.of(), null, false, null, null, null, null, List.of()
    );

    /**
     * 首次提交成功后重试同一幂等键：内容一致时返回当前详情，不重复写入。
     */
    @Test
    void shouldReturnDetailWhenReplayContentMatches() {
        Fixture fixture = new Fixture();
        SupportTicketMessageDO existing = fixture.existingMessage("补充说明内容", "[]");
        when(fixture.messageMapper.byRequest(100L, REQUEST_KEY)).thenReturn(existing);
        when(fixture.imageService.parseKeys("[]")).thenReturn(List.of());
        when(fixture.messageMapper.timelineForCustomer(100L)).thenReturn(List.of());
        when(fixture.viewAssembler.toDetail(fixture.ticket, List.of())).thenReturn(DETAIL);

        // 客户端带首尾空白重试，应规整后视为同一内容。
        SupportTicketDetailVO result = fixture.service.addMessage(
                CUSTOMER_ID, TICKET_ID, REQUEST_KEY, request(" 补充说明内容 ", null));

        assertSame(DETAIL, result);
        verify(fixture.messageMapper, never()).insert(any(SupportTicketMessageDO.class));
    }

    /**
     * 幂等键命中但正文不同：必须返回 409，不能把新内容静默丢弃。
     */
    @Test
    void shouldRejectReplayWhenContentDiffers() {
        Fixture fixture = new Fixture();
        when(fixture.messageMapper.byRequest(100L, REQUEST_KEY))
                .thenReturn(fixture.existingMessage("补充说明内容", "[]"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> fixture.service.addMessage(
                        CUSTOMER_ID, TICKET_ID, REQUEST_KEY, request("另一条补充说明", null)));

        assertEquals(CommonErrorCode.CONFLICT, exception.getErrorCode());
        verify(fixture.messageMapper, never()).insert(any(SupportTicketMessageDO.class));
        verifyNoInteractions(fixture.viewAssembler);
    }

    /**
     * 幂等键命中但图片不同：即使正文相同也必须返回 409。
     */
    @Test
    void shouldRejectReplayWhenImageKeysDiffer() {
        Fixture fixture = new Fixture();
        when(fixture.messageMapper.byRequest(100L, REQUEST_KEY))
                .thenReturn(fixture.existingMessage("补充说明内容", "[\"a\"]"));
        when(fixture.imageService.parseKeys("[\"a\"]")).thenReturn(List.of("a"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> fixture.service.addMessage(
                        CUSTOMER_ID, TICKET_ID, REQUEST_KEY, request("补充说明内容", List.of("b"))));

        assertEquals(CommonErrorCode.CONFLICT, exception.getErrorCode());
        verify(fixture.messageMapper, never()).insert(any(SupportTicketMessageDO.class));
    }

    /**
     * 未命中幂等键时正常写入，并保存规整后的正文与图片 JSON。
     */
    @Test
    void shouldInsertNormalizedMessageWhenKeyIsNew() {
        Fixture fixture = new Fixture();
        when(fixture.messageMapper.byRequest(100L, REQUEST_KEY)).thenReturn(null);
        when(fixture.imageService.validateAndNormalize(List.of("a"), CUSTOMER_ID)).thenReturn(List.of("a"));
        when(fixture.imageService.toJson(List.of("a"))).thenReturn("[\"a\"]");
        when(fixture.messageMapper.timelineForCustomer(100L)).thenReturn(List.of());
        when(fixture.ticketMapper.touchUpdatedAt(100L)).thenReturn(1);
        when(fixture.viewAssembler.toDetail(fixture.ticket, List.of())).thenReturn(DETAIL);

        fixture.service.addMessage(CUSTOMER_ID, TICKET_ID, REQUEST_KEY,
                request(" 新补充说明 ", List.of("a")));

        ArgumentCaptor<SupportTicketMessageDO> captor =
                ArgumentCaptor.forClass(SupportTicketMessageDO.class);
        verify(fixture.messageMapper).insert(captor.capture());
        assertEquals("新补充说明", captor.getValue().getContent());
        assertEquals("[\"a\"]", captor.getValue().getImageFileKeys());
        verify(fixture.ticketMapper).touchUpdatedAt(100L);
    }

    /**
     * 并发同键插入冲突后回查到的正文若不同，同样按 409 处理而不是返回他人内容。
     */
    @Test
    void shouldRejectWhenConcurrentWinnerHasDifferentContent() {
        Fixture fixture = new Fixture();
        when(fixture.messageMapper.byRequest(100L, REQUEST_KEY))
                .thenReturn(null)
                .thenReturn(fixture.existingMessage("并发写入的内容", "[]"));
        when(fixture.imageService.validateAndNormalize(any(), anyLong())).thenReturn(List.of());
        when(fixture.imageService.toJson(List.of())).thenReturn("[]");
        when(fixture.messageMapper.insert(any(SupportTicketMessageDO.class)))
                .thenThrow(new DuplicateKeyException("uk_support_ticket_messages_request"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> fixture.service.addMessage(
                        CUSTOMER_ID, TICKET_ID, REQUEST_KEY, request("本次的内容", null)));

        assertEquals(CommonErrorCode.CONFLICT, exception.getErrorCode());
        verify(fixture.ticketMapper, never()).touchUpdatedAt(anyLong());
    }

    /**
     * 构造补充说明请求。
     *
     * @param content   正文
     * @param imageKeys 图片键
     * @return 请求对象
     */
    private AddSupportMessageRequest request(String content, List<String> imageKeys) {
        AddSupportMessageRequest request = new AddSupportMessageRequest();
        request.setContent(content);
        request.setImageFileKeys(imageKeys);
        return request;
    }

    /**
     * 公共测试夹具：初始化待测服务与全部协作者替身。
     */
    private static final class Fixture {

        /**
         * 工单数据访问替身。
         */
        private final SupportTicketMapper ticketMapper = mock(SupportTicketMapper.class);

        /**
         * 消息数据访问替身。
         */
        private final SupportTicketMessageMapper messageMapper = mock(SupportTicketMessageMapper.class);

        /**
         * 图片服务替身。
         */
        private final SupportImageService imageService = mock(SupportImageService.class);

        /**
         * 视图转换器替身。
         */
        private final SupportTicketViewAssembler viewAssembler = mock(SupportTicketViewAssembler.class);

        /**
         * 测试用工单，归属当前学生且处于待受理状态。
         */
        private final SupportTicketDO ticket = ticket();

        /**
         * 被测服务。
         */
        private final SupportTicketServiceImpl service = new SupportTicketServiceImpl(
                ticketMapper,
                messageMapper,
                imageService,
                viewAssembler,
                mock(SupportOrderClient.class),
                mock(SupportWindowClient.class),
                "internal-token"
        );

        private Fixture() {
            when(ticketMapper.byTicketId(TICKET_ID)).thenReturn(ticket);
        }

        /**
         * 构造一条已写入的补充说明。
         *
         * @param content  正文
         * @param images   图片键 JSON
         * @return 消息记录
         */
        private SupportTicketMessageDO existingMessage(String content, String images) {
            SupportTicketMessageDO message = new SupportTicketMessageDO();
            message.setId(1L);
            message.setTicketId(100L);
            message.setContent(content);
            message.setImageFileKeys(images);
            message.setRequestKey(REQUEST_KEY);
            return message;
        }

        /**
         * 构造可被归属校验通过的学生工单。
         *
         * @return 工单记录
         */
        private static SupportTicketDO ticket() {
            SupportTicketDO ticket = new SupportTicketDO();
            ticket.setId(100L);
            ticket.setTicketId(TICKET_ID);
            ticket.setCustomerId(CUSTOMER_ID);
            ticket.setStatus(SupportStatus.PENDING.getCode());
            ticket.setCustomerUnread(0);
            return ticket;
        }
    }
}

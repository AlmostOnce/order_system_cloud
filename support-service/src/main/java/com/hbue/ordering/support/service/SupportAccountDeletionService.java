package com.hbue.ordering.support.service;

import com.hbue.ordering.support.mapper.SupportTicketMapper;
import com.hbue.ordering.support.vo.AccountDeletionBlockersVO;
import org.springframework.stereotype.Service;

/**
 * 账号注销的工单侧阻断查询。
 *
 * <p>只读统计学生未关闭工单数量，不修改工单状态；每次资格校验和到期复核都重新查询，
 * 保证用户在处理间隙新开工单时不会被误注销。</p>
 *
 * @author order-system
 * @date 2026-11-24
 */
@Service
public class SupportAccountDeletionService {

    /**
     * 工单数据访问。
     */
    private final SupportTicketMapper supportTicketMapper;

    /**
     * 创建工单侧阻断查询服务。
     *
     * @param supportTicketMapper 工单数据访问
     */
    public SupportAccountDeletionService(SupportTicketMapper supportTicketMapper) {
        this.supportTicketMapper = supportTicketMapper;
    }

    /**
     * 查询学生未关闭工单数量。
     *
     * @param customerId 学生内部用户编号
     * @return 阻断统计
     */
    public AccountDeletionBlockersVO blockers(Long customerId) {
        return new AccountDeletionBlockersVO(
                supportTicketMapper.countOpenForAccountDeletion(customerId));
    }
}

package com.hbue.ordering.support.service.impl;

import com.hbue.ordering.support.mapper.SupportTicketMapper;
import com.hbue.ordering.support.service.SupportAccountDeletionService;
import com.hbue.ordering.support.vo.AccountDeletionBlockersVO;
import org.springframework.stereotype.Service;

/**
 * 账号注销的工单侧阻断查询实现。
 *
 * <p>每次调用都直接查库，不加缓存：用户在注销处理间隙新开工单必须立刻反映到阻断结果，
 * 缓存在这里会把"有未关闭工单"误判为可注销。</p>
 */
@Service
public class SupportAccountDeletionServiceImpl implements SupportAccountDeletionService {

    /**
     * 工单数据访问。
     */
    private final SupportTicketMapper supportTicketMapper;

    /**
     * 创建工单侧阻断查询服务。
     *
     * @param supportTicketMapper 工单数据访问
     */
    public SupportAccountDeletionServiceImpl(SupportTicketMapper supportTicketMapper) {
        this.supportTicketMapper = supportTicketMapper;
    }

    /**
     * 查询学生未关闭工单数量。
     */
    @Override
    public AccountDeletionBlockersVO blockers(Long customerId) {
        return new AccountDeletionBlockersVO(
                supportTicketMapper.countOpenForAccountDeletion(customerId));
    }
}

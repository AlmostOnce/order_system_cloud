package com.hbue.ordering.comment.service.impl;

import com.hbue.ordering.comment.mapper.RatingBatchMapper;
import com.hbue.ordering.comment.service.RatingBatchService;
import com.hbue.ordering.comment.vo.RatingBatchItemVO;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 评分汇总批量查询的实现。
 *
 * <p>类型参数采用白名单（windows / products），未知类型直接返回参数错误：若退化为空列表，
 * 上游拼错类型时会长期显示"暂无评分"而无人察觉。</p>
 *
 * <p>编号集合限制为最多 100 个，既控制单次查询规模，也避免 IN 列表无界增长拖垮数据库。</p>
 *
 * @author order-system
 * @date 2026-10-28
 */
@Service
public class RatingBatchServiceImpl implements RatingBatchService {

    /**
     * 评分汇总查询。
     */
    private final RatingBatchMapper repository;

    /**
     * 创建批量评分服务。
     *
     * @param repository 评分汇总查询
     */
    public RatingBatchServiceImpl(RatingBatchMapper repository) {
        this.repository = repository;
    }

    public List<RatingBatchItemVO> get(String kind, List<Long> ids) {
        if ((!"windows".equals(kind) && !"products".equals(kind)) || ids == null || ids.isEmpty() || ids.size() > 100 || ids.stream().anyMatch(id -> id == null || id <= 0))
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "评分查询参数不正确");
        var rows = "windows".equals(kind) ? repository.windows(ids) : repository.products(ids);
        return rows.stream().map(row -> new RatingBatchItemVO(row.id(), row.count(), row.average())).toList();
    }
}

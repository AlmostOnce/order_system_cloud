package com.hbue.ordering.comment.service;

import com.hbue.ordering.comment.vo.RatingBatchItemVO;

import java.util.List;

/**
 * 评分汇总的批量查询。
 *
 * <p>菜品列表与店铺列表都需要展示平均分与评价数，本接口按"类型 + 编号集合"一次取回，
 * 避免前端或上游服务对每个条目单独请求。类型参数决定统计哪张表，未知类型直接拒绝而不是返回空列表，
 * 防止拼错类型被当作"暂无评分"静默放过。</p>
 *
 * @author order-system
 * @date 2026-10-28
 */
public interface RatingBatchService {

    /**
     * 批量查询评分汇总。
     *
     * @param kind 统计对象类型（店铺/菜品等），由实现校验
     * @param ids  对象编号集合，可空
     * @return 评分汇总列表；无评价的对象不返回条目
     */
    List<RatingBatchItemVO> get(String kind, List<Long> ids);
}

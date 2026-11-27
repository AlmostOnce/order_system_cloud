package com.hbue.ordering.order.service;

import com.hbue.ordering.order.vo.OrderDetailVO;

import java.util.List;

/**
 * 订单视图的店铺名称补全。
 *
 * <p>订单库只保存窗口编号，名称属于商品服务的权威数据。本接口按 100 个编号一批调用内部接口补全，
 * 不逐单调用；任何一批失败都只影响该批名称，订单正文与支付流程不受影响。</p>
 *
 * <p>补全失败时保持名称为空由前端兜底展示，不抛异常：展示字段缺失不应让用户看不到自己的订单。</p>
 *
 * @author order-system
 * @date 2026-10-18
 */
public interface OrderWindowNameService {

    /**
     * 为一批订单视图补全店铺名称。
     *
     * @param orders 待补全的订单视图
     * @return 补全后的新列表，保持原顺序
     */
    List<OrderDetailVO> enrich(List<OrderDetailVO> orders);
}

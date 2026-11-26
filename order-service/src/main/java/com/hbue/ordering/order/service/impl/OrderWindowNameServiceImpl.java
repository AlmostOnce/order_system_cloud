package com.hbue.ordering.order.service.impl;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.order.service.OrderWindowNameService;
import com.hbue.ordering.order.vo.OrderDetailVO;
import com.hbue.ordering.product.api.client.WindowInternalClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 订单视图店铺名称补全的实现。
 *
 * <p>按 100 个窗口编号一批调用商品服务内部接口，避免逐个订单发起远程调用（N+1）。
 * 每一批独立 try-catch：单批失败只丢该批名称，不影响其他批次与订单正文。</p>
 *
 * <p>名称是展示字段，缺失时前端回落展示，因此这里只记 warn 日志不抛异常；
 * 若抛异常会导致用户连自己的订单列表都打不开。</p>
 *
 * @author order-system
 * @date 2026-10-18
 */
@Service
public class OrderWindowNameServiceImpl implements OrderWindowNameService {

    /**
     * 当前类日志对象。
     */
    private static final Logger log = LoggerFactory.getLogger(OrderWindowNameServiceImpl.class);

    /**
     * 商品服务窗口内部客户端。
     */
    private final WindowInternalClient windows;

    /**
     * 服务间共享内部令牌。
     */
    private final String token;

    /**
     * 创建补全服务。
     *
     * @param windows 窗口内部客户端
     * @param token   服务间共享内部令牌
     */
    public OrderWindowNameServiceImpl(WindowInternalClient windows, @Value("${internal.scope.token:}") String token) {
        this.windows = windows;
        this.token = token;
    }

    @Override
    public List<OrderDetailVO> enrich(List<OrderDetailVO> orders) {
        var ids = orders.stream().map(OrderDetailVO::getWindowId).filter(id -> id != null && id > 0).distinct().toList();
        Map<Long, String> names = new HashMap<>();
        for (int start = 0; start < ids.size(); start += 100) {
            var batch = ids.subList(start, Math.min(start + 100, ids.size()));
            try {
                var response = windows.getWindowNames(batch, token);
                if (response != null && CommonErrorCode.SUCCESS.code().equals(response.getCode()) && response.getData() != null)
                    for (Long id : batch) {
                        String name = response.getData().get(id);
                        if (name != null && !name.isBlank()) names.put(id, name);
                    }
            } catch (RuntimeException e) {
                log.warn("订单店铺名称暂不可用");
            }
        }
        // 名称查询异常不影响支付和订单正文，前端显示名称暂不可用。
        return orders.stream().map(order -> order.toBuilder().windowName(names.get(order.getWindowId())).build()).toList();
    }
}

package com.hbue.ordering.order.service.impl;

import com.hbue.ordering.order.service.OrderWindowNameService;
import com.hbue.ordering.order.vo.OrderDetailVO;
import com.hbue.ordering.product.api.client.WindowInternalClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class OrderWindowNameServiceImpl implements OrderWindowNameService {
    private static final Logger log = LoggerFactory.getLogger(OrderWindowNameServiceImpl.class);
    private final WindowInternalClient windows;
    private final String token;
    public OrderWindowNameServiceImpl(WindowInternalClient windows, @Value("${internal.scope.token:}") String token) {
        this.windows = windows; this.token = token;
    }
    @Override
    public List<OrderDetailVO> enrich(List<OrderDetailVO> orders) {
        var ids = orders.stream().map(OrderDetailVO::getWindowId).filter(id -> id != null && id > 0).distinct().toList();
        Map<Long, String> names = new HashMap<>();
        for (int start = 0; start < ids.size(); start += 100) {
            var batch = ids.subList(start, Math.min(start + 100, ids.size()));
            try {
                var response = windows.getWindowNames(batch, token);
                if (response != null && "COMMON_000".equals(response.getCode()) && response.getData() != null)
                    for (Long id : batch) {
                        String name = response.getData().get(id);
                        if (name != null && !name.isBlank()) names.put(id, name);
                    }
            } catch (RuntimeException e) { log.warn("订单店铺名称暂不可用"); }
        }
        // 名称查询异常不影响支付和订单正文，前端显示名称暂不可用。
        return orders.stream().map(order -> order.toBuilder().windowName(names.get(order.getWindowId())).build()).toList();
    }
}

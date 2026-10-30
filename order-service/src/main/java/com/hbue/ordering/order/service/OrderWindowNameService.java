package com.hbue.ordering.order.service;

import com.hbue.ordering.order.vo.OrderDetailVO;
import java.util.List;

public interface OrderWindowNameService {
    List<OrderDetailVO> enrich(List<OrderDetailVO> orders);
}

package com.hbue.ordering.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hbue.ordering.product.mapper.DiningWindowMapper;
import com.hbue.ordering.product.model.DiningWindowDO;
import com.hbue.ordering.product.service.WindowCatalogService;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import org.springframework.stereotype.Service;

@Service
public class WindowCatalogServiceImpl implements WindowCatalogService {
    private final DiningWindowMapper windows;
    public WindowCatalogServiceImpl(DiningWindowMapper windows) { this.windows = windows; }
    @Override
    public Map<Long, String> names(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 100 || ids.stream().anyMatch(id -> id == null || id <= 0))
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "店铺查询参数不正确");
        Map<Long, String> names = new LinkedHashMap<>();
        // 历史订单也能显示停用店铺，只查询编号与名称，不读取菜单、库存。
        for (DiningWindowDO window : windows.selectList(new QueryWrapper<DiningWindowDO>()
                .select("window_id", "window_name").in("window_id", ids.stream().distinct().toList())))
            if (window.getWindowName() != null) names.put(window.getWindowId(), window.getWindowName());
        return names;
    }
}

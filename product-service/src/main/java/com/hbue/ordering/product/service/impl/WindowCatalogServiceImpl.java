package com.hbue.ordering.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.product.mapper.DiningWindowMapper;
import com.hbue.ordering.product.model.DiningWindowDO;
import com.hbue.ordering.product.service.WindowCatalogService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 店铺编号到名称的批量查询实现。
 *
 * <p>参数校验在入口完成（非空、上限 100、编号为正），随后用一条 IN 查询只取两列；
 * 店铺已停用同样返回名称，保证历史订单不出现空白店铺名。</p>
 *
 * <p>结果使用 {@link LinkedHashMap} 保持数据库返回顺序，便于调用方按同一顺序拼装下游列表。</p>
 *
 * @author order-system
 * @date 2026-10-18
 */
@Service
public class WindowCatalogServiceImpl implements WindowCatalogService {

    /**
     * 店铺数据访问。
     */
    private final DiningWindowMapper windows;

    /**
     * 创建查询服务。
     *
     * @param windows 店铺数据访问
     */
    public WindowCatalogServiceImpl(DiningWindowMapper windows) {
        this.windows = windows;
    }

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

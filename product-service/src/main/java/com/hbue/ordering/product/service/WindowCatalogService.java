package com.hbue.ordering.product.service;

import java.util.List;
import java.util.Map;

/**
 * 店铺编号到名称的批量查询。
 *
 * <p>供订单、统计等上游服务补全展示字段使用。查询包含已停用店铺：历史订单仍需要显示当时的店铺名称，
 * 按"在营"过滤会让老订单出现空白。</p>
 *
 * <p>只读取编号与名称两列，不触碰菜单与库存，避免为了展示名称把整个店铺聚合加载出来。</p>
 *
 * @author order-system
 * @date 2026-10-18
 */
public interface WindowCatalogService {

    /**
     * 批量查询店铺名称。
     *
     * @param ids 店铺编号，最多 100 个
     * @return 店铺编号到名称的映射；不存在的编号不会出现在结果中
     */
    Map<Long, String> names(List<Long> ids);
}

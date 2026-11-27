package com.hbue.ordering.product.service;

import com.hbue.ordering.product.vo.ProductImageVO;
import com.hbue.ordering.product.vo.RestaurantSummaryVO;
import com.hbue.ordering.product.vo.WindowMenuVO;
import com.hbue.ordering.product.vo.WindowSummaryVO;

import java.util.List;

/**
 * 顾客可见的餐厅、窗口和菜单查询服务契约。
 *
 * <p>定义菜单查询用例，不暴露数据库 DO，也不承担管理端目录写入；具体缓存、布隆过滤器、
 * 分布式锁和数据库回源策略由实现类负责。</p>
 *
 * @author order-system
 * @date 2026-09-28
 */
public interface ProductMenuService {

    /**
     * 查询所有启用餐厅的摘要，按展示排序值升序返回。
     *
     * @return 启用餐厅列表
     */
    List<RestaurantSummaryVO> listActiveRestaurants();

    /**
     * 批量读取最多50道当前可售菜品的图片，缺失项由界面使用默认图。
     */
    List<ProductImageVO> getProductImages(List<Long> productIds);

    /**
     * 查询所有启用窗口的摘要。
     *
     * @return 按所属餐厅展示排序、再按窗口 ID 排序的启用窗口
     */
    List<WindowSummaryVO> listActiveWindows();

    /**
     * 查询指定启用窗口的菜单。
     *
     * @param windowId 窗口 ID
     * @return 窗口信息、启用分类和在售菜品
     * @throws com.hbue.ordering.common.core.exception.BusinessException 窗口 ID 无效或窗口不存在/停用时抛出
     */
    WindowMenuVO getWindowMenu(Long windowId);

    /**
     * 查询单个窗口的展示摘要，供订单详情展示取餐地点。
     *
     * <p>与菜单查询刻意不同：这里<b>不按启用状态和营业状态过滤</b>。订单一旦产生，
     * 学生就需要能回看自己去哪取餐；窗口被停用或当前休息不应让历史订单丢失位置信息。
     * 因此只回源读取窗口行，返回编号、名称、位置等展示字段，不返回菜品、库存或管理状态，
     * 避免为了一个地点把整个菜单聚合加载出来。</p>
     *
     * <p>订单详情把它作为独立展示链路调用：查询失败只会隐藏地点行，不影响订单正文、
     * 取餐码和支付流程。</p>
     *
     * @param windowId 窗口 ID
     * @return 窗口摘要；窗口编号不存在时返回 null
     * @throws com.hbue.ordering.common.core.exception.BusinessException 窗口 ID 非法时抛出
     */
    WindowSummaryVO getWindowSummary(Long windowId);
}

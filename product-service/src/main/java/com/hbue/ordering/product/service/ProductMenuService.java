package com.hbue.ordering.product.service;

import com.hbue.ordering.product.vo.WindowMenuVO;
import com.hbue.ordering.product.vo.WindowSummaryVO;

import java.util.List;

/**
 * 顾客可见的窗口和菜单查询服务契约。
 *
 * <p>定义菜单查询用例，不暴露数据库 DO，也不承担管理端目录写入；具体缓存、布隆过滤器、
 * 分布式锁和数据库回源策略由实现类负责。</p>
 *
 * @author order-system
 * @date 2026-09-28
 */
public interface ProductMenuService {

    /**
     * 查询所有启用窗口的摘要。
     *
     * @return 按窗口 ID 排序的启用窗口
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
}

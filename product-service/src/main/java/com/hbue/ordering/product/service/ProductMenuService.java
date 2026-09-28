package com.hbue.ordering.product.service;

import com.hbue.ordering.product.vo.WindowMenuVO;
import com.hbue.ordering.product.vo.WindowSummaryVO;

import java.util.List;

/**
 * 顾客可见的窗口和菜单查询服务。
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
     */
    WindowMenuVO getWindowMenu(Long windowId);
}

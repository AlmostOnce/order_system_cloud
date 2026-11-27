package com.hbue.ordering.user.model;

/**
 * 学生收藏可以指向的商品类型。
 *
 * <p>收藏关系保存在用户库，但窗口和菜品的实际数据仍由商品服务管理；
 * 这里仅定义跨服务收藏记录使用的稳定类型代码，不复制商品字段。</p>
 */
public enum FavoriteTargetType {
    /**
     * 点餐首页展示、进入后打开菜单的窗口店铺。
     */
    WINDOW,
    /**
     * 窗口菜单中的菜品。
     */
    PRODUCT
}

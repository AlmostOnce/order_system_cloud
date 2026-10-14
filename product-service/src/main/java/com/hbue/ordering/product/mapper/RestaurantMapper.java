package com.hbue.ordering.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hbue.ordering.product.model.RestaurantDO;

/**
 * 餐厅表的数据访问接口。
 *
 * <p>继承 MyBatis-Plus 的基础 CRUD 能力；餐厅权限、停用约束和展示规则
 * 由管理服务校验，本接口只负责持久化访问。</p>
 *
 * @author order-system
 * @date 2026-10-13
 */
public interface RestaurantMapper extends BaseMapper<RestaurantDO> {
}

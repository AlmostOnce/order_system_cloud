package com.hbue.ordering.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hbue.ordering.product.model.ProductDO;

/**
 * 菜品表的数据访问接口。
 *
 * <p>继承 MyBatis-Plus 的基础 CRUD 能力，负责商品持久化查询和更新；
 * 菜品售卖规则、数据范围与缓存一致性由上层 Service 负责。</p>
 *
 * @author order-system
 * @date 2026-09-28
 */
public interface ProductMapper extends BaseMapper<ProductDO> {
}

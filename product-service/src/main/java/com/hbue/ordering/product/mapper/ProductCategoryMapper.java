package com.hbue.ordering.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hbue.ordering.product.model.ProductCategoryDO;

/**
 * 商品分类表的数据访问接口。
 *
 * <p>继承 MyBatis-Plus 的基础 CRUD 能力；分类归属、启停状态和操作者授权
 * 由上层 Service 校验，本接口不承载业务编排。</p>
 *
 * @author order-system
 * @date 2026-09-28
 */
public interface ProductCategoryMapper extends BaseMapper<ProductCategoryDO> {
}

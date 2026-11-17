package com.hbue.ordering.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hbue.ordering.product.model.ProductSpecificationGroupDO;

/** 规格组的 MyBatis-Plus 访问接口；业务层负责范围校验和软停用，禁止物理删除配置。 */
public interface ProductSpecificationGroupMapper extends BaseMapper<ProductSpecificationGroupDO> {
}

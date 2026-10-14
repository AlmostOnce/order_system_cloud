package com.hbue.ordering.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hbue.ordering.product.model.DiningWindowDO;

/**
 * 窗口表的数据访问接口。
 *
 * <p>继承 MyBatis-Plus 的基础 CRUD 能力，供商品服务读取和维护档口持久化数据；
 * 本接口只负责数据库访问，不处理操作者的数据范围或菜单缓存失效。</p>
 *
 * @author order-system
 * @date 2026-09-28
 */
public interface DiningWindowMapper extends BaseMapper<DiningWindowDO> {
}

package com.hbue.ordering.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hbue.ordering.product.model.HomeAnnouncementDO;

/**
 * 首页公告表的数据访问接口。
 *
 * <p>继承 MyBatis-Plus 的基础 CRUD 能力；公告的状态流转、时间窗口校验和
 * 角色权限由 {@code HomeAnnouncementServiceImpl} 与 Controller 负责，
 * 本接口只做持久化访问，不包含业务判断。</p>
 *
 * @author order-system
 * @date 2026-11-26
 */
public interface HomeAnnouncementMapper extends BaseMapper<HomeAnnouncementDO> {
}

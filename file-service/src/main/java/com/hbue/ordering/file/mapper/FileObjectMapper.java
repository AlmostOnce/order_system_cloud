package com.hbue.ordering.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hbue.ordering.file.model.FileObjectDO;

/**
 * 文件对象表的数据访问接口。
 *
 * <p>继承 MyBatis-Plus 的基础 CRUD 能力；文件归属、逻辑删除和业务校验
 * 由存储服务负责，本接口只负责持久化访问。</p>
 *
 * @author order-system
 * @date 2026-10-16
 */
public interface FileObjectMapper extends BaseMapper<FileObjectDO> {
}

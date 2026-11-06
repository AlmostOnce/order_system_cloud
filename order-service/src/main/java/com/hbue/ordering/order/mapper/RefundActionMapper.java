package com.hbue.ordering.order.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hbue.ordering.order.model.RefundActionDO;
import org.apache.ibatis.annotations.Mapper;
/** 仅负责退款审批轨迹持久化，不承担身份或审批判断。 */
@Mapper public interface RefundActionMapper extends BaseMapper<RefundActionDO> {}

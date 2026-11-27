package com.hbue.ordering.coupon.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 模板适用窗口关系写入接口；关系表不建立跨服务窗口外键。
 */
public interface CouponTemplateWindowMapper {

    /**
     * 读取单个模板的适用范围，避免领券响应通过管理列表反查窗口关系。
     */
    @Select("SELECT window_id FROM coupon_template_window WHERE coupon_template_id = #{templateId} ORDER BY window_id")
    List<Long> listWindowIds(@Param("templateId") Long templateId);

    /**
     * 在模板创建事务内批量写入适用窗口，重复 ID 由 Service 预先拒绝。
     */
    @Insert({
            "<script>",
            "INSERT INTO coupon_template_window (coupon_template_id, window_id) VALUES",
            "<foreach collection='windowIds' item='windowId' separator=','>",
            "(#{templateId}, #{windowId})",
            "</foreach>",
            "</script>"
    })
    int insertWindows(
            @Param("templateId") Long templateId,
            @Param("windowIds") List<Long> windowIds
    );

    /**
     * 校验订单所属窗口确实在本券的适用范围内。
     */
    @Select("SELECT COUNT(*) FROM coupon_template_window WHERE coupon_template_id = #{templateId} AND window_id = #{windowId}")
    int countWindow(
            @Param("templateId") Long templateId,
            @Param("windowId") Long windowId
    );
}

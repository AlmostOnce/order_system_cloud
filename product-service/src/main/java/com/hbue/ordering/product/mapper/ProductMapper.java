package com.hbue.ordering.product.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
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
    /** 一次读取顾客可见菜品的图片，不加载完整菜单或逐项查询。 */
    @Select("""
        <script>
        SELECT p.product_id, p.window_id, p.image_file_key
        FROM products p
        JOIN dining_windows w ON w.window_id = p.window_id AND w.status = 1
        JOIN restaurants r ON r.restaurant_id = w.restaurant_id AND r.status = 1
        LEFT JOIN product_categories c ON c.category_id = p.category_id AND c.window_id = p.window_id
        WHERE p.sale_status = 1
          AND (p.category_id IS NULL OR p.category_id = 0 OR c.status = 1)
          AND p.product_id IN
          <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
        ORDER BY p.product_id
        </script>
        """)
    List<ProductDO> selectPublicImageProducts(@Param("ids") List<Long> ids);

}

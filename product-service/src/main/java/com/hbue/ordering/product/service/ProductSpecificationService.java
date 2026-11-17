package com.hbue.ordering.product.service;

import com.hbue.ordering.product.dto.request.ProductSpecificationsSaveRequest;
import com.hbue.ordering.product.vo.ProductSpecificationGroupVO;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** 商品规格边界；管理读写必须核对操作者范围，菜单与核价只读取数据库启用配置。 */
public interface ProductSpecificationService {
    /** 读取目标菜品的启用配置；越过操作者窗口范围时返回403。 */
    List<ProductSpecificationGroupVO> get(Long operatorId, Long productId);
    /** 在事务内完整替换启用配置；返回替换后的规格，并在提交后清菜单缓存。 */
    List<ProductSpecificationGroupVO> replace(Long operatorId, Long productId, ProductSpecificationsSaveRequest request);
    /** 批量加载菜品启用规格；空编号集合不查询数据库，没有配置的菜品不出现在映射中。 */
    Map<Long, List<ProductSpecificationGroupVO>> loadActive(Collection<Long> productIds);
}

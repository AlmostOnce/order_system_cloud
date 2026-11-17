package com.hbue.ordering.order.recommendation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 核对商品服务返回或订单库保存的结构化规格事实，不从规格文字猜测标签。
 * 只做数据格式与选择键一致性验证；规格归属和当时可售性仍由商品核价事务裁决。
 */
public final class OrderSpecificationSnapshot {
    /** 仅解析JSON树，不涉及日期或业务序列化配置，初始化后不再修改。 */
    private static final ObjectMapper JSON = new ObjectMapper();

    private OrderSpecificationSnapshot() { }

    /**
     * 验证快照；新报价不允许有选择却缺少结构化事实，历史订单则允许空数组表示未知。
     *
     * @param selectionKey 商品核价或历史明细的规范化选择键
     * @param snapshot 结构化选择JSON数组，最多10组，每组最多一个选项
     * @param allowLegacyEmpty 是否允许旧订单缺失结构化信息
     * @return 校验通过的原JSON；历史null/空白统一为 []，不生成猜测选项
     * @throws IllegalArgumentException 快照损坏或与选择键不一致，调用方应回滚本次写入
     */
    public static String validate(String selectionKey, String snapshot, boolean allowLegacyEmpty) {
        String value = snapshot;
        if (value == null || value.isBlank()) {
            if (!allowLegacyEmpty) throw new IllegalArgumentException("核价缺少结构化规格快照");
            value = "[]";
        }
        String key = selectionKey == null || selectionKey.isBlank() ? "base" : selectionKey;
        try {
            var root = JSON.readTree(value);
            if (root == null || !root.isArray() || root.size() > 10) throw invalid();
            if (root.isEmpty() && allowLegacyEmpty) return value;
            Set<Long> groups = new HashSet<>();
            Set<Long> options = new HashSet<>();
            for (var row : root) {
                if (!row.isObject() || row.size() != 4
                        || !row.path("groupId").isIntegralNumber() || !row.path("groupId").canConvertToLong()
                        || row.path("groupId").longValue() <= 0
                        || !row.path("optionId").isIntegralNumber() || !row.path("optionId").canConvertToLong()
                        || row.path("optionId").longValue() <= 0
                        || !row.path("groupName").isTextual() || row.path("groupName").asText().isBlank()
                        || !row.path("optionName").isTextual() || row.path("optionName").asText().isBlank()
                        || !groups.add(row.path("groupId").longValue())
                        || !options.add(row.path("optionId").longValue())) throw invalid();
            }
            String expected = options.isEmpty() ? "base" : options.stream().sorted().map(String::valueOf)
                    .collect(Collectors.joining("-"));
            if (!expected.equals(key)) throw invalid();
            return value;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("结构化规格快照不是有效JSON", exception);
        }
    }

    /** 不在异常中回显用户选择或完整快照，避免日志扩散购买行为明细。 */
    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("结构化规格快照与选择键不一致");
    }
}

package com.hbue.ordering.recommendation.algorithm;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 仅把明确口味组与明确选项别名规范到跨菜品标签，未知商家文字不作猜测。 */
public class SpecificationPreferenceNormalizer {
    private static final Map<String, String> SPICE = Map.ofEntries(
            Map.entry("不辣", "SPICE:NONE"), Map.entry("免辣", "SPICE:NONE"), Map.entry("微辣", "SPICE:LIGHT"),
            Map.entry("少辣", "SPICE:LIGHT"), Map.entry("小辣", "SPICE:LIGHT"), Map.entry("中辣", "SPICE:MEDIUM"),
            Map.entry("正常辣", "SPICE:MEDIUM"), Map.entry("特辣", "SPICE:HOT"), Map.entry("重辣", "SPICE:HOT"),
            Map.entry("爆辣", "SPICE:HOT"));
    private static final Map<String, String> SUGAR = Map.ofEntries(
            Map.entry("无糖", "SUGAR:NONE"), Map.entry("不加糖", "SUGAR:NONE"), Map.entry("少糖", "SUGAR:LOW"),
            Map.entry("微糖", "SUGAR:LOW"), Map.entry("半糖", "SUGAR:HALF"), Map.entry("正常糖", "SUGAR:REGULAR"),
            Map.entry("标准糖", "SUGAR:REGULAR"), Map.entry("全糖", "SUGAR:REGULAR"));
    private static final Map<String, String> TEMPERATURE = Map.ofEntries(
            Map.entry("热", "TEMP:HOT"), Map.entry("温", "TEMP:WARM"), Map.entry("常温", "TEMP:ROOM"),
            Map.entry("去冰", "TEMP:NO_ICE"), Map.entry("少冰", "TEMP:LESS_ICE"), Map.entry("正常冰", "TEMP:REGULAR_ICE"),
            Map.entry("多冰", "TEMP:MORE_ICE"));
    private static final Set<String> FLAVOR_GROUPS = Set.of("口味", "风味", "酱料", "酱汁", "蘸料", "口味选择");
    /** 只把人工确认的常见口味别名跨菜归一；商家未收录的自由文本不自动变成画像标签。 */
    private static final Map<String, String> FLAVOR = Map.ofEntries(
            Map.entry("原味", "FLAVOR:ORIGINAL"), Map.entry("清淡", "FLAVOR:LIGHT"),
            Map.entry("香辣", "FLAVOR:AROMATIC_SPICY"), Map.entry("酸辣", "FLAVOR:SOUR_SPICY"),
            Map.entry("麻辣", "FLAVOR:NUMBING_SPICY"), Map.entry("蒜香", "FLAVOR:GARLIC"),
            Map.entry("葱香", "FLAVOR:SCALLION"), Map.entry("酱香", "FLAVOR:SAUCE"),
            Map.entry("咖喱", "FLAVOR:CURRY"), Map.entry("孜然", "FLAVOR:CUMIN"),
            Map.entry("黑椒", "FLAVOR:BLACK_PEPPER"), Map.entry("椒盐", "FLAVOR:SALT_PEPPER"),
            Map.entry("五香", "FLAVOR:FIVE_SPICE"), Map.entry("烧烤", "FLAVOR:BARBECUE"),
            Map.entry("番茄", "FLAVOR:TOMATO"), Map.entry("酸甜", "FLAVOR:SWEET_SOUR"),
            Map.entry("芝士", "FLAVOR:CHEESE"), Map.entry("咸蛋黄", "FLAVOR:SALTED_EGG_YOLK"));

    /**
     * 依据组名先确定语义，再匹配有限的受控别名；口味组里的“微辣”仍映射辣度，
     * 未收录的组名或选项一律忽略，避免把两个商家偶然同名的自由文本解释成相同偏好。
     */
    public String normalize(String groupName, String optionName) {
        String group = normalizeText(groupName);
        String option = normalizeText(optionName);
        if (group.isEmpty() || option.isEmpty()) return null;
        if (group.contains("辣") || group.equals("口味") && SPICE.containsKey(option)) return SPICE.get(option);
        if ((group.contains("糖") || group.contains("甜")) && SUGAR.containsKey(option)) return SUGAR.get(option);
        if ((group.contains("温") || group.contains("冰")) && TEMPERATURE.containsKey(option)) return TEMPERATURE.get(option);
        if (FLAVOR_GROUPS.contains(group)) return FLAVOR.get(option);
        return null;
    }

    /** 全角/半角、大小写及空白归一化；去掉分隔标点防止自由文本被拆成多个标签。 */
    private String normalizeText(String text) {
        if (text == null) return "";
        return Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Punct}，。；：、（）【】]+", "");
    }
}

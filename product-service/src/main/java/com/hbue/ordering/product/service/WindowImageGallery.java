package com.hbue.ordering.product.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.web.media.FileReferences;
import com.hbue.ordering.common.web.media.ManagedImageClient;
import com.hbue.ordering.product.model.DiningWindowDO;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * 店铺图片列表规则：按顺序保存最多五个文件键，第一张是封面。
 * 不存URL、不访问文件库；新增引用交给统一文件客户端验证，历史引用允许同店经营者保留及重排。
 */
public final class WindowImageGallery {
    /** 仅序列化字符串数组，不依赖日期、Web或业务对象的Jackson配置。 */
    private static final ObjectMapper JSON = new ObjectMapper();
    private WindowImageGallery() {}

    /** 验证并归一化完整列表；不截断输入，避免商家误以为第六张也保存成功。 */
    public static List<String> validate(List<String> requested) {
        if (requested == null || requested.size() > 5) throw invalid("店铺最多设置5张图片");
        List<String> keys = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        for (String value : requested) {
            String key = FileReferences.normalize(value);
            if (key == null || !seen.add(key)) throw invalid("店铺图片不能为空或重复");
            keys.add(key);
        }
        return keys;
    }

    /** 旧数据未迁移时从封面回退；空数组表示商家主动清空，不能再恢复旧封面。 */
    public static List<String> read(DiningWindowDO window) {
        if (window.getImageFileKeys() == null) {
            String cover = FileReferences.normalize(window.getImageFileKey());
            return cover == null ? List.of() : List.of(cover);
        }
        try {
            JsonNode node = JSON.readTree(window.getImageFileKeys());
            if (node == null || !node.isArray()) throw new IllegalStateException("店铺图片数据不是数组");
            List<String> keys = new ArrayList<>();
            for (JsonNode value : node) {
                if (!value.isTextual()) throw new IllegalStateException("店铺图片键不是字符串");
                keys.add(value.textValue());
            }
            return validate(keys);
        } catch (JsonProcessingException error) {
            // 损坏数据不能当空列表覆盖，必须交给统一异常处理并排查数据来源。
            throw new IllegalStateException("店铺图片数据无法读取", error);
        }
    }

    /** 校验新增图片的上传者、类型及窗口归属；任一失败即终止，不写部分列表。 */
    public static List<String> resolve(List<String> requested, List<String> existing,
            Long operatorId, Long windowId, ManagedImageClient files) {
        List<String> keys = validate(requested);
        for (String key : keys) {
            if (!existing.contains(key)) files.requireImageKey(key, operatorId, "WINDOW_IMAGE", windowId);
        }
        return keys;
    }

    /** 兼容旧单图操作：新图替换首图；已在列表的图只提升为封面，空串移除首图。 */
    public static List<String> withCover(String requested, List<String> existing) {
        if (requested == null) return existing;
        String key = FileReferences.normalize(requested);
        List<String> keys = new ArrayList<>(existing);
        // 提升已有图片只改变顺序，不能顺带丢失原封面；此操作不产生新的文件引用。
        if (key != null && keys.remove(key)) { keys.add(0, key); return validate(keys); }
        if (!keys.isEmpty()) keys.remove(0);
        if (key != null) keys.add(0, key);
        return validate(keys);
    }

    /** 数据库只保存文件键JSON；与封面列在同一条UPDATE中写入，保持原子一致。 */
    public static String json(List<String> keys) {
        try { return JSON.writeValueAsString(validate(keys)); }
        catch (JsonProcessingException error) { throw new IllegalStateException("店铺图片无法保存", error); }
    }

    /** 对外统一返回参数错误，不泄露文件服务或数据库实现细节。 */
    private static BusinessException invalid(String message) {
        return new BusinessException(CommonErrorCode.BAD_REQUEST, message);
    }
}

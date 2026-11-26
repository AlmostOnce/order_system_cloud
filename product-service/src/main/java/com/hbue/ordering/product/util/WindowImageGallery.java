package com.hbue.ordering.product.util;

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
 *
 * <p>不存 URL、不访问文件库；新增引用交给统一文件客户端验证，历史引用允许同店经营者保留及重排。
 * 全部方法为静态纯函数，因此放在 util 包而不是 service 包——它不是可注入的 Bean，
 * 误当作 Bean 注入会在启动时失败。</p>
 *
 * @author order-system
 * @date 2026-10-18
 */
public final class WindowImageGallery {

    /**
     * 单个店铺允许的最大图片数量，与前端和数据库约束保持一致。
     */
    private static final int MAX_IMAGE_COUNT = 5;

    /**
     * 窗口图片在文件服务中的业务类型。
     */
    private static final String WINDOW_IMAGE_BIZ_TYPE = "WINDOW_IMAGE";

    /**
     * 仅序列化字符串数组，不依赖日期、Web或业务对象的Jackson配置。
     */
    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * 工具类不允许实例化。
     */
    private WindowImageGallery() {
    }

    /**
     * 验证并归一化完整列表；不截断输入，避免商家误以为第六张也保存成功。
     *
     * @param requested 客户端提交的文件键列表，可空
     * @return 去重并保持提交顺序的文件键列表
     * @throws BusinessException 超过数量上限或存在空/重复键
     */
    public static List<String> validate(List<String> requested) {
        if (requested == null || requested.size() > MAX_IMAGE_COUNT) throw invalid("店铺最多设置5张图片");
        List<String> keys = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        for (String value : requested) {
            String key = FileReferences.normalize(value);
            if (key == null || !seen.add(key)) throw invalid("店铺图片不能为空或重复");
            keys.add(key);
        }
        return keys;
    }

    /**
     * 读取店铺当前图片列表。
     *
     * <p>旧数据未迁移时从封面列回退；{@code image_file_keys} 为空数组表示商家主动清空，
     * 此时不能再恢复旧封面，否则会出现"删掉又回来"的现象。</p>
     *
     * @param window 店铺持久化对象
     * @return 文件键列表，永不返回 null
     * @throws IllegalStateException 历史 JSON 损坏，交由统一异常处理并排查数据来源
     */
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

    /**
     * 校验新增图片的上传者、类型及窗口归属；任一失败即终止，不写部分列表。
     *
     * <p>已存在于列表中的历史引用跳过校验：它们可能在文件服务引入归属校验之前上传，
     * 强制校验会导致老店铺无法编辑任何图片。</p>
     *
     * @param requested  客户端提交的目标列表
     * @param existing   当前已保存的列表
     * @param operatorId 操作者内部用户编号
     * @param windowId   店铺编号，作为文件归属的业务引用
     * @param files      文件归属校验客户端
     * @return 校验通过的规范化列表
     */
    public static List<String> resolve(List<String> requested, List<String> existing,
                                       Long operatorId, Long windowId, ManagedImageClient files) {
        List<String> keys = validate(requested);
        for (String key : keys) {
            if (!existing.contains(key)) files.requireImageKey(key, operatorId, WINDOW_IMAGE_BIZ_TYPE, windowId);
        }
        return keys;
    }

    /**
     * 兼容旧单图操作：新图替换首图；已在列表的图只提升为封面，空串移除首图。
     *
     * @param requested 新封面文件键；null 表示不修改，空串表示移除封面
     * @param existing  当前列表
     * @return 调整后的列表
     */
    public static List<String> withCover(String requested, List<String> existing) {
        if (requested == null) return existing;
        String key = FileReferences.normalize(requested);
        List<String> keys = new ArrayList<>(existing);
        // 提升已有图片只改变顺序，不能顺带丢失原封面；此操作不产生新的文件引用。
        if (key != null && keys.remove(key)) {
            keys.add(0, key);
            return validate(keys);
        }
        if (!keys.isEmpty()) keys.remove(0);
        if (key != null) keys.add(0, key);
        return validate(keys);
    }

    /**
     * 序列化为数据库列值；与封面列在同一条 UPDATE 中写入，保持原子一致。
     *
     * @param keys 文件键列表
     * @return JSON 数组字符串
     */
    public static String json(List<String> keys) {
        try {
            return JSON.writeValueAsString(validate(keys));
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("店铺图片无法保存", error);
        }
    }

    /**
     * 对外统一返回参数错误，不泄露文件服务或数据库实现细节。
     *
     * @param message 面向用户的提示
     * @return 参数错误业务异常
     */
    private static BusinessException invalid(String message) {
        return new BusinessException(CommonErrorCode.BAD_REQUEST, message);
    }
}

package com.hbue.ordering.support.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.web.media.ManagedImageClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 反馈图片校验与展示地址派生。
 *
 * <p>工单只保存文件键，不保存可伪造的外部地址。提交与补充前逐个调用文件服务
 * 确认图片由当前操作者以 SUPPORT_IMAGE 类型上传且 bizRef 为空；任何一张不满足
 * 即整体拒绝，避免把他人图片挂到自己的工单上。详情返回时再按文件键派生
 * 网关下的相对内容地址，客户端可自行拼接当前主机。</p>
 *
 * @author order-system
 * @date 2026-11-23
 */
@Service
public class SupportImageService {

    /**
     * 工单图片在文件服务中的业务类型。
     */
    private static final String SUPPORT_IMAGE_BIZ_TYPE = "SUPPORT_IMAGE";

    /**
     * 单次提交允许的最大图片数量，与前端和数据库约束保持一致。
     */
    private static final int MAX_IMAGE_COUNT = 3;

    /**
     * 文件归属校验客户端。
     */
    private final ManagedImageClient managedImageClient;

    /**
     * JSON 序列化组件，用于读写数据库中的图片键数组。
     */
    private final ObjectMapper objectMapper;

    /**
     * 创建图片服务。
     *
     * @param managedImageClient 文件归属校验客户端
     * @param objectMapper       应用共享 JSON 序列化组件
     */
    public SupportImageService(ManagedImageClient managedImageClient, ObjectMapper objectMapper) {
        this.managedImageClient = managedImageClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 去重、限流并校验图片归属。
     *
     * <p>先按顺序去重再校验数量，重复键不会占用名额；任一键校验失败直接抛出
     * 业务异常，调用方不应在失败后继续写工单。返回顺序保持客户端提交顺序，
     * 便于前端按原顺序展示。</p>
     *
     * @param rawKeys    客户端提交的文件键，可空
     * @param uploaderId 当前操作者内部用户编号
     * @return 规范化后的文件键列表
     */
    public List<String> validateAndNormalize(List<String> rawKeys, Long uploaderId) {
        if (rawKeys == null || rawKeys.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> uniqueKeys = new LinkedHashSet<>();
        for (String rawKey : rawKeys) {
            if (rawKey == null || rawKey.isBlank()) {
                throw new BusinessException(CommonErrorCode.BAD_REQUEST, "图片文件键不能为空");
            }
            uniqueKeys.add(rawKey.trim());
        }
        if (uniqueKeys.size() > MAX_IMAGE_COUNT) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "最多上传 3 张图片");
        }
        List<String> normalizedKeys = new ArrayList<>(uniqueKeys.size());
        for (String key : uniqueKeys) {
            // 文件服务会同时校验格式、上传人、业务类型与 bizRef 是否为空。
            normalizedKeys.add(managedImageClient.requireImageKey(
                    key, uploaderId, SUPPORT_IMAGE_BIZ_TYPE, null));
        }
        return List.copyOf(normalizedKeys);
    }

    /**
     * 将文件键列表序列化为数据库 JSON 数组。
     *
     * @param keys 文件键列表，可空
     * @return JSON 字符串；空列表返回 {@code []}
     */
    public String toJson(List<String> keys) {
        try {
            return objectMapper.writeValueAsString(keys == null ? List.of() : keys);
        } catch (JsonProcessingException exception) {
            // 文件键均为简单字符串，序列化失败说明运行环境异常，不能静默写入空数组。
            throw new IllegalStateException("序列化工单图片文件键失败", exception);
        }
    }

    /**
     * 解析数据库中的图片键 JSON 数组。
     *
     * <p>历史数据损坏时返回空列表并记录由调用方决定是否告警，不影响工单主体展示。</p>
     *
     * @param json 数据库 JSON 字符串
     * @return 文件键列表
     */
    public List<String> parseKeys(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> keys = objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
            return keys == null ? List.of() : keys;
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    /**
     * 把文件键派生为网关下的相对内容地址。
     *
     * @param keys 文件键列表
     * @return 可直接用于图片组件的相对地址
     */
    public List<String> toDisplayUrls(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        List<String> urls = new ArrayList<>(keys.size());
        for (String key : keys) {
            urls.add("/api/files/" + key + "/content");
        }
        return urls;
    }
}

package com.hbue.ordering.support.service;

import java.util.List;

/**
 * 反馈图片的校验、序列化与展示地址派生。
 *
 * <p>工单只保存文件键，不保存可伪造的外部地址。提交与补充前必须逐个校验图片确实由当前操作者
 * 以 SUPPORT_IMAGE 类型上传，任何一张不满足即整体拒绝，避免把他人图片挂到自己的工单上。</p>
 *
 * <p>JSON 编解码与地址拼装也收在本接口内，使工单服务与视图转换器对"图片列存什么格式"保持一致口径。</p>
 */
public interface SupportImageService {

    /**
     * 去重、限流并校验图片归属。
     *
     * <p>先按顺序去重再校验数量，重复键不占用名额；返回顺序保持客户端提交顺序，便于前端按原顺序展示。</p>
     *
     * @param rawKeys    客户端提交的文件键，可空
     * @param uploaderId 当前操作者内部用户编号
     * @return 规范化后的文件键列表
     */
    List<String> validateAndNormalize(List<String> rawKeys, Long uploaderId);

    /**
     * 将文件键列表序列化为数据库 JSON 数组。
     *
     * @param keys 文件键列表，可空
     * @return JSON 字符串；空列表返回 {@code []}
     */
    String toJson(List<String> keys);

    /**
     * 解析数据库中的图片键 JSON 数组。
     *
     * <p>历史数据损坏时返回空列表并记录告警日志，不影响工单主体展示。</p>
     *
     * @param json 数据库 JSON 字符串
     * @return 文件键列表，永不返回 null
     */
    List<String> parseKeys(String json);

    /**
     * 把文件键派生为网关下的相对内容地址。
     *
     * @param keys 文件键列表
     * @return 可直接用于图片组件的相对地址
     */
    List<String> toDisplayUrls(List<String> keys);
}

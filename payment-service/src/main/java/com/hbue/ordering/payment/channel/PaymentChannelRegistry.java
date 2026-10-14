package com.hbue.ordering.payment.channel;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 支付渠道注册表。
 *
 * <p>把所有 {@link PaymentChannel} 实现按渠道标识登记，业务层通过渠道标识
 * 路由到具体实现，不感知渠道差异。新增渠道只需新增一个实现类，
 * 无需改动支付编排逻辑。</p>
 *
 * @author order-system
 * @date 2026-10-14
 */
@Component
public class PaymentChannelRegistry {

    /** 按渠道标识索引的渠道实现。 */
    private final Map<String, PaymentChannel> channels;

    /**
     * 创建支付渠道注册表。
     *
     * @param channelList Spring 容器中的全部渠道实现
     */
    public PaymentChannelRegistry(List<PaymentChannel> channelList) {
        Map<String, PaymentChannel> registered = new LinkedHashMap<>();
        for (PaymentChannel channel : channelList) {
            // 按渠道标识登记策略实现，后续由请求中的渠道码选择对应渠道。
            registered.put(channel.channelCode(), channel);
        }
        this.channels = Collections.unmodifiableMap(registered);
    }

    /**
     * 按渠道标识查找渠道实现。
     *
     * @param channelCode 渠道标识
     * @return 渠道实现；不存在时返回 null
     */
    public PaymentChannel find(String channelCode) {
        if (channelCode == null || channelCode.isBlank()) {
            return null;
        }
        // 去除首尾空格并统一大小写，确保请求值与注册表中的渠道码匹配。
        return channels.get(channelCode.trim().toUpperCase());
    }

    /**
     * 按渠道标识获取渠道实现，不存在时按参数错误拒绝。
     *
     * @param channelCode 渠道标识
     * @return 渠道实现
     */
    public PaymentChannel require(String channelCode) {
        PaymentChannel channel = find(channelCode);
        if (channel == null) {
            // 将未知渠道统一转换为业务参数错误，避免调用方继续使用空渠道实现。
            throw new BusinessException(
                    CommonErrorCode.BAD_REQUEST,
                    "不支持的支付渠道：" + channelCode
            );
        }
        return channel;
    }
}

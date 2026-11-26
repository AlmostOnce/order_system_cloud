package com.hbue.ordering.order.controller;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.order.mapper.OrderReviewCandidateMapper;
import com.hbue.ordering.order.security.InternalTokenVerifier;
import com.hbue.ordering.order.vo.ReviewCandidateVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 待评价统计的订单候选内部接口。
 *
 * <p>评论服务本身不保存订单，无法判断“哪些已完成订单还没评价”，因此由订单服务提供
 * 最近已完成订单的明细编号，评论服务再用自己的评价表判断缺失项。该接口没有用户 JWT，
 * 通过 {@code X-Internal-Token} 共享密钥认证，且 Gateway 不对外暴露。</p>
 *
 * <p>只返回订单号、档口号和菜品号，不返回金额、收货信息或用户资料；{@code userId}
 * 由评论服务从已认证身份解析后传入，不能由终端用户直接指定他人编号。</p>
 *
 * @author order-system
 * @date 2026-11-26
 */
@RestController
@Tag(name = "内部接口（服务间调用）")
@RequestMapping("/internal/orders/review-candidates")
public class OrderReviewCandidateController {

    /**
     * 内部令牌请求头名称，与订单其他内部接口保持一致。
     */
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    /**
     * 允许调用方请求的最大订单数，防止异常参数把整段历史订单拉进内存。
     *
     * <p>默认值 200 直接写在接口参数上：待评价数量用于个人中心入口角标，
     * 不需要精确遍历全部历史订单；最近 200 单已覆盖绝大多数活跃用户。</p>
     */
    private static final int MAX_LIMIT = 500;

    /**
     * 已完成订单明细只读查询。
     */
    private final OrderReviewCandidateMapper candidates;

    /**
     * 内部调用共享密钥校验器。
     */
    private final InternalTokenVerifier internalTokenVerifier;

    /**
     * 创建待评价候选接口。
     *
     * @param candidates            订单明细只读查询
     * @param internalTokenVerifier 内部调用共享密钥校验器
     */
    public OrderReviewCandidateController(OrderReviewCandidateMapper candidates,
                                          InternalTokenVerifier internalTokenVerifier) {
        this.candidates = candidates;
        this.internalTokenVerifier = internalTokenVerifier;
    }

    /**
     * 查询用户最近已完成订单的明细编号。
     *
     * <p>同一订单有几道菜就返回几行；调用方必须按订单聚合。数量上限由服务端再次收紧，
     * 即使调用方传入负数或超大值也不会放大查询范围。用户编号非法时直接拒绝，
     * 不把“没有订单”和“参数错误”混成同一个空结果。</p>
     *
     * @param token  服务间共享令牌
     * @param userId 用户内部编号
     * @param limit  最多扫描的订单数，默认 200，最大 500
     * @return 已完成订单明细候选，没有订单时为空集合
     */
    @GetMapping
    public ApiResponse<List<ReviewCandidateVO>> candidates(
            @RequestHeader(value = INTERNAL_TOKEN_HEADER, required = false)
            String token,
            @RequestParam("userId") Long userId,
            @RequestParam(name = "limit", defaultValue = "200")
            int limit
    ) {
        verifyInternalToken(token);
        if (userId == null || userId <= 0) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "用户编号必须为正数");
        }
        int boundedLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);
        return ApiResponse.success(candidates.selectCompletedOrderItems(userId, boundedLimit));
    }

    /**
     * 校验内部调用令牌。
     *
     * <p>校验失败返回 401 而不是空数据：调用方应把配置错误暴露出来，
     * 不能让评论服务把“订单服务拒绝访问”误判成“用户没有待评价订单”。</p>
     *
     * @param token 请求头中的内部令牌
     */
    private void verifyInternalToken(String token) {
        if (!internalTokenVerifier.verify(token)) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "内部调用令牌无效");
        }
    }
}

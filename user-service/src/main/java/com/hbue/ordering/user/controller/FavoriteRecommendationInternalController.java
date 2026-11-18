package com.hbue.ordering.user.controller;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.common.web.security.InternalScopeTokenVerifier;
import com.hbue.ordering.user.service.FavoriteRecommendationExportService;
import com.hbue.ordering.user.vo.FavoriteRecommendationExportPageVO;
import org.springframework.web.bind.annotation.*;

/**
 * 推荐服务历史收藏回填入口；网关不转发内部路径，直连仍必须校验内部共享凭据。
 */
@RestController
@RequestMapping("/internal/users/recommendations")
public class FavoriteRecommendationInternalController {
    /**
     * 沿用项目已有内部令牌校验，缺失或不匹配均拒绝。
     */
    private final InternalScopeTokenVerifier verifier;
    /**
     * 有界查询和一致读由专门服务处理。
     */
    private final FavoriteRecommendationExportService export;

    /**
     * 注入令牌验证与只读导出服务，不接收客户端身份覆盖字段。
     */
    public FavoriteRecommendationInternalController(InternalScopeTokenVerifier verifier,
                                                    FavoriteRecommendationExportService export) {
        this.verifier = verifier;
        this.export = export;
    }

    /**
     * 返回当前收藏的内部游标页；每次最多 100 行，结果不可作为全局删除快照。
     *
     * @param token           内部服务共享凭据，缺失时也必须由验证器拒绝
     * @param afterFavoriteId 前一页末尾收藏编号，首屏默认为零
     * @param watermark       首屏省略、后续页传回的首屏收藏编号上界
     * @param limit           每页大小，服务层校验 1 至 100
     * @return 首屏上界、逐关系版本、可见收藏和下一游标
     */
    @GetMapping("/favorites")
    public ApiResponse<FavoriteRecommendationExportPageVO> favorites(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestParam(value = "afterFavoriteId", defaultValue = "0") long afterFavoriteId,
            @RequestParam(value = "watermark", required = false) Long watermark,
            @RequestParam(value = "limit", defaultValue = "100") int limit) {
        verifier.verify(token);
        return ApiResponse.success(export.list(afterFavoriteId, watermark, limit));
    }
}

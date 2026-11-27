package com.hbue.ordering.user.service.impl;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.user.mapper.FavoriteRecommendationExportMapper;
import com.hbue.ordering.user.model.FavoriteRecommendationExportRow;
import com.hbue.ordering.user.model.FavoriteRecommendationWatermarks;
import com.hbue.ordering.user.service.FavoriteRecommendationExportService;
import com.hbue.ordering.user.vo.FavoriteRecommendationExportPageVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 推荐服务回填编排；本服务只导出当前存在关系，不将跨页缺失解释为取消。
 */
@Service
public class FavoriteRecommendationExportServiceImpl implements FavoriteRecommendationExportService {
    /**
     * 控制内部分页上限，避免一次性导出全部用户收藏。
     */
    private static final int MAX_PAGE_SIZE = 100;
    /**
     * 只读 Mapper，所有查询使用用户库而不是跨服务读库。
     */
    private final FavoriteRecommendationExportMapper mapper;

    /**
     * 注入收藏回填查询，接口调用方不得通过服务层指定其他数据库。
     */
    public FavoriteRecommendationExportServiceImpl(FavoriteRecommendationExportMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 读取一页当前收藏；首屏上界与明细处于同一可重复读事务，后续各页是独立快照。
     *
     * @param afterFavoriteId 收藏主键游标，首屏传零
     * @param watermark       首屏省略、后续原样传回的收藏主键上界
     * @param limit           希望返回条数，范围 1 至 100
     * @return 按收藏主键递增的页面；行版本来自该关系可见的最新事件
     * @throws BusinessException 参数非法时返回标准请求错误
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @Override
    public FavoriteRecommendationExportPageVO list(long afterFavoriteId, Long watermark, int limit) {
        if (afterFavoriteId < 0 || limit < 1 || limit > MAX_PAGE_SIZE
                || (watermark != null && (watermark < 0 || afterFavoriteId > watermark))
                || (afterFavoriteId > 0 && watermark == null)) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "收藏回填游标或页大小无效");
        }
        Long observedOutboxWatermark = null;
        if (watermark == null) {
            // 两个最大值在同一一致读中取得；发件箱 ID 仅为观测值，不能过滤迟提交的旧编号事件。
            FavoriteRecommendationWatermarks bounds = mapper.readWatermarks();
            if (bounds == null || bounds.getFavoriteWatermark() == null || bounds.getObservedOutboxWatermark() == null) {
                throw new IllegalStateException("无法读取收藏回填上界");
            }
            watermark = bounds.getFavoriteWatermark();
            observedOutboxWatermark = bounds.getObservedOutboxWatermark();
        }
        // 固定收藏主键上界防止扫描无限增长；多取一条判断 hasMore，不将探测行返回。
        List<FavoriteRecommendationExportRow> rows = mapper.selectPage(afterFavoriteId, watermark, limit + 1);
        boolean hasMore = rows.size() > limit;
        int visible = Math.min(rows.size(), limit);
        List<FavoriteRecommendationExportPageVO.Item> items = new ArrayList<>(visible);
        for (int index = 0; index < visible; index++) {
            FavoriteRecommendationExportRow row = rows.get(index);
            items.add(new FavoriteRecommendationExportPageVO.Item(row.getFavoriteId(), row.getUserId(),
                    row.getTargetType(), row.getTargetId(), true, row.getCreatedAt(), row.getSourceVersion()));
        }
        Long nextCursor = hasMore ? rows.get(visible - 1).getFavoriteId() : null;
        return new FavoriteRecommendationExportPageVO(watermark, observedOutboxWatermark, items, nextCursor, hasMore);
    }
}

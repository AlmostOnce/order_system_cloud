package com.hbue.ordering.comment.mapper;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 评分批量 SQL 的口径回归测试。
 *
 * <p>窗口评价是软删除（{@code status = 2}），批量统计与单条详情必须使用同一口径。本用例读取
 * {@link RatingBatchMapper#windows(List)} 注解中的 SQL 文本做静态校验，不依赖数据库；
 * 一旦有人删掉 {@code AND status = 1}，删除评价又会污染批量平均分，用例会立即变红。</p>
 */
class RatingBatchMapperSqlTest {

    /**
     * 窗口批量评分 SQL 必须包含 {@code status = 1}，只统计未删除的评价。
     */
    @Test
    void windowRatingQueryExcludesSoftDeletedReviews() throws NoSuchMethodException {
        String sql = RatingBatchMapper.class.getMethod("windows", List.class)
                .getAnnotation(Select.class).value()[0];
        assertTrue(sql.contains("status = 1"), "窗口批量评分必须只统计未删除评价，实际 SQL：" + sql);
    }
}

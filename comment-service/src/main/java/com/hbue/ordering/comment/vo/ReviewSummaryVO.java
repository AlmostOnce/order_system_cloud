package com.hbue.ordering.comment.vo;

/**
 * 单一菜品或店铺全部可见评价的概览，不包含评价正文或用户身份。
 * reviewCount包含1～5星；positiveCount仅五星，negativeCount仅一星，二者不保证相加等于总数。
 * imageCount是带图评价条数；存在尚未回填的记录时为null，不能显示成0。
 * 无评价时averageScore为null、所有数量为0，由同一聚合SQL返回，避免分次统计口径漂移。
 */
public record ReviewSummaryVO(long reviewCount, Double averageScore, long positiveCount, long negativeCount, Long imageCount) {}

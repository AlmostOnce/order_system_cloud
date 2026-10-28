package com.hbue.ordering.search.vo;

import java.util.List;

public record ProductSearchPageVO(String keyword, int page, int size, long total, List<ProductSearchResultVO> items) {}

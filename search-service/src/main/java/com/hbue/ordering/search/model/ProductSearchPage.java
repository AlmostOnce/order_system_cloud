package com.hbue.ordering.search.model;

import java.util.List;

public record ProductSearchPage(String keyword, int page, int size, long total, List<ProductSearchResult> items) {}

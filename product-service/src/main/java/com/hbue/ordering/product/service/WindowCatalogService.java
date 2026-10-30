package com.hbue.ordering.product.service;

import java.util.List;
import java.util.Map;

public interface WindowCatalogService {
    Map<Long, String> names(List<Long> ids);
}

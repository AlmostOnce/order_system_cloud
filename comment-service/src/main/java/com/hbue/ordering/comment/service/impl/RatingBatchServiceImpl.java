package com.hbue.ordering.comment.service.impl;

import com.hbue.ordering.comment.repository.RatingBatchRepository;
import com.hbue.ordering.comment.service.RatingBatchService;
import com.hbue.ordering.comment.vo.RatingBatchItemVO;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RatingBatchServiceImpl implements RatingBatchService {
    private final RatingBatchRepository repository;
    public RatingBatchServiceImpl(RatingBatchRepository repository) { this.repository = repository; }
    public List<RatingBatchItemVO> get(String kind, List<Long> ids) {
        if ((!"windows".equals(kind) && !"products".equals(kind)) || ids == null || ids.isEmpty() || ids.size() > 100 || ids.stream().anyMatch(id -> id == null || id <= 0))
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "评分查询参数不正确");
        var rows = "windows".equals(kind) ? repository.windows(ids) : repository.products(ids);
        return rows.stream().map(row -> new RatingBatchItemVO(row.id(), row.count(), row.average())).toList();
    }
}

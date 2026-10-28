package com.hbue.ordering.comment.service;

import com.hbue.ordering.comment.vo.RatingBatchItemVO;
import java.util.List;

public interface RatingBatchService {
    List<RatingBatchItemVO> get(String kind, List<Long> ids);
}

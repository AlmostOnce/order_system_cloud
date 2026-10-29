package com.hbue.ordering.comment.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.comment.client.CommentOrderClient;
import com.hbue.ordering.comment.dto.CreateWindowReviewRequest;
import com.hbue.ordering.comment.mapper.WindowReviewMapper;
import com.hbue.ordering.comment.mapper.WindowReviewMapper.Row;
import com.hbue.ordering.comment.service.WindowReviewService;
import com.hbue.ordering.comment.vo.WindowReviewPageVO;
import com.hbue.ordering.comment.vo.WindowReviewVO;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.web.media.FileReferences;
import com.hbue.ordering.common.web.media.ManagedImageClient;
import feign.FeignException;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class WindowReviewServiceImpl implements WindowReviewService {
    private final WindowReviewMapper reviews;
    private final CommentOrderClient orders;
    private final ManagedImageClient images;
    private final ObjectMapper json;
    public WindowReviewServiceImpl(WindowReviewMapper reviews, CommentOrderClient orders, ManagedImageClient images, ObjectMapper json) {
        this.reviews = reviews; this.orders = orders; this.images = images; this.json = json;
    }
    public WindowReviewVO create(Long userId, String author, String bearer, String key, CreateWindowReviewRequest input) {
        CommentServiceImpl.requireUuid(key);
        var body = new CreateWindowReviewRequest(input.orderId(), input.score(), input.content().trim(),
                input.imageFileKeys().stream().map(k -> k.toLowerCase(Locale.ROOT)).distinct().toList(),
                input.tags().stream().map(String::trim).distinct().toList());
        if (body.content().isEmpty() || body.tags().stream().anyMatch(String::isEmpty)) throw bad("评价内容或标签不能为空");
        String hash;
        try { hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(body))); }
        catch (Exception e) { throw new IllegalStateException("评价内容编码失败", e); }
        Row existing = reviews.byRequest(userId, key);
        if (existing != null) return replay(existing, hash);
        var order = fetchOrder(body.orderId(), bearer);
        List<String> keys = body.imageFileKeys().stream().map(k -> images.requireImageKey(k, userId, "COMMENT_IMAGE", null)).toList();
        String name = author == null || author.isBlank() ? "校园用户" : author.substring(0, Math.min(64, author.length()));
        try {
            var row = new Row(UUID.randomUUID().toString(), userId, name, body.orderId(), order.windowId(), body.score(),
                    body.content(), json.writeValueAsString(keys), json.writeValueAsString(body.tags()), key, hash, LocalDateTime.now());
            try { reviews.insert(row); }
            catch (DuplicateKeyException e) {
                existing = reviews.byRequest(userId, key);
                if (existing != null) return replay(existing, hash);
                throw new BusinessException(CommonErrorCode.CONFLICT, "该订单已经评价过店铺");
            }
            return view(row);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException("评价内容编码失败", e); }
    }
    private com.hbue.ordering.order.api.vo.OrderReviewSnapshotVO fetchOrder(Long id, String bearer) {
        try {
            var response = orders.getOrder(id, bearer);
            if (response == null || !"COMMON_000".equals(response.getCode()) || response.getData() == null)
                throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "无法确认订单，请稍后重试");
            var order = response.getData();
            if (!id.equals(order.orderId()) || order.windowId() == null || order.windowId() <= 0)
                throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "订单信息不完整");
            if (!"已完成".equals(order.status())) throw new BusinessException(CommonErrorCode.CONFLICT, "订单完成后才能评价");
            return order;
        } catch (FeignException e) {
            var code = e.status() == 401 ? CommonErrorCode.UNAUTHORIZED : (e.status() == 403 || e.status() == 404 ? CommonErrorCode.NOT_FOUND : CommonErrorCode.SERVICE_UNAVAILABLE);
            throw new BusinessException(code, "无法确认本人订单");
        }
    }
    public WindowReviewPageVO page(Long userId, Long windowId, String cursor, int limit) {
        if (limit < 1 || limit > 50 || (windowId != null && windowId <= 0)) throw bad("分页参数不正确");
        if (cursor != null) CommentServiceImpl.requireUuid(cursor);
        var rows = reviews.page(userId, windowId, cursor, limit + 1);
        boolean more = rows.size() > limit;
        var items = rows.stream().limit(limit).map(this::view).toList();
        return new WindowReviewPageVO(items, more ? items.getLast().reviewId() : null, more);
    }
    public com.hbue.ordering.comment.vo.WindowRatingVO rating(Long windowId) {
        var rating = reviews.rating(windowId);
        return new com.hbue.ordering.comment.vo.WindowRatingVO(rating.reviewCount(), rating.averageScore());
    }
    private WindowReviewVO replay(Row row, String hash) {
        if (!hash.equals(row.requestHash())) throw new BusinessException(CommonErrorCode.CONFLICT, "请求标识已用于其他评价内容");
        return view(row);
    }
    private WindowReviewVO view(Row row) {
        try {
            List<String> keys = json.readValue(row.images(), new TypeReference<List<String>>() {});
            List<String> tags = json.readValue(row.tags(), new TypeReference<List<String>>() {});
            return new WindowReviewVO(row.id(), row.windowId(), row.author(), row.score(), row.content(),
                    keys.stream().map(FileReferences::url).toList(), tags, row.createdAt());
        } catch (Exception e) { throw new IllegalStateException("评价内容读取失败", e); }
    }
    private static BusinessException bad(String message) { return new BusinessException(CommonErrorCode.BAD_REQUEST, message); }
}

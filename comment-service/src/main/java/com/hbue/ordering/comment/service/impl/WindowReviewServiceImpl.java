package com.hbue.ordering.comment.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.comment.client.CommentOrderClient;
import com.hbue.ordering.comment.dto.CreateWindowReviewRequest;
import com.hbue.ordering.comment.dto.UpdateWindowReviewRequest;
import com.hbue.ordering.comment.mapper.WindowReviewMapper;
import com.hbue.ordering.comment.mapper.WindowReviewMapper.Row;
import com.hbue.ordering.comment.model.ReviewType;
import com.hbue.ordering.comment.service.AuthorAvatarService;
import com.hbue.ordering.comment.service.ReviewAuthorityService;
import com.hbue.ordering.comment.service.ReviewDiscussionService;
import com.hbue.ordering.comment.service.WindowReviewService;
import com.hbue.ordering.comment.vo.WindowReviewPageVO;
import com.hbue.ordering.comment.vo.WindowReviewVO;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.web.media.FileReferences;
import com.hbue.ordering.common.web.media.ManagedImageClient;
import feign.FeignException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 店铺评价的创建、列表、修改、删除与汇总实现。
 *
 * <p>创建时先归一化（去空白、图片键转小写去重、标签去重）再计算内容摘要，因此大小写或重复项
 * 差异不会绕过幂等；随后校验图片归属、向订单服务确认已完成订单，最后落库。并发首提由唯一键兜底，
 * 捕获冲突后回读既有记录，保证同键重试拿到的是同一条评价。</p>
 *
 * <p>修改只覆盖评分、正文、图片与标签，不改变所评价的订单与档口——"确实消费过"的结论不能被编辑绕过。
 * 删除为软删除：记录与正文保留（店铺评价正文本来就存在 MySQL），但所有查询都带
 * {@code status = 1}，删除后立刻从列表、评分和汇总中消失。</p>
 *
 * <p>列表采用有界游标分页，评分筛选只接受 1 或 5；汇总统计不受当前页筛选影响，
 * 否则用户切换筛选后"总评分"会跟着变，容易被误解为数据错误。</p>
 *
 * @author order-system
 * @date 2026-10-28
 */
@Service
public class WindowReviewServiceImpl implements WindowReviewService {

    /**
     * 店铺评价读写。
     */
    private final WindowReviewMapper reviews;

    /**
     * 订单服务内部客户端，用于确认消费资格。
     */
    private final CommentOrderClient orders;

    /**
     * 图片归属校验客户端。
     */
    private final ManagedImageClient images;

    /**
     * JSON 编解码器，用于计算内容摘要与读写标签/图片列。
     */
    private final ObjectMapper json;

    /**
     * 作者头像批量解析。
     */
    private final AuthorAvatarService authors;

    /**
     * 修改与删除的操作者鉴权。
     */
    private final ReviewAuthorityService authority;

    /**
     * 自由评论区服务，用于拼装评价的评论数与预览。
     */
    private final ReviewDiscussionService discussions;

    /**
     * 无评论时的空汇总，避免每次新建对象。
     */
    private static final ReviewDiscussionService.DiscussionSummary EMPTY_DISCUSSION =
            new ReviewDiscussionService.DiscussionSummary(0, List.of());

    /**
     * 创建店铺评价服务。
     *
     * @param reviews     店铺评价读写
     * @param orders      订单服务内部客户端
     * @param images      图片归属校验
     * @param json        JSON 编解码器
     * @param authors     作者头像批量解析
     * @param authority   修改与删除的操作者鉴权
     * @param discussions 自由评论区服务
     */
    public WindowReviewServiceImpl(WindowReviewMapper reviews, CommentOrderClient orders, ManagedImageClient images,
                                   ObjectMapper json, AuthorAvatarService authors, ReviewAuthorityService authority,
                                   ReviewDiscussionService discussions) {
        this.reviews = reviews;
        this.orders = orders;
        this.images = images;
        this.json = json;
        this.authors = authors;
        this.authority = authority;
        this.discussions = discussions;
    }

    /**
     * 创建店铺评价。
     *
     * @param userId 当前登录用户编号
     * @param author 展示用作者名快照
     * @param bearer 原始 Authorization 头
     * @param key    客户端幂等键
     * @param input  评分、内容与图片
     * @return 新建评价视图
     */
    @Override
    public WindowReviewVO create(Long userId, String author, String bearer, String key, CreateWindowReviewRequest input) {
        CommentServiceImpl.requireUuid(key);
        var body = new CreateWindowReviewRequest(input.orderId(), input.score(), input.content() == null ? "" : input.content().trim(),
                (input.imageFileKeys() == null ? List.<String>of() : input.imageFileKeys()).stream().map(k -> k.toLowerCase(Locale.ROOT)).distinct().toList(),
                (input.tags() == null ? List.<String>of() : input.tags()).stream().map(String::trim).distinct().toList());
        if (body.tags().stream().anyMatch(String::isEmpty)) throw bad("评价标签不能为空");
        String hash;
        try {
            hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(body)));
        } catch (Exception e) {
            throw new IllegalStateException("评价内容编码失败", e);
        }
        Row existing = reviews.byRequest(userId, key);
        if (existing != null) return replay(existing, hash);
        var order = fetchOrder(body.orderId(), bearer);
        List<String> keys = body.imageFileKeys().stream().map(k -> images.requireImageKey(k, userId, "COMMENT_IMAGE", null)).toList();
        String name = author == null || author.isBlank() ? "校园用户" : author.substring(0, Math.min(64, author.length()));
        try {
            var row = new Row(UUID.randomUUID().toString(), userId, name, body.orderId(), order.windowId(), body.score(),
                    body.content(), json.writeValueAsString(keys), json.writeValueAsString(body.tags()), key, hash, LocalDateTime.now());
            try {
                reviews.insert(row);
            } catch (DuplicateKeyException e) {
                existing = reviews.byRequest(userId, key);
                if (existing != null) return replay(existing, hash);
                throw new BusinessException(CommonErrorCode.CONFLICT, "该订单已经评价过店铺");
            }
            return view(row);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("评价内容编码失败", e);
        }
    }

    /**
     * 修改一条已发布的店铺评价。
     *
     * <p>整体单库单事务：评分、正文、图片与标签一次 UPDATE 写入，不存在部分成功的中间态。
     * 返回 0 行说明评价已被删除或并发状态已变化，此时按冲突失败，不能用"没改到"冒充成功。</p>
     *
     * @param operatorId 当前登录用户编号
     * @param roles      当前登录用户的角色编码集合
     * @param reviewId   公开评价标识
     * @param input      修改参数
     * @return 修改后的评价视图
     */
    @Override
    public WindowReviewVO revise(Long operatorId, List<String> roles, String reviewId, UpdateWindowReviewRequest input) {
        CommentServiceImpl.requireUuid(reviewId);
        if (input == null) throw bad("修改参数不能为空");
        Row row = reviews.byId(reviewId);
        if (row == null) throw new BusinessException(CommonErrorCode.NOT_FOUND, "评价不存在");
        authority.requireManageable(operatorId, roles, row.userId(), row.windowId());

        List<String> currentKeys = readList(row.images());
        List<String> currentTags = readList(row.tags());
        // 未传的字段保持原值；传空数组表示主动清空，二者语义不同。
        String body = input.content() == null ? row.content() : input.content().trim();
        List<String> keys = input.imageFileKeys() == null ? currentKeys : normalizeKeys(input.imageFileKeys());
        List<String> tags = input.tags() == null ? currentTags : normalizeTags(input.tags());

        // 只校验新增图片的归属；评价里已有的图片仍归原作者所有，允许保留。
        for (String key : keys) {
            if (!currentKeys.contains(key)) {
                images.requireImageKey(key, operatorId, "COMMENT_IMAGE", null);
            }
        }
        boolean changed = row.score() != input.score()
                || !Objects.equals(body, row.content())
                || !Objects.equals(keys, currentKeys)
                || !Objects.equals(tags, currentTags);
        if (!changed) {
            // 内容与评分都没变：直接返回原视图，避免无意义的 UPDATE 与"并发冲突"误报。
            return view(row);
        }

        String imagesJson = writeList(keys);
        String tagsJson = writeList(tags);
        if (reviews.update(reviewId, input.score(), body, imagesJson, tagsJson) != 1) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "评价已被删除或状态已变化，请刷新后重试");
        }
        Row revised = new Row(row.id(), row.userId(), row.author(), row.orderId(), row.windowId(), input.score(),
                body, imagesJson, tagsJson, row.requestKey(), row.requestHash(), row.createdAt());
        return view(revised);
    }

    /**
     * 删除一条已发布的店铺评价（软删除）。
     *
     * <p>记录保留，所有读取按 {@code status = 1} 过滤，因此删除后不可见。
     * 唯一键 {@code uk_window_review_order} 仍占用原订单，同一订单不能再次评价，
     * 这是"一次消费只允许一次评价"的既有约束，删除不会放开它。</p>
     *
     * @param operatorId 当前登录用户编号
     * @param roles      当前登录用户的角色编码集合
     * @param reviewId   公开评价标识
     */
    @Override
    public void remove(Long operatorId, List<String> roles, String reviewId) {
        CommentServiceImpl.requireUuid(reviewId);
        Row row = reviews.byId(reviewId);
        if (row == null) throw new BusinessException(CommonErrorCode.NOT_FOUND, "评价不存在");
        authority.requireManageable(operatorId, roles, row.userId(), row.windowId());
        if (reviews.softDelete(reviewId) != 1) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "评价不存在或已删除");
        }
    }

    /**
     * 从订单服务读取订单快照并确认可评价。
     *
     * @param id     订单编号
     * @param bearer 原始 Authorization 头
     * @return 已完成订单快照
     */
    private com.hbue.ordering.order.api.vo.OrderReviewSnapshotVO fetchOrder(Long id, String bearer) {
        try {
            var response = orders.getOrder(id, bearer);
            if (response == null || !CommonErrorCode.SUCCESS.code().equals(response.getCode()) || response.getData() == null)
                throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "无法确认订单，请稍后重试");
            var order = response.getData();
            if (!id.equals(order.orderId()) || order.windowId() == null || order.windowId() <= 0)
                throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "订单信息不完整");
            if (!"已完成".equals(order.status()))
                throw new BusinessException(CommonErrorCode.CONFLICT, "订单完成后才能评价");
            return order;
        } catch (FeignException e) {
            var code = e.status() == 401 ? CommonErrorCode.UNAUTHORIZED : (e.status() == 403 || e.status() == 404 ? CommonErrorCode.NOT_FOUND : CommonErrorCode.SERVICE_UNAVAILABLE);
            throw new BusinessException(code, "无法确认本人订单");
        }
    }

    /**
     * 查询某订单对当前店铺的已有评价。
     *
     * @param userId  当前登录用户编号
     * @param orderId 订单主键
     * @return 评价视图；未评价或已删除时返回 null
     */
    @Override
    public WindowReviewVO forOrder(Long userId, Long orderId) {
        var row = reviews.byOrder(userId, orderId);
        return row == null ? null : view(row);
    }

    /**
     * 分页查询店铺评价。
     *
     * @param userId    非空时按本人查询
     * @param windowId  非空时按档口查询
     * @param cursor    游标
     * @param limit     每页条数
     * @param score     评分筛选
     * @param hasImages 是否只看带图
     * @return 评价分页结果
     */
    @Override
    public WindowReviewPageVO page(Long userId, Long windowId, String cursor, int limit, Integer score, Boolean hasImages) {
        // 筛选只接受产品定义的一星/五星；其余评分仍可发布，并在“全部”中查看。
        if (score != null && score != 1 && score != 5) throw bad("筛选分数仅支持1星或5星");
        if (limit < 1 || limit > 50 || (windowId != null && windowId <= 0)) throw bad("分页参数不正确");
        if (cursor != null) CommentServiceImpl.requireUuid(cursor);
        // 本人入口与档口入口分发到各自的固定范围查询，游标和筛选逻辑保持一致。
        var rows = userId != null
                ? reviews.pageByUser(userId, cursor, limit + 1, score, Boolean.TRUE.equals(hasImages) ? true : null)
                : reviews.pageByWindow(windowId, cursor, limit + 1, score, Boolean.TRUE.equals(hasImages) ? true : null);
        boolean more = rows.size() > limit;
        var visible = rows.stream().limit(limit).toList();
        var avatars = authors.resolve(visible.stream().map(Row::userId).toList());
        // 当前页所有评价的评论数与预览各一次批量查询，避免逐条查库。
        Map<String, ReviewDiscussionService.DiscussionSummary> summaries =
                discussions.summarize(ReviewType.WINDOW_REVIEW, reviewIds(visible), null);
        if (summaries == null) summaries = new HashMap<>();
        List<WindowReviewVO> items = new ArrayList<>(visible.size());
        for (Row row : visible) {
            items.add(view(row, avatars.getOrDefault(row.userId(), ""),
                    summaries.getOrDefault(row.id(), EMPTY_DISCUSSION)));
        }
        return new WindowReviewPageVO(items, more ? items.getLast().reviewId() : null, more);
    }

    /**
     * 查询档口评分统计。
     *
     * @param windowId 档口编号
     * @return 评分统计
     */
    @Override
    public com.hbue.ordering.comment.vo.WindowRatingVO rating(Long windowId) {
        var rating = reviews.rating(windowId);
        return new com.hbue.ordering.comment.vo.WindowRatingVO(rating.reviewCount(), rating.averageScore());
    }

    /**
     * 幂等重放：内容摘要一致返回原结果，不一致说明同一请求键被用于不同内容。
     *
     * @param row  已有评价
     * @param hash 本次请求的内容摘要
     * @return 原评价视图
     */
    private WindowReviewVO replay(Row row, String hash) {
        if (!hash.equals(row.requestHash()))
            throw new BusinessException(CommonErrorCode.CONFLICT, "请求标识已用于其他评价内容");
        return view(row);
    }

    /**
     * 单条评价视图，附带作者头像。
     *
     * @param row 评价行
     * @return 评价视图
     */
    private WindowReviewVO view(Row row) {
        return view(row, authors.resolve(List.of(row.userId())).getOrDefault(row.userId(), ""),
                summaryOf(row.id()));
    }

    /**
     * 拼装评价视图。
     *
     * @param row       评价行
     * @param avatarUrl 作者头像地址
     * @return 评价视图
     */
    private WindowReviewVO view(Row row, String avatarUrl) {
        return view(row, avatarUrl, summaryOf(row.id()));
    }

    /**
     * 拼装评价视图，附带评论数与预览。
     *
     * <p>列表与订单详情走批量汇总后调用本方法，单条详情走 {@link #summaryOf(String)}，
     * 两条路径共用同一套字段拼装。</p>
     *
     * @param row       评价行
     * @param avatarUrl 作者头像地址
     * @param summary   评论汇总
     * @return 评价视图
     */
    private WindowReviewVO view(Row row, String avatarUrl, ReviewDiscussionService.DiscussionSummary summary) {
        List<String> keys = readList(row.images());
        List<String> tags = readList(row.tags());
        return new WindowReviewVO(row.id(), row.windowId(), row.author(), avatarUrl, row.score(), row.content(),
                keys.stream().map(FileReferences::url).toList(), tags, row.createdAt(),
                summary.count(), summary.previews());
    }

    /**
     * 读取单条评价的评论汇总。
     *
     * @param reviewId 评价公开标识
     * @return 评论汇总；无评论时返回空汇总
     */
    private ReviewDiscussionService.DiscussionSummary summaryOf(String reviewId) {
        Map<String, ReviewDiscussionService.DiscussionSummary> map =
                discussions.summarize(ReviewType.WINDOW_REVIEW, List.of(reviewId), null);
        ReviewDiscussionService.DiscussionSummary summary = map == null ? null : map.get(reviewId);
        return summary == null ? EMPTY_DISCUSSION : summary;
    }

    /**
     * 提取评价公开标识列表。
     *
     * @param rows 评价行列表
     * @return 公开标识列表
     */
    private static List<String> reviewIds(List<Row> rows) {
        List<String> ids = new ArrayList<>(rows.size());
        for (Row row : rows) {
            ids.add(row.id());
        }
        return ids;
    }

    /**
     * 读取数据库中的 JSON 数组列。
     *
     * <p>历史数据损坏时直接抛出：店铺评价的图片与标签是评价正文的一部分，
     * 静默按空数组展示会让用户以为自己的图片丢了。</p>
     *
     * @param value JSON 字符串
     * @return 字符串列表
     */
    private List<String> readList(String value) {
        try {
            List<String> list = json.readValue(value, new TypeReference<List<String>>() {
            });
            return list == null ? List.of() : list;
        } catch (Exception e) {
            throw new IllegalStateException("评价内容读取失败", e);
        }
    }

    /**
     * 把列表编码为数据库 JSON 数组。
     *
     * @param values 字符串列表
     * @return JSON 字符串
     */
    private String writeList(List<String> values) {
        try {
            return json.writeValueAsString(values);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("评价内容编码失败", e);
        }
    }

    /**
     * 归一化图片文件标识：去空白、统一小写并去重。
     *
     * @param keys 客户端提交的文件标识
     * @return 归一化后的文件标识列表
     */
    private static List<String> normalizeKeys(List<String> keys) {
        return keys.stream().map(key -> key.trim().toLowerCase(Locale.ROOT)).distinct().toList();
    }

    /**
     * 归一化标签：去空白、去重，并拒绝空标签。
     *
     * @param tags 客户端提交的标签
     * @return 归一化后的标签列表
     */
    private static List<String> normalizeTags(List<String> tags) {
        List<String> normalized = tags.stream().map(String::trim).distinct().toList();
        if (normalized.stream().anyMatch(String::isEmpty)) throw bad("评价标签不能为空");
        return normalized;
    }

    /**
     * 构造参数错误异常。
     *
     * @param message 提示信息
     * @return 参数错误异常
     */
    private static BusinessException bad(String message) {
        return new BusinessException(CommonErrorCode.BAD_REQUEST, message);
    }

    /**
     * 对固定菜品/店铺做同一SQL聚合；查询失败向上抛出，不用0条掩盖故障。
     *
     * @param windowId 档口编号
     * @return 评价概览
     */
    @Override
    public com.hbue.ordering.comment.vo.ReviewSummaryVO summary(Long windowId) {
        if (windowId == null || windowId <= 0) throw bad("评价对象编号不正确");
        return reviews.reviewSummary(windowId);
    }
}

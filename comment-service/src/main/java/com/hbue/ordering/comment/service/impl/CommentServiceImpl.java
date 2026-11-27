package com.hbue.ordering.comment.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.comment.dto.CreateCommentRequest;
import com.hbue.ordering.comment.dto.UpdateCommentRequest;
import com.hbue.ordering.comment.mapper.CommentMapper;
import com.hbue.ordering.comment.model.CommentContentDO;
import com.hbue.ordering.comment.model.CommentRowDO;
import com.hbue.ordering.comment.repository.CommentContentRepository;
import com.hbue.ordering.comment.service.AuthorAvatarService;
import com.hbue.ordering.comment.service.CommentMetadataWriter;
import com.hbue.ordering.comment.service.CommentService;
import com.hbue.ordering.comment.service.PurchaseVerifier;
import com.hbue.ordering.comment.service.ReviewAuthorityService;
import com.hbue.ordering.comment.service.ReviewDiscussionService;
import com.hbue.ordering.comment.model.ReviewType;
import com.hbue.ordering.comment.vo.CommentPageVO;
import com.hbue.ordering.comment.vo.CommentVO;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.web.media.FileReferences;
import com.hbue.ordering.common.web.media.ManagedImageClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 菜品评价的创建与分页查询实现。
 *
 * <p>创建流程：校验幂等键与内容 → 归一化后计算请求摘要 → 校验图片归属 → 向订单服务确认
 * "已完成且确实购买该菜品" → 落库。任一步失败都不写入任何数据，避免出现无消费依据的评价。</p>
 *
 * <p>幂等由请求键 + 内容摘要共同保证：同键同内容返回原记录，同键不同内容返回冲突，
 * 不会被后一次提交静默覆盖。并发首提由数据库唯一键兜底，捕获冲突后回读赢家。</p>
 *
 * <p>列表查询只做只读拼装：正文从内容库取、头像批量向用户服务解析，任何附属数据缺失都不影响
 * 评价主体展示。</p>
 *
 * @author order-system
 * @date 2026-10-28
 */
@Service
public class CommentServiceImpl implements CommentService {

    /**
     * 当前类日志对象。
     */
    private static final Logger log = LoggerFactory.getLogger(CommentServiceImpl.class);

    /**
     * 评价主表读写。
     */
    private final CommentMapper rows;

    /**
     * 评价元数据（评分汇总等）写入器。
     */
    private final CommentMetadataWriter writer;

    /**
     * 消费资格校验。
     */
    private final PurchaseVerifier purchases;

    /**
     * 图片归属校验客户端。
     */
    private final ManagedImageClient images;

    /**
     * 评价正文的内容库访问。
     */
    private final CommentContentRepository contents;

    /**
     * JSON 编解码器。
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
     * 创建评价服务。
     *
     * @param rows        评价主表读写
     * @param writer      评价元数据写入
     * @param purchases   消费资格校验
     * @param images      图片归属校验
     * @param contents    正文内容库访问
     * @param json        JSON 编解码器
     * @param authors     作者头像批量解析
     * @param authority   修改与删除的操作者鉴权
     * @param discussions 自由评论区服务
     */
    public CommentServiceImpl(CommentMapper rows, CommentMetadataWriter writer, PurchaseVerifier purchases,
                              ManagedImageClient images, CommentContentRepository contents, ObjectMapper json,
                              AuthorAvatarService authors, ReviewAuthorityService authority,
                              ReviewDiscussionService discussions) {
        this.rows = rows;
        this.writer = writer;
        this.purchases = purchases;
        this.images = images;
        this.contents = contents;
        this.json = json;
        this.authors = authors;
        this.authority = authority;
        this.discussions = discussions;
    }

    public CommentVO create(Long userId, String authorName, String bearer, String key, CreateCommentRequest input) {
        requireUuid(key);
        // 统一归一化后计算幂等摘要；更改内容不能复用原请求键。
        var body = new CreateCommentRequest(input.orderId(), input.productId(), input.score(), input.content() == null ? "" : input.content().trim(),
                (input.imageFileKeys() == null ? List.<String>of() : input.imageFileKeys()).stream().map(k -> k.toLowerCase(Locale.ROOT)).distinct().toList(),
                (input.tags() == null ? List.<String>of() : input.tags()).stream().map(String::trim).distinct().toList(),
                // 匿名意图属于提交内容：同一幂等键先实名后匿名会被摘要判为冲突，避免静默改写展示方式。
                Boolean.TRUE.equals(input.anonymous()));
        if (body.tags().stream().anyMatch(String::isEmpty)) throw bad("评价标签不能为空");
        String hash = hash(body);
        CommentRowDO existing = rows.byRequest(userId, key);
        if (existing != null) return replay(existing, hash);
        Long window = purchases.requireCompletedPurchase(body.orderId(), body.productId(), bearer);
        if (rows.byPurchase(body.orderId(), body.productId()) != null)
            throw new BusinessException(CommonErrorCode.CONFLICT, "该订单的这道菜已经评价");
        List<String> fileKeys = body.imageFileKeys().stream()
                .map(file -> images.requireImageKey(file, userId, "COMMENT_IMAGE", null)).toList();
        String id = UUID.randomUUID().toString();
        String author = authorName == null || authorName.isBlank() ? "校园用户" : authorName.substring(0, Math.min(64, authorName.length()));
        var row = new CommentRowDO(id, userId, author, body.orderId(), body.productId(), window, body.score(), key, hash, LocalDateTime.now(), !fileKeys.isEmpty(), body.anonymous());
        contents.insert(new CommentContentDO(id, body.content(), fileKeys, body.tags(), Instant.now(), false));
        try {
            writer.commit(row);
        } catch (DuplicateKeyException exception) {
            // 不删除Mongo：提交结果不确定时由延迟对账核实，避免误删已经提交的正文。
            existing = rows.byRequest(userId, key);
            if (existing != null) return replay(existing, hash);
            throw new BusinessException(CommonErrorCode.CONFLICT, "该订单的这道菜已经评价");
        }
        try {
            contents.markCommitted(id);
        } catch (RuntimeException exception) {
            log.warn("评论提交后内容标记待对账，commentId={}", id);
        }
        return view(row);
    }

    public List<CommentVO> forOrder(Long userId, Long orderId) {
        List<CommentRowDO> found = rows.byOrder(userId, orderId);
        if (found.isEmpty()) return List.of();
        List<String> commentIds = commentIds(found);
        Map<String, CommentContentDO> bodies = contents.byIds(commentIds);
        Map<Long, String> avatars = authors.resolve(List.of(userId));
        // 订单详情会一次展示多道菜的评价，评论数与预览必须批量取，不能逐条查询。
        Map<String, ReviewDiscussionService.DiscussionSummary> summaries =
                discussions.summarize(ReviewType.COMMENT, commentIds, null);
        if (summaries == null) summaries = Map.of();
        List<CommentVO> views = new ArrayList<>(found.size());
        for (CommentRowDO row : found) {
            views.add(view(row, bodies.get(row.commentId()), avatars.getOrDefault(userId, ""),
                    summaries.getOrDefault(row.commentId(), EMPTY_DISCUSSION)));
        }
        return views;
    }

    public CommentVO detail(String id) {
        requireUuid(id);
        return view(requireRow(id));
    }

    public CommentPageVO page(Long userId, Long productId, String before, int limit, Integer score, Boolean hasImages) {
        // 筛选只接受产品定义的一星/五星；其余评分仍可发布，并在“全部”中查看。
        if (score != null && score != 1 && score != 5) throw bad("筛选分数仅支持1星或5星");
        if (limit < 1 || limit > 50 || (productId != null && productId <= 0)) throw bad("分页参数不正确");
        if (before != null) requireUuid(before);
        Boolean imagesOnly = Boolean.TRUE.equals(hasImages) ? Boolean.TRUE : null;
        // 游标必须属于当前查询范围与筛选：切换筛选后沿用旧游标会让游标子查询取不到 ID，
        // 分页 SQL 静默返回空页；这里显式提示重新加载，避免用户误以为没有更多评价。
        if (before != null && rows.countScopedCursor(before, userId, productId, score, imagesOnly) == 0)
            throw bad("分页游标与当前筛选不匹配，请重新加载");
        // 未回填记录不能被默认为无图，避免旧代码运行期间漏掉带图历史评价。
        if (Boolean.TRUE.equals(hasImages) && rows.hasUnknownImages(userId, productId))
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "评价图片信息待同步，请稍后重试");
        // 路由已确定查询范围；本人和商品分别执行显式条件的 SQL，避免在一个查询里切换范围。
        var found = userId != null
                ? rows.pageByUser(userId, before, limit + 1, score, imagesOnly)
                : rows.pageByProduct(productId, before, limit + 1, score, imagesOnly);
        boolean more = found.size() > limit;
        var visible = found.stream().limit(limit).toList();
        Map<String, CommentContentDO> contents = new HashMap<>();
        if (!visible.isEmpty()) {
            contents.putAll(this.contents.byIds(visible.stream().map(CommentRowDO::commentId).toList()));
        }
        var avatars = authors.resolve(visible.stream().map(CommentRowDO::userId).toList());
        // 当前页所有评价的评论数与预览各一次批量查询，避免 50 条评价产生 50 次往返。
        Map<String, ReviewDiscussionService.DiscussionSummary> summaries =
                discussions.summarize(ReviewType.COMMENT, commentIds(visible), null);
        if (summaries == null) summaries = Map.of();
        List<CommentVO> items = new ArrayList<>(visible.size());
        for (CommentRowDO row : visible) {
            items.add(view(row, contents.get(row.commentId()), avatars.getOrDefault(row.userId(), ""),
                    summaries.getOrDefault(row.commentId(), EMPTY_DISCUSSION)));
        }
        return new CommentPageVO(items, more ? items.getLast().commentId() : null, more);
    }

    /**
     * 修改一条已发布的菜品评价。
     *
     * <p>顺序刻意安排为"先改正文、再改元数据"：正文里包含评分之外的展示内容，元数据事务会同时
     * 调整评分汇总并写 Outbox，一旦后者失败就回滚正文，保证用户看到的失败提示与实际状态一致。
     * 反过来先写元数据，一旦 Mongo 失败就会出现"评分已变、正文未变"且无法自动回滚的局面。</p>
     *
     * @param operatorId 当前登录用户编号
     * @param roles      当前登录用户的角色编码集合
     * @param id         公开评价标识
     * @param input      修改参数
     * @return 修改后的评价视图
     */
    @Override
    public CommentVO revise(Long operatorId, List<String> roles, String id, UpdateCommentRequest input) {
        requireUuid(id);
        if (input == null) throw bad("修改参数不能为空");
        CommentRowDO row = requireRow(id);
        authority.requireManageable(operatorId, roles, row.userId(), row.windowId());
        CommentContentDO current = contents.byId(id);
        if (current == null) {
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "评价内容暂不可用");
        }

        // 未传的字段表示保持原值；传空数组表示主动清空，二者必须区分。
        String body = input.content() == null ? current.body() : input.content().trim();
        List<String> fileKeys = input.imageFileKeys() == null
                ? current.imageFileKeys() : normalizeImageKeys(input.imageFileKeys());
        List<String> tags = input.tags() == null ? current.tags() : normalizeTags(input.tags());
        boolean hasImages = !fileKeys.isEmpty();

        boolean contentChanged = !Objects.equals(body, current.body())
                || !Objects.equals(fileKeys, current.imageFileKeys())
                || !Objects.equals(tags, current.tags());
        boolean scoreChanged = row.score() != input.score();
        if (!contentChanged && !scoreChanged) {
            // 内容与评分都没有变化：直接返回原视图，不产生无意义的 Mapper 写入与下游评分事件。
            return view(row, current, authors.resolve(List.of(row.userId())).getOrDefault(row.userId(), ""));
        }

        // 只校验本次新增的图片；评价里已有的图片仍归原作者所有，允许原样保留。
        for (String key : fileKeys) {
            if (!current.imageFileKeys().contains(key)) {
                images.requireImageKey(key, operatorId, "COMMENT_IMAGE", null);
            }
        }
        if (contentChanged) {
            if (contents.updateContent(id, body, fileKeys, tags) != 1) {
                // 文档不存在或仍在首次提交流程中，不能继续改元数据，否则会出现"有评分没正文"。
                throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "评价内容暂不可用");
            }
        }
        try {
            writer.revise(id, row.productId(), row.score(), input.score(), hasImages);
        } catch (RuntimeException exception) {
            // 元数据事务已回滚，这里把正文补偿回修改前的内容。
            if (contentChanged) {
                restoreContent(id, current);
            }
            throw exception;
        }

        CommentRowDO updated = new CommentRowDO(row.commentId(), row.userId(), row.authorName(), row.orderId(),
                row.productId(), row.windowId(), input.score(), row.requestKey(), row.requestHash(),
                row.createdAt(), hasImages, row.anonymous());
        CommentContentDO content = new CommentContentDO(id, body, fileKeys, tags, current.createdAt(), true);
        return view(updated, content, authors.resolve(List.of(updated.userId())).getOrDefault(updated.userId(), ""));
    }

    /**
     * 删除一条已发布的菜品评价（软删除）。
     *
     * <p>正文留在内容库不删：删除后所有查询都按状态过滤，已经不可见；保留原文才能在申诉、
     * 举报复核或误删恢复时还原事实。评分汇总与下游快照由元数据事务负责。</p>
     *
     * @param operatorId 当前登录用户编号
     * @param roles      当前登录用户的角色编码集合
     * @param id         公开评价标识
     */
    @Override
    public void remove(Long operatorId, List<String> roles, String id) {
        requireUuid(id);
        CommentRowDO row = requireRow(id);
        authority.requireManageable(operatorId, roles, row.userId(), row.windowId());
        writer.remove(row);
    }

    /**
     * 把正文补偿回修改前的内容。
     *
     * <p>补偿本身失败时只记录错误日志并带上评价标识：此时正文与元数据可能不一致，
     * 需要人工按标识核对，不能吞掉原始异常。</p>
     *
     * @param id      评价标识
     * @param current 修改前的正文快照
     */
    private void restoreContent(String id, CommentContentDO current) {
        try {
            contents.updateContent(id, current.body(), current.imageFileKeys(), current.tags());
        } catch (RuntimeException restoreFailure) {
            log.error("评价正文回滚失败，需人工核对，commentId={}", id, restoreFailure);
        }
    }

    /**
     * 归一化图片文件标识：去空白、统一小写并去重，与创建时的口径保持一致。
     *
     * @param keys 客户端提交的文件标识
     * @return 归一化后的文件标识列表
     */
    private static List<String> normalizeImageKeys(List<String> keys) {
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

    private CommentRowDO requireRow(String id) {
        CommentRowDO row = rows.byId(id);
        if (row == null) throw new BusinessException(CommonErrorCode.NOT_FOUND, "评价不存在");
        return row;
    }

    private CommentVO replay(CommentRowDO row, String hash) {
        if (!hash.equals(row.requestHash()))
            throw new BusinessException(CommonErrorCode.CONFLICT, "请求标识已用于其他评价内容");
        return view(row);
    }

    private CommentVO view(CommentRowDO row) {
        CommentContentDO content = contents.byId(row.commentId());
        String avatar = authors.resolve(List.of(row.userId())).getOrDefault(row.userId(), "");
        return view(row, content, avatar, summaryOf(row.commentId()));
    }

    private CommentVO view(CommentRowDO row, CommentContentDO content, String avatarUrl) {
        return view(row, content, avatarUrl, summaryOf(row.commentId()));
    }

    /**
     * 拼装评价视图，附带评论数与预览。
     *
     * <p>列表与订单详情走批量汇总后调用本方法，单条详情走 {@link #summaryOf(String)}，
     * 两条路径共用同一套字段拼装，避免字段遗漏或口径不一致。</p>
     *
     * <p>匿名脱敏只在这里执行：昵称保留首字、其余用星号替换，头像返回空串由客户端回落到默认头像。
     * 真实昵称与用户编号仍保存在服务端，供作者回溯、申诉和管理端核查使用，不进入公开视图。</p>
     *
     * @param row       评价元数据
     * @param content   评价正文；缺失视为服务暂不可用
     * @param avatarUrl 作者头像
     * @param summary   评论汇总
     * @return 评价视图
     */
    private CommentVO view(CommentRowDO row, CommentContentDO content, String avatarUrl,
                           ReviewDiscussionService.DiscussionSummary summary) {
        if (content == null) throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "评价内容暂不可用");
        String authorName = row.anonymous() ? maskAuthorName(row.authorName()) : row.authorName();
        // 匿名评价不返回真实头像地址，客户端拿到空串后统一回落到默认头像。
        String avatar = row.anonymous() ? "" : avatarUrl;
        return new CommentVO(row.commentId(), row.productId(), row.windowId(), authorName, avatar, row.score(),
                content.body(), content.imageFileKeys().stream().map(FileReferences::url).toList(), content.tags(),
                row.createdAt(), summary.count(), summary.previews());
    }

    /**
     * 匿名称谓脱敏：保留首字，其余字符逐个替换为星号。
     *
     * <p>按 Unicode 码点处理，避免昵称以 emoji 等代理对开头时被截断成半个字符；
     * 空昵称统一回落为“匿名用户”，单字昵称在首字后补一个星号，确保展示上始终能看出已匿名。</p>
     *
     * @param name 发布时的真实昵称快照
     * @return 可直接对外展示的匿名称谓
     */
    static String maskAuthorName(String name) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.isEmpty()) return "匿名用户";
        int codePoints = normalized.codePointCount(0, normalized.length());
        int headEnd = normalized.offsetByCodePoints(0, 1);
        return normalized.substring(0, headEnd) + "*".repeat(Math.max(1, codePoints - 1));
    }

    /**
     * 读取单条评价的评论汇总。
     *
     * @param commentId 评价公开标识
     * @return 评论汇总；无评论时返回空汇总
     */
    private ReviewDiscussionService.DiscussionSummary summaryOf(String commentId) {
        Map<String, ReviewDiscussionService.DiscussionSummary> map =
                discussions.summarize(ReviewType.COMMENT, List.of(commentId), null);
        ReviewDiscussionService.DiscussionSummary summary = map == null ? null : map.get(commentId);
        return summary == null ? EMPTY_DISCUSSION : summary;
    }

    /**
     * 提取评价公开标识列表。
     *
     * @param rows 评价元数据列表
     * @return 公开标识列表
     */
    private static List<String> commentIds(List<CommentRowDO> rows) {
        List<String> ids = new ArrayList<>(rows.size());
        for (CommentRowDO row : rows) {
            ids.add(row.commentId());
        }
        return ids;
    }

    private String hash(CreateCommentRequest body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(json.writeValueAsBytes(body)));
        } catch (java.security.NoSuchAlgorithmException | JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static void requireUuid(String id) {
        if (id == null || !id.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"))
            throw bad("请求标识格式不正确");
    }

    private static BusinessException bad(String message) {
        return new BusinessException(CommonErrorCode.BAD_REQUEST, message);
    }

    /**
     * 对固定菜品/店铺做同一SQL聚合；查询失败向上抛出，不用0条掩盖故障。
     */
    public com.hbue.ordering.comment.vo.ReviewSummaryVO summary(Long productId) {
        if (productId == null || productId <= 0) throw bad("评价对象编号不正确");
        return rows.reviewSummary(productId);
    }
}

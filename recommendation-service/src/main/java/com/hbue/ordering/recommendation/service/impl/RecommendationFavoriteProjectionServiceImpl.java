package com.hbue.ordering.recommendation.service.impl;

import com.hbue.ordering.recommendation.mapper.RecommendationFavoriteStateMapper;
import com.hbue.ordering.recommendation.model.RecommendationFavoriteStateDO;
import com.hbue.ordering.recommendation.messaging.FavoriteChangedMessage;
import com.hbue.ordering.recommendation.service.RecommendationFavoriteProjectionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.time.LocalDateTime;

/** 应用收藏当前态及取消墓碑；乱序时按 Outbox 自增编号比较而不是按时间猜测。 */
@Service
public class RecommendationFavoriteProjectionServiceImpl implements RecommendationFavoriteProjectionService {
    private static final Pattern EVENT_ID = Pattern.compile("favorite:([1-9][0-9]{0,18})");
    private final RecommendationFavoriteStateMapper states;

    /** 注入收藏当前态表；数据库唯一键保证同一用户目标只有一行。 */
    public RecommendationFavoriteProjectionServiceImpl(RecommendationFavoriteStateMapper states) {
        this.states = states;
    }

    /**
     * 收藏源表采用物理删除，因此 active=false 必须写入当前态投影作为墓碑。
     * MySQL 按 BIGINT 数值取较大版本，保证取消消息先到时迟到旧收藏也不能复活。
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void apply(FavoriteChangedMessage event) {
        if (event == null || event.userId() == null || event.userId() <= 0 || event.targetId() == null
                || event.targetId() <= 0 || event.active() == null || event.changedAt() == null
                || !("PRODUCT".equals(event.targetType()) || "WINDOW".equals(event.targetType()))) {
            throw new IllegalArgumentException("收藏状态事件格式无效");
        }
        Matcher matcher = EVENT_ID.matcher(event.eventId() == null ? "" : event.eventId());
        if (!matcher.matches()) throw new IllegalArgumentException("收藏事件编号无效");
        long sourceVersion;
        try {
            sourceVersion = Long.parseLong(matcher.group(1));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("收藏事件版本超出范围", exception);
        }
        persistState(event.userId(), event.targetType(), event.targetId(), sourceVersion,
                event.active(), event.changedAt());
    }

    /**
     * 投影历史当前收藏；旧关系可能还没有 Outbox 事件，因此允许 sourceVersion=0。
     * 该方法只接收用户服务内部受保护快照，实时事件仍必须通过带发件箱编号的 apply。
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void applySnapshot(Long userId, String targetType, Long targetId, Long sourceVersion,
            Boolean active, LocalDateTime changedAt) {
        if (userId == null || userId <= 0 || targetId == null || targetId <= 0
                || sourceVersion == null || sourceVersion < 0 || !Boolean.TRUE.equals(active)
                || changedAt == null || !("PRODUCT".equals(targetType) || "WINDOW".equals(targetType))) {
            throw new IllegalArgumentException("收藏历史快照格式无效");
        }
        persistState(userId, targetType, targetId, sourceVersion, true, changedAt);
    }

    /** 两种可信来源共用数值版本 upsert，确保历史快照不能覆盖已应用的更新事件。 */
    private void persistState(Long userId, String targetType, Long targetId, long sourceVersion,
            boolean active, LocalDateTime changedAt) {
        RecommendationFavoriteStateDO row = new RecommendationFavoriteStateDO();
        row.setUserId(userId);
        row.setTargetType(targetType);
        row.setTargetId(targetId);
        row.setSourceVersion(sourceVersion);
        row.setActive(active);
        row.setChangedAt(changedAt);
        // 幂等重放及旧版本可能使 MySQL 返回0行变化；只有 SQL 异常才算投影写入失败。
        states.upsertIfNewer(row);
    }
}

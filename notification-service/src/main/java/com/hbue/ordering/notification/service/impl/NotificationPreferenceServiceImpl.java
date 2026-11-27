package com.hbue.ordering.notification.service.impl;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.notification.dto.NotificationPreferenceUpdateRequest;
import com.hbue.ordering.notification.mapper.NotificationPreferenceMapper;
import com.hbue.ordering.notification.model.NotificationCategory;
import com.hbue.ordering.notification.model.NotificationPreferenceDO;
import com.hbue.ordering.notification.service.NotificationPreferenceService;
import com.hbue.ordering.notification.vo.NotificationPreferenceVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户通知偏好实现。
 *
 * <p>数据库采用「缺省即开启」的稀疏存储：用户从未修改的分类没有记录，
 * 读取时补默认值，写入时只落显式选择。这样可以避免为每个新注册用户批量插入默认行，
 * 也不会因为后续新增分类而需要回填历史用户。</p>
 *
 * <p>关闭开关不会删除或改写已产生的通知记录，只影响此后的实时提示与未读角标；
 * 重新开启后，历史未读会重新计入角标，因为阅读状态本身没有被修改。</p>
 *
 * @author order-system
 * @date 2026-11-24
 */
@Service
public class NotificationPreferenceServiceImpl implements NotificationPreferenceService {

    /**
     * 通知偏好数据访问。
     */
    private final NotificationPreferenceMapper repository;

    /**
     * 创建通知偏好服务。
     *
     * @param repository 通知偏好数据访问
     */
    public NotificationPreferenceServiceImpl(NotificationPreferenceMapper repository) {
        this.repository = repository;
    }

    /**
     * 查询当前用户的全部通知分类开关。
     *
     * <p>只查一次数据库，再按枚举顺序合并默认值，避免逐分类查询；
     * 数据库中未知分类编码不会展示，也不会影响已知分类的读取。</p>
     */
    @Override
    public List<NotificationPreferenceVO> list(long userId) {
        Map<String, Boolean> stored = new HashMap<>();
        for (NotificationPreferenceDO row : repository.selectByUser(userId)) {
            // 只保存显式值；空值按默认开启处理，避免坏数据把提醒意外关掉。
            stored.put(row.getCategory(), Boolean.TRUE.equals(row.getEnabled()));
        }
        List<NotificationPreferenceVO> items = new ArrayList<>(NotificationCategory.values().length);
        for (NotificationCategory category : NotificationCategory.values()) {
            items.add(new NotificationPreferenceVO(
                    category.code(),
                    category.label(),
                    category.description(),
                    stored.getOrDefault(category.code(), Boolean.TRUE)
            ));
        }
        return items;
    }

    /**
     * 修改通知分类开关并返回保存后的完整列表。
     *
     * <p>先完成全部校验再写库，避免写了前几条才发现后面的分类非法。
     * 同一分类重复出现时以最后一次为准；逐条 upsert 在同一事务内提交，
     * 任一条失败整体回滚，防止出现只保存了一半的开关组合。</p>
     */
    @Override
    @Transactional
    public List<NotificationPreferenceVO> update(long userId, NotificationPreferenceUpdateRequest request) {
        if (request == null || request.preferences() == null || request.preferences().isEmpty()) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "请选择要修改的通知类型");
        }
        Map<NotificationCategory, Boolean> changes = new LinkedHashMap<>();
        for (NotificationPreferenceUpdateRequest.Item item : request.preferences()) {
            if (item == null || item.category() == null || item.category().isBlank()) {
                throw new BusinessException(CommonErrorCode.BAD_REQUEST, "通知类型不能为空");
            }
            NotificationCategory category = NotificationCategory.fromCode(item.category());
            if (category == null) {
                throw new BusinessException(CommonErrorCode.BAD_REQUEST, "通知类型不正确");
            }
            if (item.enabled() == null) {
                // 客户端漏传开关状态时拒绝，不能默认成关闭或开启，否则会静默改变用户选择。
                throw new BusinessException(CommonErrorCode.BAD_REQUEST, "通知开关状态不能为空");
            }
            changes.put(category, item.enabled());
        }
        for (Map.Entry<NotificationCategory, Boolean> entry : changes.entrySet()) {
            repository.upsert(userId, entry.getKey().code(), entry.getValue());
        }
        return list(userId);
    }

    /**
     * 判断指定分类是否开启。
     *
     * <p>用「是否存在关闭记录」判断：没有记录返回 0，即默认开启。
     * 依赖异常向上抛出，调用方不能把无法确认的偏好当成任意一种状态。</p>
     */
    @Override
    public boolean isEnabled(long userId, NotificationCategory category) {
        return repository.countDisabled(userId, category.code()) == 0;
    }
}

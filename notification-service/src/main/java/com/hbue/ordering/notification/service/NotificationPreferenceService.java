package com.hbue.ordering.notification.service;

import com.hbue.ordering.notification.dto.NotificationPreferenceUpdateRequest;
import com.hbue.ordering.notification.model.NotificationCategory;
import com.hbue.ordering.notification.vo.NotificationPreferenceVO;

import java.util.List;

/**
 * 用户通知偏好契约。
 *
 * <p>偏好只控制「是否提醒」，不控制「是否记录」：关闭某分类后通知仍会写入消息中心，
 * 聊天消息仍可正常收发，只是不再实时提示、不参与未读角标。账号安全类邮件不经过本服务，
 * 因此不受这里的开关影响。</p>
 *
 * @author order-system
 * @date 2026-11-24
 */
public interface NotificationPreferenceService {

    /**
     * 查询当前用户的全部通知分类开关。
     *
     * <p>未保存过的分类按默认开启返回，保证设置页展示与运行时行为一致。</p>
     *
     * @param userId 用户内部编号
     * @return 按固定顺序排列的分类开关列表
     */
    List<NotificationPreferenceVO> list(long userId);

    /**
     * 修改通知分类开关并返回保存后的完整列表。
     *
     * <p>参数为空、分类未知或开关状态缺失时抛出业务异常，不写入任何部分结果；
     * 多分类更新在同一事务内完成。</p>
     *
     * @param userId  用户内部编号
     * @param request 修改请求
     * @return 保存后的完整分类开关列表
     */
    List<NotificationPreferenceVO> update(long userId, NotificationPreferenceUpdateRequest request);

    /**
     * 判断指定用户是否开启某分类的提醒。
     *
     * <p>供通知落库后的实时提示、未读统计和聊天角标复用。没有记录视为开启；
     * 查询失败时向上抛出，由调用方按依赖故障处理，不静默假定开启或关闭。</p>
     *
     * @param userId   用户内部编号
     * @param category 通知分类
     * @return true 表示开启提醒
     */
    boolean isEnabled(long userId, NotificationCategory category);
}

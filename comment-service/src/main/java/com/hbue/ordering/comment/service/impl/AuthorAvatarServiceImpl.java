package com.hbue.ordering.comment.service.impl;

import com.hbue.ordering.comment.client.CommentUserClient;
import com.hbue.ordering.comment.service.AuthorAvatarService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * 每页一次批量回源，头像失败不使评价正文不可用。
 */
@Service
public class AuthorAvatarServiceImpl implements AuthorAvatarService {
    private static final Logger log = LoggerFactory.getLogger(AuthorAvatarServiceImpl.class);

    /**
     * 单次批量回源的最大用户数，与用户服务批量头像接口的上限保持一致；
     * 超过时按此大小分片，避免评价页因作者过多直接抛错。
     */
    private static final int BATCH_SIZE = 50;

    private final CommentUserClient users;
    private final String token;

    public AuthorAvatarServiceImpl(CommentUserClient users, @Value("${internal.scope.token:}") String token) {
        this.users = users;
        this.token = token;
    }

    @Override
    public Map<Long, String> resolve(Collection<Long> userIds) {
        var ids = userIds.stream().filter(id -> id != null && id > 0).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        Map<Long, String> result = new HashMap<>();
        // 评价页、楼层与预览回复可能合并出超过批量上限的作者，必须分片回源，不能整页失败。
        for (int from = 0; from < ids.size(); from += BATCH_SIZE) {
            var batch = ids.subList(from, Math.min(from + BATCH_SIZE, ids.size()));
            try {
                var response = users.avatars(batch, token);
                if (response == null || !"COMMON_000".equals(response.getCode()) || response.getData() == null)
                    continue;
                for (Long id : batch) {
                    String url = response.getData().get(id);
                    if (url != null && !url.isBlank()) result.put(id, url);
                }
            } catch (RuntimeException error) {
                // 单个分片失败只丢这一批头像，正文与其他分片仍可用。
                log.warn("评论作者头像暂不可用，使用默认头像");
            }
        }
        return result;
    }
}

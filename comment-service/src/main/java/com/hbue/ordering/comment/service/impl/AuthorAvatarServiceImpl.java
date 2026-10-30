package com.hbue.ordering.comment.service.impl;

import com.hbue.ordering.comment.client.CommentUserClient;
import com.hbue.ordering.comment.service.AuthorAvatarService;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 每页一次批量回源，头像失败不使评价正文不可用。 */
@Service
public class AuthorAvatarServiceImpl implements AuthorAvatarService {
    private static final Logger log = LoggerFactory.getLogger(AuthorAvatarServiceImpl.class);
    private final CommentUserClient users;
    private final String token;
    public AuthorAvatarServiceImpl(CommentUserClient users, @Value("${internal.scope.token:}") String token) {
        this.users = users; this.token = token;
    }
    @Override
    public Map<Long, String> resolve(Collection<Long> userIds) {
        var ids = userIds.stream().filter(id -> id != null && id > 0).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        if (ids.size() > 50) throw new IllegalArgumentException("头像查询最多50个用户");
        try {
            var response = users.avatars(ids, token);
            if (response == null || !"COMMON_000".equals(response.getCode()) || response.getData() == null)
                return Map.of();
            Map<Long, String> result = new HashMap<>();
            for (Long id : ids) {
                String url = response.getData().get(id);
                if (url != null && !url.isBlank()) result.put(id, url);
            }
            return result;
        } catch (RuntimeException error) {
            log.warn("评论作者头像暂不可用，使用默认头像");
            return Map.of();
        }
    }
}

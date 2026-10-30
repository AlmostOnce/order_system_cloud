package com.hbue.ordering.comment.service;

import java.util.Collection;
import java.util.Map;

public interface AuthorAvatarService {
    Map<Long, String> resolve(Collection<Long> userIds);
}

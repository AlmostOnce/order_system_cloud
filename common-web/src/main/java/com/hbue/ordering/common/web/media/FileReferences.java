package com.hbue.ordering.common.web.media;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import java.util.Locale;

/** 业务仅持久化文件键；展示地址由统一规则派生。 */
public final class FileReferences {
    private FileReferences() {}

    public static String normalize(String key) {
        if (key == null || key.isEmpty()) return null;
        if (!key.matches("[a-fA-F0-9]{32}")) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "图片文件键格式不正确");
        }
        return key.toLowerCase(Locale.ROOT);
    }

    public static String url(String key) {
        String normalized = normalize(key);
        return normalized == null ? null : "/api/files/" + normalized + "/content";
    }
}

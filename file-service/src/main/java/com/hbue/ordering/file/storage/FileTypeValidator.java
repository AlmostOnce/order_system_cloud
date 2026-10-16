package com.hbue.ordering.file.storage;

import org.springframework.stereotype.Component;

/**
 * 文件类型校验器。
 *
 * <p>先读取文件头的魔数（magic number）判定真实类型，再映射扩展名与 MIME，
 * 不信任客户端提交的扩展名或 Content-Type，避免把可执行内容伪装成图片。
 * 白名单仅包含常见图片格式：jpg/jpeg/png/gif/webp。</p>
 *
 * @author order-system
 * @date 2026-10-16
 */
@Component
public class FileTypeValidator {

    /** JPEG 文件头魔数。 */
    private static final byte[] MAGIC_JPEG = {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF
    };

    /** PNG 文件头魔数。 */
    private static final byte[] MAGIC_PNG = {
            (byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47,
            (byte) 0x0D, (byte) 0x0A, (byte) 0x1A, (byte) 0x0A
    };

    /** GIF 文件头魔数（GIF8）。 */
    private static final byte[] MAGIC_GIF = {
            (byte) 0x47, (byte) 0x49, (byte) 0x46, (byte) 0x38
    };

    /** WEBP 容器 RIFF 头魔数。 */
    private static final byte[] MAGIC_RIFF = {
            (byte) 0x52, (byte) 0x49, (byte) 0x46, (byte) 0x46
    };

    /** WEBP 格式标识。 */
    private static final byte[] MAGIC_WEBP = {
            (byte) 0x57, (byte) 0x45, (byte) 0x42, (byte) 0x50
    };

    /**
     * 根据文件头魔数解析真实扩展名。
     *
     * @param content 文件字节内容
     * @return 白名单内的小写扩展名；无法识别或内容为空时返回 null
     */
    public String resolveExtension(byte[] content) {
        // 内容为空时无法判定类型。
        if (content == null || content.length == 0) {
            return null;
        }

        // 依次匹配各类图片的文件头魔数。
        if (startsWith(content, MAGIC_JPEG, 0)) {
            return "jpg";
        }
        if (startsWith(content, MAGIC_PNG, 0)) {
            return "png";
        }
        if (startsWith(content, MAGIC_GIF, 0)) {
            return "gif";
        }
        // WEBP 需同时满足前 4 字节为 RIFF、第 8-11 字节为 WEBP。
        if (content.length >= 12
                && startsWith(content, MAGIC_RIFF, 0)
                && startsWith(content, MAGIC_WEBP, 8)) {
            return "webp";
        }

        // 不在白名单内的类型一律返回 null。
        return null;
    }

    /**
     * 根据扩展名解析 MIME 类型。
     *
     * @param ext 小写扩展名
     * @return 对应的 MIME 类型；未知扩展名返回 null
     */
    public String resolveContentType(String ext) {
        // 扩展名为空时无法映射。
        if (ext == null || ext.isBlank()) {
            return null;
        }

        // 按扩展名映射常见图片 MIME 类型。
        if ("jpg".equals(ext) || "jpeg".equals(ext)) {
            return "image/jpeg";
        }
        if ("png".equals(ext)) {
            return "image/png";
        }
        if ("gif".equals(ext)) {
            return "image/gif";
        }
        if ("webp".equals(ext)) {
            return "image/webp";
        }

        // 未知扩展名返回 null。
        return null;
    }

    /**
     * 在指定偏移处比较字节前缀是否与魔数一致。
     *
     * @param content 待比较内容
     * @param magic 目标魔数
     * @param offset 比较起始偏移
     * @return 完全一致返回 true，否则返回 false
     */
    private boolean startsWith(byte[] content, byte[] magic, int offset) {
        // 剩余长度不足时直接判定不匹配。
        if (content.length < offset + magic.length) {
            return false;
        }

        // 逐字节比较前缀。
        for (int index = 0; index < magic.length; index++) {
            if (content[offset + index] != magic[index]) {
                return false;
            }
        }
        return true;
    }
}

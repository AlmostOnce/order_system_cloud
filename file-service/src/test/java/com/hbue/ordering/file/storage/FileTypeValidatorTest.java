package com.hbue.ordering.file.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 文件类型校验器单元测试。
 *
 * <p>覆盖白名单内各类图片的魔数识别、非白名单内容拒绝以及扩展名到 MIME 的映射。</p>
 *
 * @author order-system
 * @date 2026-10-16
 */
public class FileTypeValidatorTest {

    /** 被测校验器。 */
    private final FileTypeValidator validator = new FileTypeValidator();

    /**
     * 验证 PNG 文件头能被识别为 png。
     */
    @Test
    public void shouldResolvePngExtension() {
        // PNG 魔数后接任意字节。
        byte[] content = {
                (byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47,
                (byte) 0x0D, (byte) 0x0A, (byte) 0x1A, (byte) 0x0A,
                (byte) 0x00, (byte) 0x00
        };

        // 期望识别为 png。
        assertEquals("png", validator.resolveExtension(content));
    }

    /**
     * 验证 JPEG 文件头能被识别为 jpg。
     */
    @Test
    public void shouldResolveJpegExtension() {
        // JPEG 魔数后接任意字节。
        byte[] content = {
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
                (byte) 0x00, (byte) 0x10
        };

        // 期望识别为 jpg。
        assertEquals("jpg", validator.resolveExtension(content));
    }

    /**
     * 验证 GIF 文件头能被识别为 gif。
     */
    @Test
    public void shouldResolveGifExtension() {
        // GIF 魔数 GIF8 后接版本字符。
        byte[] content = {
                (byte) 0x47, (byte) 0x49, (byte) 0x46, (byte) 0x38,
                (byte) 0x39, (byte) 0x61
        };

        // 期望识别为 gif。
        assertEquals("gif", validator.resolveExtension(content));
    }

    /**
     * 验证 WEBP 文件头能被识别为 webp。
     */
    @Test
    public void shouldResolveWebpExtension() {
        // 前 4 字节 RIFF，第 8-11 字节 WEBP，中间为文件长度。
        byte[] content = {
                (byte) 0x52, (byte) 0x49, (byte) 0x46, (byte) 0x46,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x57, (byte) 0x45, (byte) 0x42, (byte) 0x50
        };

        // 期望识别为 webp。
        assertEquals("webp", validator.resolveExtension(content));
    }

    /**
     * 验证伪造扩展名（内容非白名单）会被拒绝。
     */
    @Test
    public void shouldRejectUnknownContent() {
        // 内容头部不属于任何白名单魔数，即使扩展名写成 png 也应拒绝。
        byte[] content = {(byte) 0x00, (byte) 0x01, (byte) 0x02, (byte) 0x03};

        // 期望返回 null，表示类型不在白名单内。
        assertNull(validator.resolveExtension(content));
    }

    /**
     * 验证空内容会被拒绝。
     */
    @Test
    public void shouldRejectEmptyContent() {
        // 空内容无法判定类型，期望返回 null。
        assertNull(validator.resolveExtension(new byte[0]));
        assertNull(validator.resolveExtension(null));
    }

    /**
     * 验证扩展名到 MIME 类型的映射。
     */
    @Test
    public void shouldResolveContentType() {
        // 逐项校验常见图片扩展名对应的 MIME 类型。
        assertEquals("image/jpeg", validator.resolveContentType("jpg"));
        assertEquals("image/jpeg", validator.resolveContentType("jpeg"));
        assertEquals("image/png", validator.resolveContentType("png"));
        assertEquals("image/gif", validator.resolveContentType("gif"));
        assertEquals("image/webp", validator.resolveContentType("webp"));

        // 未知扩展名与空值均返回 null。
        assertNull(validator.resolveContentType("bmp"));
        assertNull(validator.resolveContentType(null));
    }
}

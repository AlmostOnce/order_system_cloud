package com.hbue.ordering.file.storage;

import com.hbue.ordering.common.core.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 本地文件存储单元测试。
 *
 * <p>使用临时目录作为存储根目录，覆盖写入、存在判断、读取与删除的往返流程，
 * 并验证含路径穿越片段的对象键会被拒绝。</p>
 *
 * @author order-system
 * @date 2026-10-16
 */
public class LocalFileStorageTest {

    /** 临时存储根目录，由 JUnit 注入并在用例结束后清理。 */
    @TempDir
    Path tempDir;

    /**
     * 创建指向临时目录的本地存储实例。
     *
     * @return 本地存储实例
     */
    private LocalFileStorage createStorage() {
        // 以临时目录的 file: URI 作为根目录配置。
        return new LocalFileStorage(
                new DefaultResourceLoader(),
                tempDir.toUri().toString()
        );
    }

    /**
     * 验证写入、存在判断、读取与删除的往返流程。
     */
    @Test
    public void shouldStoreLoadAndDelete() {
        // 创建指向临时目录的存储实例。
        LocalFileStorage storage = createStorage();

        // 准备待写入的内容与对象键。
        String objectKey = "2026/10/16/ab/demo.png";
        byte[] content = {(byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47};

        // 写入前文件尚不存在。
        assertFalse(storage.exists(objectKey));

        // 写入内容。
        storage.store(objectKey, content);

        // 写入后文件存在。
        assertTrue(storage.exists(objectKey));

        // 读取内容与写入内容一致。
        assertArrayEquals(content, storage.load(objectKey));

        // 删除后文件不再存在。
        storage.delete(objectKey);
        assertFalse(storage.exists(objectKey));
    }

    /**
     * 验证重复删除保持幂等。
     */
    @Test
    public void shouldDeleteIdempotently() {
        // 创建指向临时目录的存储实例。
        LocalFileStorage storage = createStorage();

        // 删除不存在的文件不应抛出异常。
        storage.delete("no/such/file.png");
        assertFalse(storage.exists("no/such/file.png"));
    }

    /**
     * 验证含路径穿越片段的对象键会被拒绝。
     */
    @Test
    public void shouldRejectPathTraversal() {
        // 创建指向临时目录的存储实例。
        LocalFileStorage storage = createStorage();

        // 含 ../ 的对象键会跳出根目录，写入与读取均应被拒绝。
        assertThrows(
                BusinessException.class,
                () -> storage.store("../escape.png", new byte[]{0x01})
        );
        assertThrows(
                BusinessException.class,
                () -> storage.load("../escape.png")
        );
    }

    /**
     * 验证空对象键会被拒绝。
     */
    @Test
    public void shouldRejectBlankObjectKey() {
        // 创建指向临时目录的存储实例。
        LocalFileStorage storage = createStorage();

        // 空对象键无法定位文件，应抛出业务异常。
        assertThrows(
                BusinessException.class,
                () -> storage.exists("  ")
        );
    }
}

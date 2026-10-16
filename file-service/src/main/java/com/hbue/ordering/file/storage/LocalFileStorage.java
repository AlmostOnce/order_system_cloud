package com.hbue.ordering.file.storage;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 基于本地文件系统的文件存储实现。
 *
 * <p>根目录来自配置 {@code file.storage.root}（形如 {@code file:/home/user/...}），
 * 通过 {@link ResourceLoader} 解析后转为 {@link Path}。所有落盘路径都由根目录
 * 与对象键拼接并归一化，且必须仍位于根目录之下，防止路径穿越写入越界位置。</p>
 *
 * @author order-system
 * @date 2026-10-16
 */
@Component
public class LocalFileStorage implements FileStorage {

    /** 本地存储根目录。 */
    private final Path root;

    /**
     * 创建本地文件存储。
     *
     * @param resourceLoader Spring 资源加载器
     * @param rootLocation 本地存储根目录资源位置
     */
    public LocalFileStorage(
            ResourceLoader resourceLoader,
            @Value("${file.storage.root}") String rootLocation
    ) {
        // 通过资源加载器解析配置的根目录位置。
        Resource rootResource = resourceLoader.getResource(rootLocation);
        try {
            // 将资源 URI 转为绝对路径并归一化，作为后续路径拼接的可信基准。
            this.root = Paths.get(rootResource.getURI()).toAbsolutePath().normalize();
        } catch (IOException exception) {
            throw new IllegalStateException("文件存储根目录配置无效", exception);
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean exists(String objectKey) {
        // 归一化并校验对象键后再判断文件是否存在。
        Path target = resolveSafely(objectKey);
        return Files.exists(target);
    }

    /** {@inheritDoc} */
    @Override
    public void store(String objectKey, byte[] content) {
        // 归一化并校验对象键，避免路径穿越。
        Path target = resolveSafely(objectKey);
        try {
            // 按需创建父目录，目录已存在时不报错。
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            // 将字节内容写入目标文件，覆盖式写入保证幂等。
            Files.write(target, content);
        } catch (IOException exception) {
            throw new BusinessException(
                    CommonErrorCode.INTERNAL_ERROR,
                    "文件写入失败"
            );
        }
    }

    /** {@inheritDoc} */
    @Override
    public byte[] load(String objectKey) {
        // 归一化并校验对象键，避免越界读取。
        Path target = resolveSafely(objectKey);
        try {
            // 读取目标文件全部字节。
            return Files.readAllBytes(target);
        } catch (IOException exception) {
            throw new BusinessException(
                    CommonErrorCode.NOT_FOUND,
                    "文件内容不存在"
            );
        }
    }

    /** {@inheritDoc} */
    @Override
    public void delete(String objectKey) {
        // 归一化并校验对象键，避免越界删除。
        Path target = resolveSafely(objectKey);
        try {
            // 文件不存在时静默返回，保证删除幂等。
            Files.deleteIfExists(target);
        } catch (IOException exception) {
            throw new BusinessException(
                    CommonErrorCode.INTERNAL_ERROR,
                    "文件删除失败"
            );
        }
    }

    /**
     * 将对象键解析为根目录下的安全路径。
     *
     * <p>先与根目录拼接再归一化，随后断言结果仍位于根目录之下；
     * 若对象键含 {@code ..} 等穿越片段，归一化后会跳出根目录而被拒绝。</p>
     *
     * @param objectKey 介质内相对路径
     * @return 归一化后的目标路径
     * @throws BusinessException 对象键为空或归一化后越出根目录时抛出
     */
    private Path resolveSafely(String objectKey) {
        // 对象键不能为空。
        if (objectKey == null || objectKey.isBlank()) {
            throw new BusinessException(
                    CommonErrorCode.BAD_REQUEST,
                    "文件路径不能为空"
            );
        }

        // 拼接根目录并归一化，消除 . 与 .. 等相对片段。
        Path target = root.resolve(objectKey).normalize();

        // 归一化后必须仍位于根目录之下，否则视为路径穿越。
        if (!target.startsWith(root)) {
            throw new BusinessException(
                    CommonErrorCode.BAD_REQUEST,
                    "非法的文件路径"
            );
        }
        return target;
    }
}

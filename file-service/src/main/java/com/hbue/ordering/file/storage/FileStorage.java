package com.hbue.ordering.file.storage;

/**
 * 文件存储介质抽象。
 *
 * <p>抽象出与具体介质无关的读写能力，当前由 {@link LocalFileStorage} 基于本地
 * 文件系统实现；后续如需接入 MinIO 或对象存储，只需新增实现而不改动业务层。
 * 所有方法以 {@code objectKey}（介质内相对路径）为唯一定位，调用方不得传入用户输入。</p>
 *
 * @author order-system
 * @date 2026-10-16
 */
public interface FileStorage {

    /**
     * 判断指定对象键对应的文件是否已存在。
     *
     * <p>用于上传时的秒传判断：内容摘要相同时对象键一致，已存在则无需重复写盘。</p>
     *
     * @param objectKey 介质内相对路径
     * @return 文件存在返回 true，否则返回 false
     */
    boolean exists(String objectKey);

    /**
     * 将字节内容写入指定对象键。
     *
     * <p>写入时按需创建父目录；父目录或文件写入失败时抛出运行期异常，
     * 由上层转换为业务异常。</p>
     *
     * @param objectKey 介质内相对路径
     * @param content 文件字节内容
     */
    void store(String objectKey, byte[] content);

    /**
     * 读取指定对象键对应的文件内容。
     *
     * @param objectKey 介质内相对路径
     * @return 文件字节内容
     */
    byte[] load(String objectKey);

    /**
     * 删除指定对象键对应的文件。
     *
     * <p>文件不存在时不做处理，保证删除操作的幂等性。</p>
     *
     * @param objectKey 介质内相对路径
     */
    void delete(String objectKey);
}

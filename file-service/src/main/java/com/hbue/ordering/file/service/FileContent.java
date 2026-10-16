package com.hbue.ordering.file.service;

/**
 * 文件内容读取的返回载体。
 *
 * <p>承载文件字节内容及其类型、原始文件名，供内容接口设置响应头并写出字节流；
 * 只在服务层内部传递，不作为持久化映射对象或对外响应体。</p>
 *
 * @author order-system
 * @date 2026-10-16
 */
public class FileContent {

    /** 文件字节内容。 */
    private final byte[] content;

    /** MIME 类型。 */
    private final String contentType;

    /** 原始文件名。 */
    private final String originalName;

    /**
     * 创建文件内容载体。
     *
     * @param content 文件字节内容
     * @param contentType MIME 类型
     * @param originalName 原始文件名
     */
    public FileContent(byte[] content, String contentType, String originalName) {
        this.content = content;
        this.contentType = contentType;
        this.originalName = originalName;
    }

    /**
     * 获取文件字节内容。
     *
     * @return 文件字节内容
     */
    public byte[] getContent() {
        return content;
    }

    /**
     * 获取 MIME 类型。
     *
     * @return MIME 类型
     */
    public String getContentType() {
        return contentType;
    }

    /**
     * 获取原始文件名。
     *
     * @return 原始文件名
     */
    public String getOriginalName() {
        return originalName;
    }
}

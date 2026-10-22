package com.hbue.ordering.file.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文件元数据查询返回的视图对象。
 *
 * <p>展示文件的业务归属、类型与摘要信息，供调用方在业务侧记录或校验；
 * 不包含内部存储路径，也不作为持久化映射对象。</p>
 *
 * @author order-system
 * @date 2026-10-16
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileMetaVO {

    /** 对外文件键。 */
    private String fileKey;

    /** 业务类型。 */
    private String bizType;

    /** 关联业务主键，可为空。 */
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Long bizRef;

    /** 原始文件名，仅作展示。 */
    private String originalName;

    /** 扩展名（小写、白名单内）。 */
    private String fileExt;

    /** MIME 类型。 */
    private String contentType;

    /** 字节数。 */
    private Long fileSize;

    /** 内容摘要，用于秒传去重与完整性校验。 */
    private String sha256;

    /** 上传者 user_id。 */
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Long uploaderId;

    /** 创建时间。 */
    private LocalDateTime createdAt;
}

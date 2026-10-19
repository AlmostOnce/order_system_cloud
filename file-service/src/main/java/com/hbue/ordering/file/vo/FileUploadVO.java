package com.hbue.ordering.file.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件上传成功后返回的视图对象。
 *
 * <p>只暴露对外可用的文件键与访问地址，不包含自增主键、存储路径等内部字段。</p>
 *
 * @author order-system
 * @date 2026-10-16
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadVO {

    /** 对外文件键，后续凭它查询元数据、读取内容或删除。 */
    private String fileKey;

    /** 文件内容访问地址，指向网关的文件内容接口。 */
    private String url;

    /** 原始文件名，仅作展示。 */
    private String originalName;

    /** 扩展名（小写、白名单内）。 */
    private String fileExt;

    /** MIME 类型。 */
    private String contentType;

    /** 字节数。 */
    private Long fileSize;
}

package com.hbue.ordering.file.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件对象持久化对象。
 *
 * <p>映射 file_db 中的 file_objects 表，保存一份上传文件的内容摘要与定位信息。
 * 同一内容可能被多条记录引用，故删除只做逻辑删除，不清理物理文件。
 * 该类只映射数据库字段，不负责存储读写或业务校验，也不直接作为接口响应对象。</p>
 *
 * @author order-system
 * @date 2026-10-16
 */
@Data
@TableName("file_objects")
public class FileObjectDO {

    /**
     * 文件记录主键。
     */
    @TableId(value = "file_id", type = IdType.AUTO)
    private Long fileId;

    /**
     * 对外文件键（UUID 去横线），接口只暴露此值，不暴露自增 ID。
     */
    @TableField("file_key")
    private String fileKey;

    /**
     * 业务类型：AVATAR/WINDOW_IMAGE/PRODUCT_IMAGE/RESTAURANT_IMAGE/COMMENT_IMAGE/OTHER。
     */
    @TableField("biz_type")
    private String bizType;

    /**
     * 关联业务主键，上传时未知可为空。
     */
    @TableField("biz_ref")
    private Long bizRef;

    /**
     * 存储介质：LOCAL/MINIO/S3。
     */
    @TableField("storage_type")
    private String storageType;

    /**
     * 介质内相对路径，由服务端按内容摘要生成，绝不拼接用户输入。
     */
    @TableField("object_key")
    private String objectKey;

    /**
     * 原始文件名，仅作展示，不参与落盘路径。
     */
    @TableField("original_name")
    private String originalName;

    /**
     * 扩展名（小写、白名单内）。
     */
    @TableField("file_ext")
    private String fileExt;

    /**
     * MIME 类型，由魔数校验后确定。
     */
    @TableField("content_type")
    private String contentType;

    /**
     * 字节数。
     */
    @TableField("file_size")
    private Long fileSize;

    /**
     * 内容摘要，用于秒传去重与完整性校验。
     */
    @TableField("sha256")
    private String sha256;

    /**
     * 上传者 user_id（取自 JWT sub）。
     */
    @TableField("uploader_id")
    private Long uploaderId;

    /**
     * 逻辑删除：0 正常 1 已删。
     */
    @TableField("is_deleted")
    private Integer isDeleted;

    /**
     * 创建时间。
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 修改时间。
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}

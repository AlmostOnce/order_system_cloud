package com.hbue.ordering.file.service;

import com.hbue.ordering.file.vo.FileMetaVO;
import com.hbue.ordering.file.vo.FileUploadVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储业务接口。
 *
 * <p>串联类型校验、内容摘要、存储介质读写与元数据落库，对上层暴露上传、查询、
 * 内容读取与逻辑删除四个能力；归属与角色判定也在此层完成。</p>
 *
 * @author order-system
 * @date 2026-10-16
 */
public interface FileStorageService {

    /**
     * 校验并保存上传文件，同时登记文件元数据。
     *
     * @param file 上传的文件
     * @param bizType 业务类型，为空时缺省为 OTHER
     * @param bizRef 关联业务主键，可为空
     * @param uploaderId 上传者用户 ID
     * @return 上传结果
     */
    FileUploadVO upload(MultipartFile file, String bizType, Long bizRef, Long uploaderId);

    /**
     * 按文件键查询文件元数据。
     *
     * @param fileKey 对外文件键
     * @return 文件元数据
     */
    FileMetaVO getMeta(String fileKey);

    /**
     * 按文件键读取文件内容。
     *
     * @param fileKey 对外文件键
     * @return 文件内容载体
     */
    FileContent loadContent(String fileKey);

    /**
     * 逻辑删除文件记录，不清理物理文件。
     *
     * @param fileKey 对外文件键
     * @param operatorId 操作者用户 ID
     * @param systemAdmin 操作者是否为系统管理员
     */
    void delete(String fileKey, Long operatorId, boolean systemAdmin);
}

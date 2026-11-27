package com.hbue.ordering.file.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.file.mapper.FileObjectMapper;
import com.hbue.ordering.file.model.FileObjectDO;
import com.hbue.ordering.file.model.FileContent;
import com.hbue.ordering.file.service.FileStorageService;
import com.hbue.ordering.file.storage.FileStorage;
import com.hbue.ordering.file.storage.FileTypeValidator;
import com.hbue.ordering.file.vo.FileMetaVO;
import com.hbue.ordering.file.vo.FileUploadVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * 文件存储业务实现。
 *
 * <p>上传流程为：校验大小与真实类型 → 计算内容摘要 → 按摘要生成存储路径并秒传判断
 * → 生成对外文件键 → 落库元数据。存储路径只由内容摘要决定（不含上传日期），
 * 因此同一内容无论何时上传都落到同一对象键，跨天也不会重复存盘。</p>
 *
 * <p>删除先做逻辑删除；仅当同一对象键已没有任何未删除记录引用时，才回收物理文件，
 * 避免误删仍被其它元数据引用的内容。物理回收失败不会让逻辑删除表现为失败，
 * 残留文件由运维按日志清理。</p>
 *
 * @author order-system
 * @date 2026-10-16
 */
@Service
public class FileStorageServiceImpl implements FileStorageService {

    /**
     * 单文件大小上限，5MB。
     */
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;

    /**
     * 业务类型缺省值。
     */
    private static final String DEFAULT_BIZ_TYPE = "OTHER";

    /**
     * 本地存储介质标识。
     */
    private static final String STORAGE_TYPE_LOCAL = "LOCAL";

    /**
     * 逻辑删除：正常。
     */
    private static final int NOT_DELETED = 0;

    /**
     * 逻辑删除：已删除。
     */
    private static final int DELETED = 1;

    /**
     * 当前类日志对象。
     */
    private static final Logger log = LoggerFactory.getLogger(FileStorageServiceImpl.class);

    /**
     * 内容摘要算法名。
     */
    private static final String DIGEST_ALGORITHM = "SHA-256";

    /**
     * 十六进制字符表，用于普通循环拼接摘要。
     */
    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    /**
     * 文件元数据 Mapper。
     */
    private final FileObjectMapper fileObjectMapper;

    /**
     * 文件存储介质。
     */
    private final FileStorage fileStorage;

    /**
     * 文件类型校验器。
     */
    private final FileTypeValidator fileTypeValidator;

    /**
     * 文件访问地址公共前缀。
     */
    private final String publicBaseUrl;

    /**
     * 创建文件存储服务。
     *
     * @param fileObjectMapper  文件元数据 Mapper
     * @param fileStorage       文件存储介质
     * @param fileTypeValidator 文件类型校验器
     * @param publicBaseUrl     文件访问地址公共前缀
     */
    public FileStorageServiceImpl(
            FileObjectMapper fileObjectMapper,
            FileStorage fileStorage,
            FileTypeValidator fileTypeValidator,
            @Value("${file.public-base-url}") String publicBaseUrl
    ) {
        this.fileObjectMapper = fileObjectMapper;
        this.fileStorage = fileStorage;
        this.fileTypeValidator = fileTypeValidator;
        this.publicBaseUrl = publicBaseUrl;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public FileUploadVO upload(
            MultipartFile file,
            String bizType,
            Long bizRef,
            Long uploaderId
    ) {
        // 校验文件非空且大小在上限内。
        requireUploadable(file);

        // 读取文件字节内容，读取失败按系统异常处理。
        byte[] content = readBytes(file);

        // 用魔数判定真实扩展名，不在白名单内直接拒绝。
        String fileExt = fileTypeValidator.resolveExtension(content);
        if (fileExt == null) {
            throw new BusinessException(
                    CommonErrorCode.BAD_REQUEST,
                    "不支持的文件类型，仅支持 jpg/jpeg/png/gif/webp"
            );
        }

        // 由已确认的扩展名映射 MIME 类型。
        String contentType = fileTypeValidator.resolveContentType(fileExt);

        // 计算内容摘要，作为存储路径与去重依据。
        String sha256 = computeSha256(content);

        // 按日期与摘要拼出存储路径，路径只含服务端生成的摘要，不含用户输入。
        String objectKey = buildObjectKey(sha256, fileExt);

        // 同一内容已存在时跳过写盘（秒传），否则写入存储介质。
        if (!fileStorage.exists(objectKey)) {
            fileStorage.store(objectKey, content);
        }

        // 生成对外文件键，去掉 UUID 中的横线。
        String fileKey = generateFileKey();

        // 组装文件元数据对象。
        FileObjectDO fileObject = new FileObjectDO();
        fileObject.setFileKey(fileKey);
        fileObject.setBizType(resolveBizType(bizType));
        fileObject.setBizRef(bizRef);
        fileObject.setStorageType(STORAGE_TYPE_LOCAL);
        fileObject.setObjectKey(objectKey);
        fileObject.setOriginalName(resolveOriginalName(file));
        fileObject.setFileExt(fileExt);
        fileObject.setContentType(contentType);
        fileObject.setFileSize((long) content.length);
        fileObject.setSha256(sha256);
        fileObject.setUploaderId(uploaderId);
        fileObject.setIsDeleted(NOT_DELETED);

        // 落库文件元数据。
        fileObjectMapper.insert(fileObject);

        // 返回包含访问地址的上传结果。
        return toUploadVO(fileObject);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FileMetaVO getMeta(String fileKey) {
        // 查询未删除的文件记录，不存在按 404 语义处理。
        FileObjectDO fileObject = requireActiveFile(fileKey);
        return toMetaVO(fileObject);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FileContent loadContent(String fileKey) {
        // 查询未删除的文件记录，不存在按 404 语义处理。
        FileObjectDO fileObject = requireActiveFile(fileKey);

        // 从存储介质读取文件字节内容。
        byte[] content = fileStorage.load(fileObject.getObjectKey());

        // 组装内容载体，供内容接口设置响应头并写出字节流。
        return new FileContent(
                content,
                fileObject.getContentType(),
                fileObject.getOriginalName()
        );
    }

    /**
     * 删除文件：先逻辑删除，再按引用情况回收物理文件。
     *
     * <p>同一对象键可能被多条元数据记录引用（相同内容重复上传）。因此逻辑删除后必须回查
     * 是否还有未删除记录引用该对象键，只有引用数为零时才删除物理文件；否则保留文件，
     * 让其它记录继续可读。</p>
     *
     * <p>并发边界：上传端先判断文件是否存在再决定写盘，若其判断恰好发生在本次回收之前，
     * 并发的同内容上传可能短暂指向已回收的文件。该窗口极小，且内容寻址保证再次上传同一
     * 内容即可自愈（文件不存在时会重新写盘）；为控制改动范围，这里不引入跨表引用计数。</p>
     */
    @Override
    @Transactional
    public void delete(String fileKey, Long operatorId, boolean systemAdmin) {
        // 查询未删除的文件记录，不存在按 404 语义处理。
        FileObjectDO fileObject = requireActiveFile(fileKey);

        // 非本人且非系统管理员不允许删除，返回 403 语义。
        boolean owner = fileObject.getUploaderId() != null
                && fileObject.getUploaderId().equals(operatorId);
        if (!owner && !systemAdmin) {
            throw new BusinessException(
                    CommonErrorCode.FORBIDDEN,
                    "无权删除该文件"
            );
        }

        // 先做逻辑删除：元数据立即不可见，后续物理回收失败也不会把文件暴露给业务。
        UpdateWrapper<FileObjectDO> update = new UpdateWrapper<>();
        update.eq("file_key", fileKey)
                .eq("is_deleted", NOT_DELETED)
                .set("is_deleted", DELETED);
        fileObjectMapper.update(null, update);

        // 回查同一对象键的未删除引用数，仍有引用时物理文件必须保留。
        Long activeReferences = fileObjectMapper.selectCount(
                new QueryWrapper<FileObjectDO>()
                        .eq("object_key", fileObject.getObjectKey())
                        .eq("is_deleted", NOT_DELETED)
        );
        if (activeReferences == null || activeReferences > 0L) {
            return;
        }

        try {
            // 没有任何未删除记录引用时才回收物理文件；介质实现本身幂等，重复调用安全。
            fileStorage.delete(fileObject.getObjectKey());
        } catch (RuntimeException exception) {
            // 逻辑删除已经生效，物理回收失败只记录日志：不能把一次成功的删除操作报成失败，
            // 残留文件可按日志与对象键人工清理。
            log.warn("孤立文件物理回收失败，objectKey={}", fileObject.getObjectKey(), exception);
        }
    }

    /**
     * 校验上传文件是否可被接受。
     *
     * @param file 上传文件
     * @throws BusinessException 文件为空或超出大小上限时抛出
     */
    private void requireUploadable(MultipartFile file) {
        // 文件对象不能为空，且必须携带内容。
        if (file == null || file.isEmpty()) {
            throw new BusinessException(
                    CommonErrorCode.BAD_REQUEST,
                    "上传文件不能为空"
            );
        }

        // 文件大小不能超过上限。
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(
                    CommonErrorCode.BAD_REQUEST,
                    "文件大小超过 5MB 限制"
            );
        }
    }

    /**
     * 读取上传文件的字节内容。
     *
     * @param file 上传文件
     * @return 文件字节内容
     * @throws BusinessException 读取失败时抛出
     */
    private byte[] readBytes(MultipartFile file) {
        try {
            // 读取全部字节，供类型校验与摘要计算使用。
            return file.getBytes();
        } catch (IOException exception) {
            throw new BusinessException(
                    CommonErrorCode.INTERNAL_ERROR,
                    "读取上传文件失败"
            );
        }
    }

    /**
     * 计算字节内容的 SHA-256 摘要。
     *
     * @param content 文件字节内容
     * @return 64 位小写十六进制摘要
     */
    private String computeSha256(byte[] content) {
        try {
            // 使用 SHA-256 计算内容摘要。
            MessageDigest digest = MessageDigest.getInstance(DIGEST_ALGORITHM);
            byte[] hash = digest.digest(content);

            // 用普通循环逐字节拼接十六进制字符串，避免使用 Stream。
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                int unsigned = value & 0xFF;
                builder.append(HEX_CHARS[unsigned >>> 4]);
                builder.append(HEX_CHARS[unsigned & 0x0F]);
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new BusinessException(
                    CommonErrorCode.INTERNAL_ERROR,
                    "内容摘要计算失败"
            );
        }
    }

    /**
     * 按内容摘要生成存储路径。
     *
     * <p>路径只包含摘要前两位目录与完整摘要，不含上传日期：同一内容在不同日期上传会得到
     * 同一对象键，才能真正命中秒传与元数据复用；日期分层会让跨天重复上传产生多份物理副本。</p>
     *
     * @param sha256  内容摘要
     * @param fileExt 扩展名
     * @return 形如 {@code {摘要前2位}/{摘要}.{扩展名}} 的相对路径
     */
    private String buildObjectKey(String sha256, String fileExt) {
        // 取摘要前两位作为一级目录，避免单目录文件过多。
        String prefix = sha256.substring(0, 2);

        // 拼出最终相对路径，路径片段全部来自服务端生成。
        return prefix + "/" + sha256 + "." + fileExt;
    }

    /**
     * 生成对外文件键。
     *
     * @return 去掉横线的 UUID 字符串
     */
    private String generateFileKey() {
        // 生成 UUID 并去掉横线，保证长度固定且不可枚举。
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 解析业务类型，留空时使用缺省值。
     *
     * @param bizType 请求中的业务类型
     * @return 最终写入的业务类型
     */
    private String resolveBizType(String bizType) {
        // 业务类型为空时缺省为 OTHER。
        if (bizType == null || bizType.isBlank()) {
            return DEFAULT_BIZ_TYPE;
        }
        return bizType;
    }

    /**
     * 解析原始文件名，缺失时使用占位名。
     *
     * <p>原始文件名仅作展示，不参与落盘路径，因而无需清洗路径片段。</p>
     *
     * @param file 上传文件
     * @return 原始文件名或占位名
     */
    private String resolveOriginalName(MultipartFile file) {
        // 取上传时携带的原始文件名。
        String originalName = file.getOriginalFilename();

        // 文件名缺失时用占位名保证非空。
        if (originalName == null || originalName.isBlank()) {
            return "unnamed";
        }
        return originalName;
    }

    /**
     * 查询未删除的文件记录，不存在时返回统一 404 业务异常。
     *
     * @param fileKey 对外文件键
     * @return 文件元数据
     */
    private FileObjectDO requireActiveFile(String fileKey) {
        // 文件键不能为空。
        if (fileKey == null || fileKey.isBlank()) {
            throw new BusinessException(
                    CommonErrorCode.BAD_REQUEST,
                    "文件键不能为空"
            );
        }

        // 按文件键查询未删除的记录。
        FileObjectDO fileObject = fileObjectMapper.selectOne(
                new QueryWrapper<FileObjectDO>()
                        .eq("file_key", fileKey)
                        .eq("is_deleted", NOT_DELETED)
        );

        // 记录不存在或已删除时统一返回 404 语义，不区分存在性。
        if (fileObject == null) {
            throw new BusinessException(
                    CommonErrorCode.NOT_FOUND,
                    "文件不存在"
            );
        }
        return fileObject;
    }

    /**
     * 将文件元数据转换为上传结果视图。
     *
     * @param fileObject 文件元数据
     * @return 上传结果视图
     */
    private FileUploadVO toUploadVO(FileObjectDO fileObject) {
        // 拼接网关下的文件内容访问地址。
        String url = publicBaseUrl + "/files/" + fileObject.getFileKey() + "/content";

        return new FileUploadVO(
                fileObject.getFileKey(),
                url,
                fileObject.getOriginalName(),
                fileObject.getFileExt(),
                fileObject.getContentType(),
                fileObject.getFileSize()
        );
    }

    /**
     * 将文件元数据转换为元数据视图。
     *
     * @param fileObject 文件元数据
     * @return 元数据视图
     */
    private FileMetaVO toMetaVO(FileObjectDO fileObject) {
        return new FileMetaVO(
                fileObject.getFileKey(),
                fileObject.getBizType(),
                fileObject.getBizRef(),
                fileObject.getOriginalName(),
                fileObject.getFileExt(),
                fileObject.getContentType(),
                fileObject.getFileSize(),
                fileObject.getSha256(),
                fileObject.getUploaderId(),
                fileObject.getCreatedAt()
        );
    }
}

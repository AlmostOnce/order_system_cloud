package com.hbue.ordering.file.service.impl;

import com.hbue.ordering.file.mapper.FileObjectMapper;
import com.hbue.ordering.file.model.FileObjectDO;
import com.hbue.ordering.file.storage.FileStorage;
import com.hbue.ordering.file.storage.FileTypeValidator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文件存储服务测试：对象键按内容寻址、跨天不重复写盘，以及删除后的物理文件回收。
 *
 * @author order-system
 * @date 2026-11-27
 */
class FileStorageServiceImplTest {

    /**
     * 测试用未删除标记。
     */
    private static final int NOT_DELETED = 0;

    /**
     * 上传两次相同内容应得到同一对象键，且第二次命中秒传不再写盘。
     */
    @Test
    void uploadUsesContentAddressedKeyWithoutDateLayer() throws Exception {
        FileObjectMapper mapper = mock(FileObjectMapper.class);
        FileStorage storage = mock(FileStorage.class);
        FileTypeValidator validator = validator("png");
        FileStorageServiceImpl service = new FileStorageServiceImpl(
                mapper, storage, validator, "http://localhost:8080");
        byte[] content = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};
        MultipartFile file = file(content, "avatar.png");

        service.upload(file, "avatar", 1L, 5L);

        ArgumentCaptor<FileObjectDO> first = ArgumentCaptor.forClass(FileObjectDO.class);
        verify(mapper).insert(first.capture());
        String objectKey = first.getValue().getObjectKey();
        // 路径只由摘要构成：不再带 yyyy/MM/dd 前缀，跨天上传同一内容会命中同一对象。
        assertTrue(objectKey.matches("[0-9a-f]{2}/[0-9a-f]{64}\\.png"), objectKey);
        verify(storage).store(objectKey, content);

        // 同一内容再次上传：文件已存在，不再写盘，对象键保持一致（真正去重）。
        when(storage.exists(objectKey)).thenReturn(true);
        service.upload(file, "avatar", 1L, 6L);

        ArgumentCaptor<FileObjectDO> second = ArgumentCaptor.forClass(FileObjectDO.class);
        verify(mapper, times(2)).insert(second.capture());
        assertEquals(objectKey, second.getAllValues().get(1).getObjectKey());
        verify(storage, times(1)).store(anyString(), any());
    }

    /**
     * 删除最后一条引用记录时应回收物理文件。
     */
    @Test
    void deleteRemovesPhysicalFileWhenNoActiveReferenceRemains() {
        FileObjectMapper mapper = mock(FileObjectMapper.class);
        FileStorage storage = mock(FileStorage.class);
        FileStorageServiceImpl service = new FileStorageServiceImpl(
                mapper, storage, validator("png"), "http://localhost:8080");
        when(mapper.selectOne(any())).thenReturn(fileObject(5L));
        when(mapper.selectCount(any())).thenReturn(0L);

        service.delete("fk", 5L, false);

        verify(storage).delete("ab/sample.png");
    }

    /**
     * 仍有其它未删除记录引用同一对象键时必须保留物理文件。
     */
    @Test
    void deleteKeepsPhysicalFileWhenOtherRecordStillReferencesIt() {
        FileObjectMapper mapper = mock(FileObjectMapper.class);
        FileStorage storage = mock(FileStorage.class);
        FileStorageServiceImpl service = new FileStorageServiceImpl(
                mapper, storage, validator("png"), "http://localhost:8080");
        when(mapper.selectOne(any())).thenReturn(fileObject(5L));
        when(mapper.selectCount(any())).thenReturn(1L);

        service.delete("fk", 5L, false);

        verify(storage, never()).delete(anyString());
    }

    /**
     * 非本人且非管理员不能删除，也不能触发物理回收。
     */
    @Test
    void deleteRejectsNonOwnerWithoutTouchingStorage() {
        FileObjectMapper mapper = mock(FileObjectMapper.class);
        FileStorage storage = mock(FileStorage.class);
        FileStorageServiceImpl service = new FileStorageServiceImpl(
                mapper, storage, validator("png"), "http://localhost:8080");
        when(mapper.selectOne(any())).thenReturn(fileObject(5L));

        org.junit.jupiter.api.Assertions.assertThrows(
                com.hbue.ordering.common.core.exception.BusinessException.class,
                () -> service.delete("fk", 9L, false)
        );

        verify(storage, never()).delete(anyString());
        verify(mapper, never()).selectCount(any());
    }

    /**
     * 构造文件类型校验器替身。
     *
     * @param ext 允许的扩展名
     * @return 校验器替身
     */
    private FileTypeValidator validator(String ext) {
        FileTypeValidator validator = mock(FileTypeValidator.class);
        when(validator.resolveExtension(any())).thenReturn(ext);
        when(validator.resolveContentType(eq(ext))).thenReturn("image/" + ext);
        return validator;
    }

    /**
     * 构造上传文件替身。
     *
     * @param content  文件内容
     * @param filename 原始文件名
     * @return 上传文件替身
     * @throws Exception 读取字节时抛出
     */
    private MultipartFile file(byte[] content, String filename) throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn((long) content.length);
        when(file.getBytes()).thenReturn(content);
        when(file.getOriginalFilename()).thenReturn(filename);
        return file;
    }

    /**
     * 构造未删除的文件元数据。
     *
     * @param uploaderId 上传人编号
     * @return 文件元数据
     */
    private FileObjectDO fileObject(Long uploaderId) {
        FileObjectDO fileObject = new FileObjectDO();
        fileObject.setFileKey("fk");
        fileObject.setObjectKey("ab/sample.png");
        fileObject.setUploaderId(uploaderId);
        fileObject.setIsDeleted(NOT_DELETED);
        return fileObject;
    }
}

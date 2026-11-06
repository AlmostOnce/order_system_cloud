package com.hbue.ordering.product.service;

import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.web.media.ManagedImageClient;
import com.hbue.ordering.product.model.DiningWindowDO;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 店铺图片数量、顺序及文件校验回归；本次仅提供测试源码，不运行Java测试。 */
class WindowImageGalleryTest {
    private static final String A = "a".repeat(32), B = "b".repeat(32);

    /** 历史封面自动作为单图列表；显式清空后不复活旧图。 */
    @Test void legacyCoverAndExplicitEmpty() {
        DiningWindowDO window = new DiningWindowDO(); window.setImageFileKey(A);
        assertEquals(List.of(A), WindowImageGallery.read(window));
        window.setImageFileKeys("[]");
        assertEquals(List.of(), WindowImageGallery.read(window));
    }

    /** 大小写归一后也不能重复；超五张、空键和非文件键一律拒绝。 */
    @Test void invalidKeysAndLimitAreRejected() {
        assertThrows(BusinessException.class, () -> WindowImageGallery.validate(List.of(A, A.toUpperCase())));
        assertThrows(BusinessException.class, () -> WindowImageGallery.validate(List.of("")));
        assertThrows(BusinessException.class, () -> WindowImageGallery.validate(List.of("https://image")));
        assertThrows(BusinessException.class, () -> WindowImageGallery.validate(List.of(A,B,"c".repeat(32),"d".repeat(32),"e".repeat(32),"f".repeat(32))));
    }

    /** 已绑定图片可保留或重排，新加入的文件必须验证操作者及窗口业务归属。 */
    @Test void verifyOnlyNewReferencesAndPreserveOrder() {
        ManagedImageClient files = mock(ManagedImageClient.class);
        when(files.requireImageKey(B, 7L, "WINDOW_IMAGE", 9L)).thenReturn(B);
        assertEquals(List.of(B,A), WindowImageGallery.resolve(List.of(B,A), List.of(A), 7L, 9L, files));
        verify(files).requireImageKey(B,7L,"WINDOW_IMAGE",9L);
        verifyNoMoreInteractions(files);
        assertEquals(List.of(A,B), WindowImageGallery.read(jsonWindow(List.of(A,B))));
        assertEquals(List.of(B,A), WindowImageGallery.withCover(B,List.of(A,B)));
        assertEquals(List.of(B), WindowImageGallery.withCover("",List.of(A,B)));
    }

    /** 文件服务拒绝越权时不吞掉异常，调用方事务不能继续写入图片引用。 */
    @Test void fileOwnershipFailurePropagates() {
        ManagedImageClient files = mock(ManagedImageClient.class);
        when(files.requireImageKey(B,7L,"WINDOW_IMAGE",9L)).thenThrow(new IllegalStateException("denied"));
        assertThrows(IllegalStateException.class, () -> WindowImageGallery.resolve(List.of(B),List.of(),7L,9L,files));
    }

    private DiningWindowDO jsonWindow(List<String> keys) {
        DiningWindowDO window = new DiningWindowDO();
        window.setImageFileKeys(WindowImageGallery.json(keys)); return window;
    }
}

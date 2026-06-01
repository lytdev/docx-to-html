package cn.p4u.smart.renderer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class WmfConverterTest {

    @AfterEach
    void resetStrategyCache() {
        WmfConverter.resetStrategy();
    }

    @Test
    void isWmfOrEmfDetectsCorrectMimeTypes() {
        assertTrue(WmfConverter.isWmfOrEmf("image/x-wmf"));
        assertTrue(WmfConverter.isWmfOrEmf("image/x-emf"));
        assertFalse(WmfConverter.isWmfOrEmf("image/png"));
        assertFalse(WmfConverter.isWmfOrEmf("image/jpeg"));
        assertFalse(WmfConverter.isWmfOrEmf(null));
    }

    @Test
    void isWmfOrEmfPathDetectsCorrectExtensions() {
        assertTrue(WmfConverter.isWmfOrEmfPath(Paths.get("media/image1.wmf")));
        assertTrue(WmfConverter.isWmfOrEmfPath(Paths.get("media/image2.emf")));
        assertTrue(WmfConverter.isWmfOrEmfPath(Paths.get("image.WMF")));
        assertFalse(WmfConverter.isWmfOrEmfPath(Paths.get("media/image1.png")));
        assertFalse(WmfConverter.isWmfOrEmfPath(Paths.get("media/image1.jpg")));
        assertFalse(WmfConverter.isWmfOrEmfPath(null));
    }

    @Test
    void convertToPngReturnsNullForEmptyInput() {
        assertNull(WmfConverter.convertToPng(null, 0, 0));
        assertNull(WmfConverter.convertToPng(new byte[0], 100, 100));
    }

    @Test
    void imagemagickPathPropIsCorrect() {
        assertEquals("docx2html.imagemagick.path", WmfConverter.IMAGEMAGICK_PATH_PROP);
    }
}

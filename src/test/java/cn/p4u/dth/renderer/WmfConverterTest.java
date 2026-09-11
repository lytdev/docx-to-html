package cn.p4u.dth.renderer;

import cn.p4u.dth.converter.ConversionConfig;
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
    void conversionConfigDefaultsToAutoStrategy() {
        ConversionConfig config = ConversionConfig.defaults();

        assertEquals(WmfConversionStrategy.AUTO, config.wmfStrategy());
        assertNull(config.imageMagickPath());
    }

    @Test
    void conversionConfigAcceptsStrategyAndNormalizesImageMagickPath() {
        ImageUriResolver resolver = (path, mimeType) ->
                new ImageUriResolver.ResolveResult(path.toUri().toString(), mimeType);
        ConversionConfig config = new ConversionConfig(
                resolver,
                WmfConversionStrategy.IMAGEMAGICK,
                "  C:\\ImageMagick\\magick.exe  ");

        assertEquals(WmfConversionStrategy.IMAGEMAGICK, config.wmfStrategy());
        assertEquals("C:\\ImageMagick\\magick.exe", config.imageMagickPath());
    }

    @Test
    void noneStrategySkipsConversion() {
        ImageUriResolver resolver = (path, mimeType) ->
                new ImageUriResolver.ResolveResult(path.toUri().toString(), mimeType);
        ConversionConfig config = new ConversionConfig(
                resolver, null, WmfConversionStrategy.NONE, null);

        assertNull(WmfConverter.convertToPng(new byte[]{1, 2, 3}, 100, 100, config));
    }

    @Test
    void configImageMagickPathOverridesLegacySystemProperty() {
        String oldValue = System.getProperty(WmfConverter.IMAGEMAGICK_PATH_PROP);
        try {
            System.setProperty(WmfConverter.IMAGEMAGICK_PATH_PROP, "legacy-magick");
            assertEquals("configured-magick",
                    WmfConverter.effectiveImageMagickPath(" configured-magick "));
            assertEquals("legacy-magick", WmfConverter.effectiveImageMagickPath(null));
        } finally {
            if (oldValue == null) {
                System.clearProperty(WmfConverter.IMAGEMAGICK_PATH_PROP);
            } else {
                System.setProperty(WmfConverter.IMAGEMAGICK_PATH_PROP, oldValue);
            }
        }
    }

    @Test
    void imagemagickPathPropIsCorrect() {
        assertEquals("docx2html.imagemagick.path", WmfConverter.IMAGEMAGICK_PATH_PROP);
    }
}

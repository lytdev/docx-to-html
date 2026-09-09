package cn.p4u.smart.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import cn.p4u.smart.renderer.ImageUriResolver;
import cn.p4u.smart.renderer.WmfConversionStrategy;
import org.junit.jupiter.api.Test;

class ConversionConfigTest {

  @Test
  void builderUsesDocumentedDefaults() {
    ConversionConfig config = ConversionConfig.builder().build();

    assertEquals(WmfConversionStrategy.AUTO, config.wmfStrategy());
    assertNull(config.tmpDir());
    assertNull(config.imageMagickPath());
  }

  @Test
  void builderCreatesReadableMultiOptionConfiguration() {
    ImageUriResolver resolver = (path, mimeType) ->
        new ImageUriResolver.ResolveResult("memory:test", mimeType);

    ConversionConfig config = ConversionConfig.builder()
        .imageUriResolver(resolver)
        .latexRenderUrl(null)
        .tmpDir("D:/docx-temp")
        .wmfStrategy(WmfConversionStrategy.IMAGEMAGICK)
        .imageMagickPath(" C:/ImageMagick/magick.exe ")
        .build();

    assertSame(resolver, config.imageUriResolver());
    assertNull(config.latexRenderUrl());
    assertEquals("D:/docx-temp", config.tmpDir());
    assertEquals(WmfConversionStrategy.IMAGEMAGICK, config.wmfStrategy());
    assertEquals("C:/ImageMagick/magick.exe", config.imageMagickPath());
  }

  @Test
  void toBuilderCopiesExistingValues() {
    ConversionConfig original = ConversionConfig.builder()
        .tmpDir("D:/first")
        .wmfStrategy(WmfConversionStrategy.NONE)
        .build();

    ConversionConfig changed = original.toBuilder().tmpDir("D:/second").build();

    assertEquals("D:/first", original.tmpDir());
    assertEquals("D:/second", changed.tmpDir());
    assertEquals(WmfConversionStrategy.NONE, changed.wmfStrategy());
  }
}

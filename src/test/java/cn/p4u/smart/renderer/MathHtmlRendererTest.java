package cn.p4u.smart.renderer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.p4u.smart.converter.ConversionConfig;
import cn.p4u.smart.model.MathElement;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MathHtmlRendererTest {
  @TempDir Path resourceRoot;

  @Test
  void imageHasHigherPriorityThanMathMlAndLatex() throws Exception {
    Path image = resourceRoot.resolve("word/media/formula.png");
    Files.createDirectories(image.getParent());
    Files.write(image, new byte[] {1, 2, 3});
    AtomicBoolean resolved = new AtomicBoolean();
    ImageUriResolver resolver = (path, mimeType) -> {
      resolved.set(true);
      assertEquals(image, path);
      return new ImageUriResolver.ResolveResult("memory:formula", mimeType);
    };
    ConversionConfig config = ConversionConfig.builder()
        .imageUriResolver(resolver)
        .build();
    MathElement math = new MathElement("x", "<math>ignored</math>",
        "media/formula.png", "image/png");

    StringBuilder html = new StringBuilder();
    MathHtmlRenderer.render(html, math, config, resourceRoot);

    assertTrue(resolved.get());
    assertTrue(html.toString().startsWith("<img src=\"memory:formula\""));
    assertTrue(html.toString().contains("data-latex=\"x\""));
  }

  @Test
  void mathMlHasHigherPriorityThanOnlineLatex() {
    ConversionConfig config = ConversionConfig.builder()
        .latexRenderUrl("https://example.test/{latex}")
        .build();
    MathElement math = new MathElement("x", "<math><mi>x</mi></math>", null, null);

    StringBuilder html = new StringBuilder();
    MathHtmlRenderer.render(html, math, config, resourceRoot);

    assertEquals("<math><mi>x</mi></math>", html.toString());
  }

  @Test
  void latexTextFallbackEscapesHtml() {
    ConversionConfig config = ConversionConfig.builder().latexRenderUrl(null).build();
    MathElement math = new MathElement("a<b&c", null, null);

    StringBuilder html = new StringBuilder();
    MathHtmlRenderer.render(html, math, config, resourceRoot);

    assertEquals(
        "<span data-latex=\"a<b&amp;c\" class=\"latex-formula\">a&lt;b&amp;c</span>",
        html.toString());
  }
}

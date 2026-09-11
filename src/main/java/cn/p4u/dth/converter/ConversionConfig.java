package cn.p4u.dth.converter;

import cn.p4u.dth.renderer.ImageUriResolver;
import cn.p4u.dth.renderer.WmfConversionStrategy;
import java.util.Objects;

/**
 * DOCX 转换选项。
 *
 * <p>这是一个不可变 record，创建后配置不会被其他线程修改。参数较少时可以继续使用构造器；
 * 参数较多时建议使用 {@link #builder()}，避免连续多个 String 参数难以辨认。
 */
public record ConversionConfig(
    ImageUriResolver imageUriResolver,
    String latexRenderUrl,
    String tmpDir,
    WmfConversionStrategy wmfStrategy,
    String imageMagickPath) {

  private static final String DEFAULT_LATEX_RENDER_URL =
      "https://latex.codecogs.com/svg.image?{latex}";

  public ConversionConfig {
    Objects.requireNonNull(imageUriResolver, "imageUriResolver");
    wmfStrategy = Objects.requireNonNullElse(wmfStrategy, WmfConversionStrategy.AUTO);
    imageMagickPath = normalizePath(imageMagickPath);
  }

  /** 创建使用指定图片资源处理器的配置。 */
  public ConversionConfig(ImageUriResolver imageUriResolver) {
    this(imageUriResolver, DEFAULT_LATEX_RENDER_URL, null,
        WmfConversionStrategy.AUTO, null);
  }

  public ConversionConfig(ImageUriResolver imageUriResolver, String tmpDir) {
    this(imageUriResolver, DEFAULT_LATEX_RENDER_URL, tmpDir,
        WmfConversionStrategy.AUTO, null);
  }

  /** 创建包含 WMF 转换策略、使用默认公式地址且不指定临时目录的配置。 */
  public ConversionConfig(
      ImageUriResolver imageUriResolver,
      WmfConversionStrategy wmfStrategy,
      String imageMagickPath) {
    this(imageUriResolver, DEFAULT_LATEX_RENDER_URL, null, wmfStrategy, imageMagickPath);
  }

  /** 保留原三参数构造方式，默认自动选择 WMF 转换策略。 */
  public ConversionConfig(
      ImageUriResolver imageUriResolver, String latexRenderUrl, String tmpDir) {
    this(imageUriResolver, latexRenderUrl, tmpDir, WmfConversionStrategy.AUTO, null);
  }

  /** 创建包含 WMF 转换策略的配置。 */
  public ConversionConfig(
      ImageUriResolver imageUriResolver,
      String tmpDir,
      WmfConversionStrategy wmfStrategy,
      String imageMagickPath) {
    this(imageUriResolver, DEFAULT_LATEX_RENDER_URL, tmpDir, wmfStrategy, imageMagickPath);
  }

  /** Convenience factory with Image2Base64Resolver as default. */
  public static ConversionConfig defaults() {
    return new ConversionConfig(new cn.p4u.dth.renderer.Image2Base64Resolver());
  }

  /**
   * 创建配置构建器（Builder 模式）。
   *
   * <p>构建器先收集可选参数，最后一次性生成不可变配置，适合 Spring Bean 配置代码。</p>
   */
  public static Builder builder() {
    return new Builder();
  }

  /** 使用当前配置创建构建器，便于只修改其中一项。 */
  public Builder toBuilder() {
    return new Builder()
        .imageUriResolver(imageUriResolver)
        .latexRenderUrl(latexRenderUrl)
        .tmpDir(tmpDir)
        .wmfStrategy(wmfStrategy)
        .imageMagickPath(imageMagickPath);
  }

  /**
   * ConversionConfig 的构建器。
   *
   * <p>每个 setter 都返回当前对象，因此可以连续调用；build() 才真正创建配置。</p>
   */
  public static final class Builder {
    private ImageUriResolver imageUriResolver =
        new cn.p4u.dth.renderer.Image2Base64Resolver();
    private String latexRenderUrl = DEFAULT_LATEX_RENDER_URL;
    private String tmpDir;
    private WmfConversionStrategy wmfStrategy = WmfConversionStrategy.AUTO;
    private String imageMagickPath;

    private Builder() {}

    public Builder imageUriResolver(ImageUriResolver imageUriResolver) {
      this.imageUriResolver = Objects.requireNonNull(imageUriResolver, "imageUriResolver");
      return this;
    }

    public Builder latexRenderUrl(String latexRenderUrl) {
      this.latexRenderUrl = latexRenderUrl;
      return this;
    }

    public Builder tmpDir(String tmpDir) {
      this.tmpDir = tmpDir;
      return this;
    }

    public Builder wmfStrategy(WmfConversionStrategy wmfStrategy) {
      this.wmfStrategy = Objects.requireNonNull(wmfStrategy, "wmfStrategy");
      return this;
    }

    public Builder imageMagickPath(String imageMagickPath) {
      this.imageMagickPath = imageMagickPath;
      return this;
    }

    /** 根据当前收集到的参数创建不可变配置。 */
    public ConversionConfig build() {
      return new ConversionConfig(
          imageUriResolver, latexRenderUrl, tmpDir, wmfStrategy, imageMagickPath);
    }
  }

  private static String normalizePath(String path) {
    if (path == null || path.isBlank()) {
      return null;
    }
    return path.trim();
  }
}

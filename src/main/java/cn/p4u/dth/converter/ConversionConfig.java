package cn.p4u.dth.converter;

import cn.p4u.dth.renderer.ImageUriResolver;
import cn.p4u.dth.renderer.WmfConversionStrategy;
import java.util.Objects;

/**
 * DOCX 转换选项。
 *
 * <p>这是一个不可变 record，创建后配置不会被其他线程修改。参数较少时可以继续使用构造器；
 * 参数较多时建议使用 {@link #builder()}，避免连续多个 String 参数难以辨认。
 *
 * @param imageUriResolver 图片资源地址解析器，不可为 {@code null}
 * @param latexRenderUrl LaTeX 在线渲染地址模板，可为 {@code null}
 * @param tmpDir 临时目录根路径；为 {@code null} 时使用系统临时目录
 * @param wmfStrategy WMF/EMF 转换策略；为 {@code null} 时使用自动策略
 * @param imageMagickPath ImageMagick 可执行文件路径；为空时使用系统配置
 */
public record ConversionConfig(
    ImageUriResolver imageUriResolver,
    String latexRenderUrl,
    String tmpDir,
    WmfConversionStrategy wmfStrategy,
    String imageMagickPath) {

  private static final String DEFAULT_LATEX_RENDER_URL =
      "https://latex.codecogs.com/svg.image?{latex}";

  /** 校验并规范化完整配置。 */
  public ConversionConfig {
    Objects.requireNonNull(imageUriResolver, "imageUriResolver");
    wmfStrategy = Objects.requireNonNullElse(wmfStrategy, WmfConversionStrategy.AUTO);
    imageMagickPath = normalizePath(imageMagickPath);
  }

  /**
   * 创建使用指定图片资源处理器、其余选项采用默认值的配置。
   *
   * @param imageUriResolver 图片资源地址解析器，不可为 {@code null}
   */
  public ConversionConfig(ImageUriResolver imageUriResolver) {
    this(imageUriResolver, DEFAULT_LATEX_RENDER_URL, null,
        WmfConversionStrategy.AUTO, null);
  }

  /**
   * 创建使用指定图片资源处理器和临时目录的配置。
   *
   * @param imageUriResolver 图片资源地址解析器，不可为 {@code null}
   * @param tmpDir 临时目录根路径；为 {@code null} 时使用系统临时目录
   */
  public ConversionConfig(ImageUriResolver imageUriResolver, String tmpDir) {
    this(imageUriResolver, DEFAULT_LATEX_RENDER_URL, tmpDir,
        WmfConversionStrategy.AUTO, null);
  }

  /**
   * 创建包含 WMF 转换策略、使用默认公式地址且不指定临时目录的配置。
   *
   * @param imageUriResolver 图片资源地址解析器，不可为 {@code null}
   * @param wmfStrategy WMF/EMF 转换策略，不可为 {@code null}
   * @param imageMagickPath ImageMagick 可执行文件路径；为空时使用系统配置
   */
  public ConversionConfig(
      ImageUriResolver imageUriResolver,
      WmfConversionStrategy wmfStrategy,
      String imageMagickPath) {
    this(imageUriResolver, DEFAULT_LATEX_RENDER_URL, null, wmfStrategy, imageMagickPath);
  }

  /**
   * 保留原三参数构造方式，默认自动选择 WMF 转换策略。
   *
   * @param imageUriResolver 图片资源地址解析器，不可为 {@code null}
   * @param latexRenderUrl LaTeX 在线渲染地址模板，可为 {@code null}
   * @param tmpDir 临时目录根路径；为 {@code null} 时使用系统临时目录
   */
  public ConversionConfig(
      ImageUriResolver imageUriResolver, String latexRenderUrl, String tmpDir) {
    this(imageUriResolver, latexRenderUrl, tmpDir, WmfConversionStrategy.AUTO, null);
  }

  /**
   * 创建包含 WMF 转换策略、使用默认公式地址的配置。
   *
   * @param imageUriResolver 图片资源地址解析器，不可为 {@code null}
   * @param tmpDir 临时目录根路径；为 {@code null} 时使用系统临时目录
   * @param wmfStrategy WMF/EMF 转换策略，不可为 {@code null}
   * @param imageMagickPath ImageMagick 可执行文件路径；为空时使用系统配置
   */
  public ConversionConfig(
      ImageUriResolver imageUriResolver,
      String tmpDir,
      WmfConversionStrategy wmfStrategy,
      String imageMagickPath) {
    this(imageUriResolver, DEFAULT_LATEX_RENDER_URL, tmpDir, wmfStrategy, imageMagickPath);
  }

  /**
   * 创建默认配置，图片使用 Base64 内嵌方式处理。
   *
   * @return 默认转换配置
   */
  public static ConversionConfig defaults() {
    return new ConversionConfig(new cn.p4u.dth.renderer.Image2Base64Resolver());
  }

  /**
   * 创建配置构建器（Builder 模式）。
   *
   * <p>构建器先收集可选参数，最后一次性生成不可变配置，适合 Spring Bean 配置代码。</p>
   *
   * @return 新的配置构建器
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * 使用当前配置创建构建器，便于只修改其中一项。
   *
   * @return 已复制当前配置值的构建器
   */
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

    /**
     * 设置图片资源地址解析器。
     *
     * @param imageUriResolver 图片资源地址解析器，不可为 {@code null}
     * @return 当前构建器
     */
    public Builder imageUriResolver(ImageUriResolver imageUriResolver) {
      this.imageUriResolver = Objects.requireNonNull(imageUriResolver, "imageUriResolver");
      return this;
    }

    /**
     * 设置 LaTeX 在线渲染地址模板。
     *
     * @param latexRenderUrl LaTeX 在线渲染地址模板，可为 {@code null}
     * @return 当前构建器
     */
    public Builder latexRenderUrl(String latexRenderUrl) {
      this.latexRenderUrl = latexRenderUrl;
      return this;
    }

    /**
     * 设置临时目录根路径。
     *
     * @param tmpDir 临时目录根路径；为 {@code null} 时使用系统临时目录
     * @return 当前构建器
     */
    public Builder tmpDir(String tmpDir) {
      this.tmpDir = tmpDir;
      return this;
    }

    /**
     * 设置 WMF/EMF 转换策略。
     *
     * @param wmfStrategy WMF/EMF 转换策略，不可为 {@code null}
     * @return 当前构建器
     */
    public Builder wmfStrategy(WmfConversionStrategy wmfStrategy) {
      this.wmfStrategy = Objects.requireNonNull(wmfStrategy, "wmfStrategy");
      return this;
    }

    /**
     * 设置 ImageMagick 可执行文件路径。
     *
     * @param imageMagickPath ImageMagick 可执行文件路径；为空时使用系统配置
     * @return 当前构建器
     */
    public Builder imageMagickPath(String imageMagickPath) {
      this.imageMagickPath = imageMagickPath;
      return this;
    }

    /**
     * 根据当前收集到的参数创建不可变配置。
     *
     * @return 新的不可变转换配置
     */
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

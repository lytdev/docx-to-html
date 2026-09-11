package cn.p4u.dth.renderer;

import cn.p4u.dth.converter.ConversionConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * WMF/EMF 转 PNG 的统一入口（Facade 模式）。
 *
 * <p>这个类只做三件事：识别格式、管理临时文件、调用选中的转换策略。
 * ImageMagick 和 PowerShell 的命令细节分别放在独立策略类中，初学者可以沿着
 * WmfConverter → WmfRasterizerFactory → WmfRasterizer 的顺序阅读。</p>
 */
public final class WmfConverter {
  private static final Logger LOG = Logger.getLogger(WmfConverter.class.getName());

  /** 旧版系统属性名；新代码优先使用 ConversionConfig.imageMagickPath()。 */
  public static final String IMAGEMAGICK_PATH_PROP = "docx2html.imagemagick.path";

  private WmfConverter() {}

  /** 清空 AUTO 策略探测缓存，主要供测试使用。 */
  static void resetStrategy() {
    WmfRasterizerFactory.resetCache();
  }

  /** 判断 MIME 类型是否表示 WMF 或 EMF。 */
  public static boolean isWmfOrEmf(String mimeType) {
    return "image/x-wmf".equals(mimeType) || "image/x-emf".equals(mimeType);
  }

  /** 判断路径扩展名是否为 .wmf 或 .emf，比较时忽略大小写。 */
  public static boolean isWmfOrEmfPath(Path path) {
    if (path == null) {
      return false;
    }
    String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
    return fileName.endsWith(".wmf") || fileName.endsWith(".emf");
  }

  /**
   * 使用默认 AUTO 策略转换。保留该重载是为了兼容已有调用代码。
   */
  public static byte[] convertToPng(byte[] data, int logicalWidth, int logicalHeight) {
    return convertToPng(
        data, logicalWidth, logicalHeight, WmfConversionStrategy.AUTO, null);
  }

  /** 按 ConversionConfig 中的策略转换 WMF/EMF 数据。 */
  public static byte[] convertToPng(
      byte[] data, int logicalWidth, int logicalHeight, ConversionConfig config) {
    Objects.requireNonNull(config, "config");
    return convertToPng(
        data,
        logicalWidth,
        logicalHeight,
        config.wmfStrategy(),
        config.imageMagickPath());
  }

  private static byte[] convertToPng(
      byte[] data,
      int logicalWidth,
      int logicalHeight,
      WmfConversionStrategy strategy,
      String imageMagickPath) {
    if (data == null || data.length == 0) {
      return null;
    }

    WmfRasterizer rasterizer = WmfRasterizerFactory.create(strategy, imageMagickPath);
    if (!rasterizer.isAvailable()) {
      return null;
    }

    Path source = null;
    Path target = null;
    try {
      // 外部程序通常只接受文件路径，因此在调用前把内存数据落到临时文件。
      source = Files.createTempFile("wmf2png_", ".wmf");
      target = Files.createTempFile("wmf2png_", ".png");
      Files.write(source, data);

      if (rasterizer.rasterize(source, target, logicalWidth, logicalHeight)
          && Files.size(target) > 0) {
        return Files.readAllBytes(target);
      }
      return null;
    } catch (Exception e) {
      LOG.warning("WMF→PNG conversion failed: " + e.getMessage());
      return null;
    } finally {
      deleteQuietly(source);
      deleteQuietly(target);
    }
  }

  /** 配置参数优先，旧系统属性兜底；保留包可见性便于单元测试。 */
  static String effectiveImageMagickPath(String configuredPath) {
    return WmfRasterizerFactory.effectiveImageMagickPath(configuredPath);
  }

  /** 临时文件清理采用 best-effort，不能掩盖真正的转换结果。 */
  private static void deleteQuietly(Path path) {
    if (path == null) {
      return;
    }
    try {
      Files.deleteIfExists(path);
    } catch (Exception ignored) {
      // 清理失败不改变转换结果；操作系统后续仍可回收临时目录。
    }
  }
}

package cn.p4u.smart.renderer;

import java.io.OutputStream;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;

/**
 * 根据配置创建 WMF 栅格化策略（Factory 模式）。
 *
 * <p>策略“怎么选”集中在这里，策略“怎么执行”由各自实现类负责。</p>
 */
final class WmfRasterizerFactory {
  private static final Logger LOG = Logger.getLogger(WmfRasterizerFactory.class.getName());
  private static final ConcurrentMap<String, WmfRasterizer> AUTO_CACHE =
      new ConcurrentHashMap<>();

  private WmfRasterizerFactory() {}

  static WmfRasterizer create(
      WmfConversionStrategy requestedStrategy, String configuredImageMagickPath) {
    WmfConversionStrategy strategy =
        Objects.requireNonNullElse(requestedStrategy, WmfConversionStrategy.AUTO);

    return switch (strategy) {
      case NONE -> DisabledWmfRasterizer.INSTANCE;
      case POWERSHELL -> new PowerShellWmfRasterizer();
      case IMAGEMAGICK -> createImageMagick(configuredImageMagickPath);
      case AUTO -> createAutomatically(configuredImageMagickPath);
    };
  }

  private static WmfRasterizer createAutomatically(String configuredImageMagickPath) {
    String effectivePath = effectiveImageMagickPath(configuredImageMagickPath);
    String cacheKey = effectivePath == null ? "<PATH>" : effectivePath;
    return AUTO_CACHE.computeIfAbsent(cacheKey, ignored -> detect(effectivePath));
  }

  private static WmfRasterizer detect(String configuredImageMagickPath) {
    String command = resolveImageMagickCommand(configuredImageMagickPath);
    if (command != null) {
      LOG.info("Using ImageMagick for WMF→PNG conversion");
      return new ImageMagickWmfRasterizer(command);
    }
    if (isWindows() && testPowerShell()) {
      LOG.info("Using PowerShell for WMF→PNG conversion");
      return new PowerShellWmfRasterizer();
    }

    LOG.warning("No WMF→PNG converter available; WMF images will not render in browsers. "
        + "Install ImageMagick or run on Windows with .NET System.Drawing.");
    return DisabledWmfRasterizer.INSTANCE;
  }

  private static WmfRasterizer createImageMagick(String configuredImageMagickPath) {
    String command = resolveImageMagickCommand(configuredImageMagickPath);
    if (command != null) {
      return new ImageMagickWmfRasterizer(command);
    }
    LOG.warning("Configured WMF strategy is IMAGEMAGICK, but ImageMagick is unavailable");
    return DisabledWmfRasterizer.INSTANCE;
  }

  /** 配置对象中的路径优先，旧系统属性只作为兼容兜底。 */
  static String effectiveImageMagickPath(String configuredPath) {
    if (configuredPath != null && !configuredPath.isBlank()) {
      return configuredPath.trim();
    }
    String legacyPath = System.getProperty(WmfConverter.IMAGEMAGICK_PATH_PROP);
    return legacyPath == null || legacyPath.isBlank() ? null : legacyPath.trim();
  }

  static void resetCache() {
    AUTO_CACHE.clear();
  }

  private static String resolveImageMagickCommand(String configuredPath) {
    String effectivePath = effectiveImageMagickPath(configuredPath);
    if (effectivePath != null) {
      if (testImageMagickCommand(effectivePath)) {
        return effectivePath;
      }
      LOG.warning("Configured ImageMagick path '" + effectivePath + "' is not usable");
      return null;
    }

    // ImageMagick 7 使用 magick；ImageMagick 6 通常使用 convert。
    for (String command : List.of("magick", "convert")) {
      if (testImageMagickCommand(command)) {
        return command;
      }
    }
    return null;
  }

  private static boolean testImageMagickCommand(String command) {
    try {
      ProcessBuilder processBuilder = new ProcessBuilder(command, "-version");
      ImageMagickWmfRasterizer.configureEnvironment(processBuilder, command);
      processBuilder.redirectErrorStream(true);
      Process process = processBuilder.start();
      process.getOutputStream().close();
      process.getInputStream().transferTo(OutputStream.nullOutputStream());
      return process.waitFor() == 0;
    } catch (Exception ignored) {
      return false;
    }
  }

  private static boolean testPowerShell() {
    try {
      ProcessBuilder processBuilder = new ProcessBuilder(
          "powershell", "-NoProfile", "-NonInteractive", "-Command",
          "Add-Type -AssemblyName System.Drawing; Write-Host ok");
      processBuilder.redirectErrorStream(true);
      Process process = processBuilder.start();
      process.getOutputStream().close();
      process.getInputStream().transferTo(OutputStream.nullOutputStream());
      return process.waitFor() == 0;
    } catch (Exception ignored) {
      return false;
    }
  }

  private static boolean isWindows() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows");
  }
}

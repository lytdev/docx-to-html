package cn.p4u.smart.renderer;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.logging.Logger;

/** 使用 Windows PowerShell 与 System.Drawing 执行转换的策略。 */
final class PowerShellWmfRasterizer implements WmfRasterizer {
  private static final Logger LOG = Logger.getLogger(PowerShellWmfRasterizer.class.getName());
  private static final int HI_DPI_SCALE = 4;

  @Override
  public boolean isAvailable() {
    return true;
  }

  @Override
  public boolean rasterize(Path source, Path target, int logicalWidth, int logicalHeight) {
    try {
      String sourcePath = source.toAbsolutePath().toString();
      String targetPath = target.toAbsolutePath().toString();
      int renderWidth = logicalWidth > 0 ? logicalWidth * HI_DPI_SCALE : 0;
      int renderHeight = logicalHeight > 0 ? logicalHeight * HI_DPI_SCALE : 0;

      String script = renderWidth > 0 && renderHeight > 0
          ? fixedSizeScript(sourcePath, targetPath, renderWidth, renderHeight)
          : naturalSizeScript(sourcePath, targetPath);

      ProcessBuilder processBuilder = new ProcessBuilder(
          "powershell", "-NoProfile", "-NonInteractive", "-Command", script);
      processBuilder.redirectErrorStream(true);
      Process process = processBuilder.start();
      process.getOutputStream().close();
      String output = readOutput(process);

      int exitCode = process.waitFor();
      if (exitCode != 0) {
        LOG.warning("PowerShell WMF conversion exited with code " + exitCode
            + ". Output: " + output);
        return false;
      }
      return Files.exists(target) && Files.size(target) > 0;
    } catch (Exception e) {
      LOG.warning("PowerShell WMF conversion failed: " + e.getMessage());
      return false;
    }
  }

  private static String fixedSizeScript(
      String sourcePath, String targetPath, int width, int height) {
    return String.format(Locale.ROOT,
        "Add-Type -AssemblyName System.Drawing; "
            + "$img = [System.Drawing.Image]::FromFile('%s'); "
            + "$bmp = New-Object System.Drawing.Bitmap(%d, %d, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb); "
            + "$g = [System.Drawing.Graphics]::FromImage($bmp); "
            + "$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic; "
            + "$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality; "
            + "$g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit; "
            + "$g.Clear([System.Drawing.Color]::Transparent); "
            + "$g.DrawImage($img, 0, 0, %d, %d); "
            + "$g.Dispose(); $bmp.Save('%s', [System.Drawing.Imaging.ImageFormat]::Png); "
            + "$bmp.Dispose(); $img.Dispose()",
        escape(sourcePath), width, height, width, height, escape(targetPath));
  }

  private static String naturalSizeScript(String sourcePath, String targetPath) {
    return String.format(Locale.ROOT,
        "Add-Type -AssemblyName System.Drawing; "
            + "$img = [System.Drawing.Image]::FromFile('%s'); "
            + "$w = [int]($img.Width * %d); $h = [int]($img.Height * %d); "
            + "$bmp = New-Object System.Drawing.Bitmap($w, $h, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb); "
            + "$g = [System.Drawing.Graphics]::FromImage($bmp); "
            + "$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic; "
            + "$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality; "
            + "$g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit; "
            + "$g.Clear([System.Drawing.Color]::Transparent); "
            + "$g.DrawImage($img, 0, 0, $w, $h); "
            + "$g.Dispose(); $bmp.Save('%s', [System.Drawing.Imaging.ImageFormat]::Png); "
            + "$bmp.Dispose(); $img.Dispose()",
        escape(sourcePath), 2, 2, escape(targetPath));
  }

  private static String readOutput(Process process) throws Exception {
    StringBuilder output = new StringBuilder();
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(process.getInputStream()))) {
      String line;
      while ((line = reader.readLine()) != null) {
        output.append(line).append('\n');
      }
    }
    return output.toString();
  }

  /** PowerShell 单引号字符串中的单引号要写成两个连续单引号。 */
  private static String escape(String path) {
    return path.replace("'", "''");
  }
}

package cn.p4u.smart.renderer;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/** 使用 ImageMagick 执行 WMF/EMF 转 PNG 的策略。 */
final class ImageMagickWmfRasterizer implements WmfRasterizer {
  private static final Logger LOG = Logger.getLogger(ImageMagickWmfRasterizer.class.getName());
  private static final int HI_DPI_SCALE = 4;

  private final String command;

  ImageMagickWmfRasterizer(String command) {
    this.command = command;
  }

  @Override
  public boolean isAvailable() {
    return true;
  }

  @Override
  public boolean rasterize(Path source, Path target, int logicalWidth, int logicalHeight) {
    try {
      List<String> arguments = new ArrayList<>();
      arguments.add(command);

      int renderWidth = logicalWidth > 0 ? logicalWidth * HI_DPI_SCALE : 0;
      int renderHeight = logicalHeight > 0 ? logicalHeight * HI_DPI_SCALE : 0;

      if (renderWidth > 0 && renderHeight > 0) {
        arguments.add(source.toAbsolutePath().toString());
        arguments.add("-density");
        arguments.add("300");
        arguments.add("-background");
        arguments.add("transparent");
        arguments.add("-resize");
        arguments.add(renderWidth + "x" + renderHeight);
      } else {
        arguments.add("-density");
        arguments.add("300");
        arguments.add(source.toAbsolutePath().toString());
        arguments.add("-background");
        arguments.add("transparent");
      }
      arguments.add(target.toAbsolutePath().toString());

      ProcessBuilder processBuilder = new ProcessBuilder(arguments);
      configureEnvironment(processBuilder, command);
      processBuilder.redirectErrorStream(true);
      Process process = processBuilder.start();
      process.getOutputStream().close();

      String output = readOutput(process);
      int exitCode = process.waitFor();
      if (exitCode != 0) {
        LOG.warning("ImageMagick WMF conversion exited with code " + exitCode
            + ". Output: " + output);
        return false;
      }
      return Files.exists(target) && Files.size(target) > 0;
    } catch (Exception e) {
      LOG.warning("ImageMagick WMF conversion failed: " + e.getMessage());
      return false;
    }
  }

  /**
   * Windows 会从可执行文件所在目录加载 DLL，因此完整路径模式下把该目录加入 PATH。
   */
  static void configureEnvironment(ProcessBuilder processBuilder, String commandPath) {
    File executable = new File(commandPath);
    File executableDirectory = executable.getParentFile();
    if (executableDirectory == null || !executableDirectory.exists()) {
      return;
    }

    processBuilder.directory(executableDirectory);
    String oldPath = processBuilder.environment().get("PATH");
    String directoryPath = executableDirectory.getAbsolutePath();
    processBuilder.environment().put(
        "PATH",
        oldPath == null || oldPath.isEmpty() ? directoryPath : directoryPath + ";" + oldPath);
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
}

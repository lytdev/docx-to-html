package cn.p4u.smart.renderer;

import java.io.OutputStream;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * WMF/EMF 图像转 PNG 转换器。
 *
 * <p>核心职责：将旧版 Windows Metafile (WMF) 和 Enhanced Metafile (EMF) 格式的图像
 * 转换为浏览器可直接渲染的 PNG 格式。由于浏览器原生不支持 WMF/EMF，必须在服务端
 * 借助外部工具完成格式转换。</p>
 *
 * <p>转换策略（按优先级自动检测）：</p>
 * <ol>
 *   <li><b>ImageMagick</b> — 跨平台方案，需要系统预装 ImageMagick（magick 或 convert 命令），
 *       可通过系统属性 {@value #IMAGEMAGICK_PATH_PROP} 指定可执行文件路径。</li>
 *   <li><b>PowerShell + .NET System.Drawing</b> — 仅 Windows 可用，作为备选方案，
 *       利用系统自带的 .NET 类库进行高质量渲染。</li>
 *   <li><b>NONE</b> — 两种方案均不可用时，转换直接返回 null，图像将不会出现在 HTML 输出中。</li>
 * </ol>
 *
 * <p>主要使用场景：{@link HtmlRenderer} 在渲染流程中遇到 WMF/EMF 图像时，
 * 调用本类的 {@link #convertToPng} 方法将其转为 PNG 字节流，
 * 再由 {@link HtmlRenderer} 通过 {@link ImageUriResolver} 生成对应的 HTML img 标签。</p>
 *
 * <p>该类为无状态的工具类，所有方法均为 static，策略检测结果通过 volatile 缓存，
 * 避免重复执行外部进程探测。</p>
 */
public final class WmfConverter {

    private static final Logger LOG = Logger.getLogger(WmfConverter.class.getName());

    /** 系统属性名，用于指定 ImageMagick 可执行文件的绝对路径 */
    public static final String IMAGEMAGICK_PATH_PROP = "docx2html.imagemagick.path";

    // 高 DPI 缩放倍数：渲染时按 4 倍分辨率生成 PNG，确保在高清屏上依然清晰。
    // HTML 的 width/height 属性会将显示尺寸约束回逻辑像素大小。
    private static final int HI_DPI_SCALE = 4;

    /**
     * 转换策略枚举，表示当前环境可用的 WMF→PNG 转换方式。
     */
    private enum Strategy {
        POWERSHELL,   // 使用 Windows PowerShell + .NET System.Drawing
        IMAGEMAGICK,  // 使用 ImageMagick 命令行工具
        NONE          // 无可用转换方式
    }

    // 缓存已检测到的转换策略，volatile 保证多线程可见性。
    // 只在首次调用时执行检测，后续直接复用结果。
    private static volatile Strategy detected = null;

    private WmfConverter() {}

    /**
     * 重置缓存的转换策略，主要用于测试。
     * 下次调用 {@link #convertToPng} 时会重新检测可用策略。
     */
    static void resetStrategy() {
        detected = null;
    }

    /**
     * 判断给定 MIME 类型是否为 WMF 或 EMF 格式。
     *
     * @param mimeType 图像的 MIME 类型字符串，例如 "image/x-wmf"
     * @return 如果是 WMF 或 EMF 类型返回 true，否则返回 false
     */
    public static boolean isWmfOrEmf(String mimeType) {
        return "image/x-wmf".equals(mimeType) || "image/x-emf".equals(mimeType);
    }

    /**
     * 判断给定文件路径的扩展名是否为 .wmf 或 .emf。
     *
     * @param path 文件路径，可为 null
     * @return 如果路径以 .wmf 或 .emf 结尾（不区分大小写）返回 true；路径为 null 时返回 false
     */
    public static boolean isWmfOrEmfPath(Path path) {
        if (path == null) return false;
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".wmf") || name.endsWith(".emf");
    }

    /**
     * 将 WMF/EMF 原始字节数据转换为 PNG 格式。
     *
     * <p>该方法会自动选择当前环境可用的转换策略（PowerShell 优先，ImageMagick 次之），
     * 将原始 WMF/EMF 数据写入临时文件，调用外部工具转换后读取 PNG 字节。
     * 临时文件在方法退出时无条件删除。</p>
     *
     * @param wmfData       WMF/EMF 图像的原始字节数据
     * @param logicalWidth  逻辑显示宽度（CSS 像素），0 表示未知，将使用 WMF 原生尺寸
     * @param logicalHeight 逻辑显示高度（CSS 像素），0 表示未知，将使用 WMF 原生尺寸
     * @return 转换后的 PNG 字节数组；如果输入为空、无可用策略或转换失败，返回 null
     */
    public static byte[] convertToPng(byte[] wmfData, int logicalWidth, int logicalHeight) {
        // 空数据直接返回，避免无意义的临时文件操作
        if (wmfData == null || wmfData.length == 0) return null;

        // 首次调用时检测并缓存转换策略
        Strategy s = detected;
        if (s == null) {
            s = detectStrategy();
            detected = s;
        }
        // 无可用策略时直接返回 null，不做任何外部调用
        if (s == Strategy.NONE) return null;

        Path tempWmf = null;
        Path tempPng = null;
        try {
            // 将 WMF 字节写入临时文件，供外部工具读取
            tempWmf = Files.createTempFile("wmf2png_", ".wmf");
            Files.write(tempWmf, wmfData);
            // 创建 PNG 临时文件占位（外部工具会覆盖写入）
            tempPng = Files.createTempFile("wmf2png_", ".png");

            boolean ok;
            // 根据检测到的策略分发转换调用
            if (s == Strategy.IMAGEMAGICK) {
                ok = convertViaImageMagick(tempWmf, tempPng, logicalWidth, logicalHeight);
            } else if (s == Strategy.POWERSHELL) {
                ok = convertViaPowerShell(tempWmf, tempPng, logicalWidth, logicalHeight);
            } else {
                ok = false;
            }

            // 转换成功且输出了有效文件，读取 PNG 字节返回
            if (ok && Files.exists(tempPng) && Files.size(tempPng) > 0) {
                return Files.readAllBytes(tempPng);
            }
            return null;
        } catch (Exception e) {
            LOG.warning("WMF→PNG conversion failed: " + e.getMessage());
            return null;
        } finally {
            // 无论成功与否，始终清理临时文件，防止磁盘泄漏
            try { if (tempWmf != null) Files.deleteIfExists(tempWmf); } catch (Exception ignored) {}
            try { if (tempPng != null) Files.deleteIfExists(tempPng); } catch (Exception ignored) {}
        }
    }

    /**
     * 检测当前环境中可用的转换策略。
     *
     * <p>检测顺序：优先测试 ImageMagick（跨平台方案），
     * 不可用时在 Windows 下尝试 PowerShell + .NET System.Drawing。
     * 两者均不可用时返回 NONE。</p>
     *
     * <p>ImageMagick 路径查找优先级：
     * <ol>
     *   <li>系统属性 {@value #IMAGEMAGICK_PATH_PROP} 指定的路径</li>
     *   <li>PATH 中的 {@code magick} 命令（ImageMagick v7）</li>
     *   <li>PATH 中的 {@code convert} 命令（ImageMagick v6）</li>
     * </ol></p>
     *
     * @return 检测到的可用策略；无可用时返回 {@link Strategy#NONE}
     */
    private static Strategy detectStrategy() {
        // 优先尝试 ImageMagick（跨平台兼容）
        if (testImageMagick()) {
            LOG.info("Using ImageMagick for WMF→PNG conversion");
            return Strategy.IMAGEMAGICK;
        }
        // ImageMagick 不可用时，Windows 平台尝试 PowerShell 方案
        if (isWindows() && testPowerShell()) {
            LOG.info("Using PowerShell for WMF→PNG conversion");
            return Strategy.POWERSHELL;
        }
        // 两种方案均不可用，发出警告：WMF 图像将不会在浏览器中渲染
        LOG.warning("No WMF→PNG converter available; WMF images will not render in browsers. "
                + "Install ImageMagick or run on Windows with .NET System.Drawing.");
        return Strategy.NONE;
    }

    /**
     * 判断当前操作系统是否为 Windows。
     *
     * @return 操作系统名称包含 "windows" 时返回 true
     */
    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows");
    }

    /**
     * 测试 PowerShell 是否可用且能否加载 .NET System.Drawing 程序集。
     *
     * <p>执行一条简单的 PowerShell 命令来验证：加载 System.Drawing 并输出 "ok"。
     * 若退出码为 0 则认为可用。</p>
     *
     * @return PowerShell + System.Drawing 可用返回 true，否则返回 false
     */
    private static boolean testPowerShell() {
        try {
            // 构造探测命令：尝试加载 System.Drawing 程序集，成功则输出 ok
            ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command",
                    "Add-Type -AssemblyName System.Drawing; Write-Host ok");
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            // 关闭进程的输入流，避免进程阻塞等待输入
            proc.getOutputStream().close();
            // 消费输出流，防止缓冲区满导致进程挂起
            proc.getInputStream().transferTo(OutputStream.nullOutputStream());
            int code = proc.waitFor();
            return code == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 测试 ImageMagick 是否已安装且可用。
     *
     * <p>查找顺序：</p>
     * <ol>
     *   <li>系统属性 {@value #IMAGEMAGICK_PATH_PROP} 指定的可执行文件路径</li>
     *   <li>PATH 中的 {@code magick} 命令（ImageMagick v7）</li>
     *   <li>PATH 中的 {@code convert} 命令（ImageMagick v6）</li>
     * </ol>
     * <p>任一命令退出码为 0 即认为可用。</p>
     *
     * @return ImageMagick 可用返回 true，否则返回 false
     */
    private static boolean testImageMagick() {
        // 优先使用系统属性指定的路径
        String configuredPath = System.getProperty(IMAGEMAGICK_PATH_PROP);
        if (configuredPath != null && !configuredPath.isEmpty()) {
            try {
                ProcessBuilder pb = new ProcessBuilder(configuredPath, "-version");
                configureImageMagickEnv(pb, configuredPath);
                pb.redirectErrorStream(true);
                Process proc = pb.start();
                proc.getOutputStream().close();
                proc.getInputStream().transferTo(OutputStream.nullOutputStream());
                if (proc.waitFor() == 0) return true;
            } catch (Exception e) {
                LOG.warning("Configured ImageMagick path '" + configuredPath + "' is not usable: " + e.getMessage());
            }
        }
        // magick 是 v7 新命令，convert 是 v6 及更早版本的命令
        for (String cmd : Arrays.asList("magick", "convert")) {
            try {
                ProcessBuilder pb = new ProcessBuilder(cmd, "-version");
                pb.redirectErrorStream(true);
                Process proc = pb.start();
                proc.getOutputStream().close();
                proc.getInputStream().transferTo(OutputStream.nullOutputStream());
                if (proc.waitFor() == 0) return true;
            } catch (Exception ignored) {}
        }
        return false;
    }

    /**
     * 为 ImageMagick 进程配置环境变量和工作目录。
     *
     * <p>当 ImageMagick 可执行文件路径由系统属性指定时，需要将其所在目录
     * 加入 PATH 环境变量并设为工作目录，确保 Windows 能正确加载同目录下的
     * 运行时 DLL（vcruntime140.dll 等）。</p>
     *
     * @param pb     待配置的 ProcessBuilder
     * @param cmdPath ImageMagick 可执行文件的绝对路径
     */
    private static void configureImageMagickEnv(ProcessBuilder pb, String cmdPath) {
        File exeFile = new File(cmdPath);
        File exeDir = exeFile.getParentFile();
        if (exeDir != null && exeDir.exists()) {
            pb.directory(exeDir);
            String oldPath = pb.environment().get("PATH");
            String dirPath = exeDir.getAbsolutePath();
            if (oldPath != null && !oldPath.isEmpty()) {
                pb.environment().put("PATH", dirPath + ";" + oldPath);
            } else {
                pb.environment().put("PATH", dirPath);
            }
        }
    }

    /**
     * 获取 ImageMagick 可执行文件路径。
     *
     * <p>优先返回系统属性 {@value #IMAGEMAGICK_PATH_PROP} 指定的路径，
     * 否则依次尝试 PATH 中的 {@code magick}（v7）和 {@code convert}（v6）命令。</p>
     *
     * @return 可用的 ImageMagick 命令路径；无可用时返回 null
     */
    private static String resolveImageMagickCmd() {
        String configuredPath = System.getProperty(IMAGEMAGICK_PATH_PROP);
        if (configuredPath != null && !configuredPath.isEmpty()) {
            return configuredPath;
        }
        for (String cmd : Arrays.asList("magick", "convert")) {
            try {
                ProcessBuilder pb = new ProcessBuilder(cmd, "-version");
                pb.redirectErrorStream(true);
                Process proc = pb.start();
                proc.getOutputStream().close();
                proc.getInputStream().transferTo(OutputStream.nullOutputStream());
                if (proc.waitFor() == 0) return cmd;
            } catch (Exception ignored) {}
        }
        return null;
    }

    /**
     * 通过 PowerShell + .NET System.Drawing 将 WMF/EMF 文件转换为 PNG。
     *
     * <p>转换流程：</p>
     * <ol>
     *   <li>加载源图像文件；</li>
     *   <li>创建指定尺寸的高质量 Bitmap；</li>
     *   <li>设置插值、平滑、文字渲染等高质量模式；</li>
     *   <li>将源图像缩放绘制到 Bitmap 上；</li>
     *   <li>以 PNG 格式保存到目标文件。</li>
     * </ol>
     *
     * <p>当逻辑宽高已知时，按 {@link #HI_DPI_SCALE} 倍放大渲染以获得高清输出；
     * 未知时使用源图像原生尺寸的 2 倍作为保底渲染分辨率。</p>
     *
     * @param wmfFile 源 WMF/EMF 临时文件路径
     * @param pngFile 目标 PNG 临时文件路径
     * @param logicalW 逻辑显示宽度（CSS 像素），0 表示未知
     * @param logicalH 逻辑显示高度（CSS 像素），0 表示未知
     * @return 转换成功且输出文件有效返回 true，否则返回 false
     */
    private static boolean convertViaPowerShell(Path wmfFile, Path pngFile, int logicalW, int logicalH) {
        try {
            String wmfPath = wmfFile.toAbsolutePath().toString();
            String pngPath = pngFile.toAbsolutePath().toString();

            // 逻辑尺寸已知时，乘以高 DPI 缩放倍数作为实际渲染像素尺寸
            int renderW = logicalW > 0 ? logicalW * HI_DPI_SCALE : 0;
            int renderH = logicalH > 0 ? logicalH * HI_DPI_SCALE : 0;

            String script;
            if (renderW > 0 && renderH > 0) {
                // 已知逻辑尺寸：按放大后的精确尺寸渲染，确保输出与文档排版一致
                script = String.format(Locale.ROOT,
                    "Add-Type -AssemblyName System.Drawing; " +
                    "$img = [System.Drawing.Image]::FromFile('%s'); " +
                    "$bmp = New-Object System.Drawing.Bitmap(%d, %d, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb); " +
                    "$g = [System.Drawing.Graphics]::FromImage($bmp); " +
                    "$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic; " +
                    "$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality; " +
                    "$g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit; " +
                    "$g.Clear([System.Drawing.Color]::Transparent); " +
                    "$g.DrawImage($img, 0, 0, %d, %d); " +
                    "$g.Dispose(); $bmp.Save('%s', [System.Drawing.Imaging.ImageFormat]::Png); " +
                    "$bmp.Dispose(); $img.Dispose()",
                    escapePs(wmfPath), renderW, renderH, renderW, renderH, escapePs(pngPath));
            } else {
                // 未知逻辑尺寸：回退到源图像原生尺寸 × 2，作为中等质量的保底方案
                script = String.format(Locale.ROOT,
                    "Add-Type -AssemblyName System.Drawing; " +
                    "$img = [System.Drawing.Image]::FromFile('%s'); " +
                    "$w = [int]($img.Width * %d); $h = [int]($img.Height * %d); " +
                    "$bmp = New-Object System.Drawing.Bitmap($w, $h, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb); " +
                    "$g = [System.Drawing.Graphics]::FromImage($bmp); " +
                    "$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic; " +
                    "$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality; " +
                    "$g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit; " +
                    "$g.Clear([System.Drawing.Color]::Transparent); " +
                    "$g.DrawImage($img, 0, 0, $w, $h); " +
                    "$g.Dispose(); $bmp.Save('%s', [System.Drawing.Imaging.ImageFormat]::Png); " +
                    "$bmp.Dispose(); $img.Dispose()",
                    escapePs(wmfPath), 2, 2, escapePs(pngPath));
            }

            // 启动 PowerShell 进程执行转换脚本
            ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive",
                    "-Command", script);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            // 关闭输入流，防止进程阻塞
            proc.getOutputStream().close();

            // 读取 PowerShell 的全部输出，用于失败时的诊断日志
            StringBuilder output = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            } finally {
                reader.close();
            }

            int code = proc.waitFor();
            if (code != 0) {
                // 退出码非零说明脚本执行出错，记录输出便于排查
                LOG.warning("PowerShell WMF conversion exited with code " + code + ". Output: " + output);
                return false;
            }
            // 验证输出文件存在且非空
            return Files.exists(pngFile) && Files.size(pngFile) > 0;
        } catch (Exception e) {
            LOG.warning("PowerShell WMF conversion failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * 通过 ImageMagick 将 WMF/EMF 文件转换为 PNG。
     *
     * <p>使用 {@link #resolveImageMagickCmd()} 获取可执行文件路径。
     * 当逻辑宽高已知时，按 {@link #HI_DPI_SCALE} 倍放大渲染以获得高清输出，
     * 并通过 {@code -resize} 约束像素尺寸；未知时使用 -density 300 作为保底方案。</p>
     *
     * @param wmfFile   源 WMF/EMF 临时文件路径
     * @param pngFile   目标 PNG 临时文件路径
     * @param logicalW  逻辑显示宽度（CSS 像素），0 表示未知
     * @param logicalH  逻辑显示高度（CSS 像素），0 表示未知
     * @return 转换成功且输出文件有效返回 true，否则返回 false
     */
    private static boolean convertViaImageMagick(Path wmfFile, Path pngFile, int logicalW, int logicalH) {
        String cmd = resolveImageMagickCmd();
        if (cmd == null) return false;

        try {
            List<String> args = new ArrayList<String>();
            args.add(cmd);

            int renderW = logicalW > 0 ? logicalW * HI_DPI_SCALE : 0;
            int renderH = logicalH > 0 ? logicalH * HI_DPI_SCALE : 0;

            if (renderW > 0 && renderH > 0) {
                // 已知逻辑尺寸：先按原生密度读入，再缩放到高 DPI 像素尺寸
                args.add(wmfFile.toAbsolutePath().toString());
                args.add("-density");
                args.add("300");
                args.add("-background");
                args.add("transparent");
                args.add("-resize");
                args.add(renderW + "x" + renderH);
            } else {
                // 未知逻辑尺寸：使用固定密度 300 渲染
                args.add("-density");
                args.add("300");
                args.add(wmfFile.toAbsolutePath().toString());
                args.add("-background");
                args.add("transparent");
            }

            args.add(pngFile.toAbsolutePath().toString());

            ProcessBuilder pb = new ProcessBuilder(args);
            configureImageMagickEnv(pb, cmd);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            proc.getOutputStream().close();
            // 读取输出用于失败诊断
            StringBuilder output = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            } finally {
                reader.close();
            }

            int code = proc.waitFor();
            if (code != 0) {
                LOG.warning("ImageMagick WMF conversion exited with code " + code + ". Output: " + output);
                return false;
            }
            return Files.exists(pngFile) && Files.size(pngFile) > 0;
        } catch (Exception e) {
            LOG.warning("ImageMagick WMF conversion failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * 对 PowerShell 单引号字符串中的单引号进行转义。
     *
     * <p>PowerShell 中单引号字符串内的单引号需要用两个连续单引号表示。
     * 此方法用于安全地将文件路径嵌入 PowerShell 脚本，避免路径中包含单引号时
     * 导致脚本语法错误。</p>
     *
     * @param path 待转义的文件路径字符串
     * @return 转义后的路径字符串
     */
    private static String escapePs(String path) {
        return path.replace("'", "''");
    }
}

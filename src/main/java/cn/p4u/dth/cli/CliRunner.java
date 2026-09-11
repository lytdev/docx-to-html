package cn.p4u.dth.cli;

import cn.p4u.dth.converter.ConversionConfig;
import cn.p4u.dth.converter.DocxConverter;
import cn.p4u.dth.renderer.WmfConversionStrategy;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * 命令行入口类，用于在 Linux 服务器上通过命令行调用 docx → HTML 转换。
 *
 * <p>核心职责：解析命令行参数、构造 {@link ConversionConfig}、调用
 * {@link DocxConverter#convert(InputStream, ConversionConfig)} 完成转换，
 * 并将结果写入输出文件或标准输出。
 *
 * <p>主要使用场景：打包成可执行 fat jar 后在无图形界面的 Linux 服务器上运行，例如：
 *
 * <pre>{@code
 *   java -jar docx-to-html-cli.jar report.docx
 *   java -jar docx-to-html-cli.jar -i report.docx -o report.html
 *   java -jar docx-to-html-cli.jar report.docx --wmf-strategy IMAGEMAGICK --imagemagick-path /usr/bin/convert
 * }</pre>
 *
 * <p>退出码约定：{@code 0} 成功；{@code 1} 转换或写入失败；{@code 2} 参数错误。
 */
public final class CliRunner {

  private static final String USAGE = """
      用法:
        java -jar docx-to-html-cli.jar <input.docx> [选项]
        java -jar docx-to-html-cli.jar -i <input.docx> [选项]

      选项:
        -i, --input <path>         输入 .docx 文件路径（必填，也可作为位置参数）
        -o, --output <path>        输出 .html 文件路径（默认：与输入同目录同名）
        --stdout                   将 HTML 输出到标准输出而非文件
        --wmf-strategy <name>      WMF/EMF 转换策略：AUTO | IMAGEMAGICK | POWERSHELL | NONE（默认 AUTO）
        --imagemagick-path <path>  ImageMagick 可执行文件路径（Linux 上如 /usr/bin/convert）
        --tmp-dir <path>           临时目录根路径（默认：系统临时目录）
        --latex-url <url>          LaTeX 公式渲染地址模板
        -h, --help                 显示本帮助

      示例:
        java -jar docx-to-html-cli.jar report.docx
        java -jar docx-to-html-cli.jar -i report.docx -o report.html
        java -jar docx-to-html-cli.jar report.docx --wmf-strategy IMAGEMAGICK --imagemagick-path /usr/bin/convert
        java -jar docx-to-html-cli.jar report.docx --stdout > report.html
      """;

  private CliRunner() {}

  /** 程序入口方法，按退出码结束进程。 */
  public static void main(String[] args) {
    int exitCode = run(args, System.out, System.err);
    System.exit(exitCode);
  }

  /**
   * 执行命令行转换流程，返回进程退出码。
   *
   * @param args 命令行参数
   * @param out  用于输出帮助、状态信息及 {@code --stdout} 模式下的 HTML
   * @param err  用于输出错误信息
   * @return 退出码：{@code 0} 成功，{@code 1} 失败，{@code 2} 参数错误
   */
  static int run(String[] args, PrintStream out, PrintStream err) {
    Options opts;
    try {
      opts = Options.parse(args);
    } catch (UsageException e) {
      err.println("错误: " + e.getMessage());
      err.println();
      err.println(USAGE);
      return 2;
    }

    if (opts.help) {
      out.println(USAGE);
      return 0;
    }
    if (opts.input == null) {
      err.println("错误: 缺少输入文件");
      err.println();
      err.println(USAGE);
      return 2;
    }
    if (opts.stdout && opts.output != null) {
      err.println("错误: --stdout 与 --output 不能同时使用");
      return 2;
    }

    Path input = opts.input;
    if (!Files.exists(input)) {
      err.println("错误: 输入文件不存在: " + input);
      return 2;
    }
    if (!Files.isRegularFile(input)) {
      err.println("错误: 输入路径不是文件: " + input);
      return 2;
    }

    String html;
    try (InputStream in = Files.newInputStream(input)) {
      html = DocxConverter.convert(in, toConfig(opts));
    } catch (Exception e) {
      Throwable cause = e.getCause() != null ? e.getCause() : e;
      String msg = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
      err.println("转换失败: " + msg);
      return 1;
    }

    try {
      if (opts.stdout) {
        out.print(html);
      } else {
        Path output = opts.output != null ? opts.output : deriveOutputPath(input);
        Files.writeString(output, html, StandardCharsets.UTF_8);
        out.println("转换完成: " + output.toAbsolutePath());
      }
    } catch (IOException e) {
      err.println("写入输出失败: " + e.getMessage());
      return 1;
    }
    return 0;
  }

  /** 根据命令行参数构造转换配置，未指定的选项使用默认值。 */
  private static ConversionConfig toConfig(Options opts) {
    ConversionConfig.Builder builder = ConversionConfig.builder()
        .wmfStrategy(opts.wmfStrategy);
    if (opts.imageMagickPath != null) {
      builder.imageMagickPath(opts.imageMagickPath);
    }
    if (opts.tmpDir != null) {
      builder.tmpDir(opts.tmpDir);
    }
    if (opts.latexUrl != null) {
      builder.latexRenderUrl(opts.latexUrl);
    }
    return builder.build();
  }

  /**
   * 根据输入 .docx 文件路径推导输出 .html 文件路径。
   * 替换文件扩展名为 .html（不区分大小写），保留同目录。
   *
   * @param docxPath 输入的 .docx 文件路径
   * @return 对应的 .html 输出路径
   */
  static Path deriveOutputPath(Path docxPath) {
    String fileName = docxPath.getFileName().toString();
    String htmlName;
    if (fileName.toLowerCase(Locale.ROOT).endsWith(".docx")) {
      htmlName = fileName.substring(0, fileName.length() - 5) + ".html";
    } else {
      htmlName = fileName + ".html";
    }
    Path parent = docxPath.getParent();
    return parent != null ? parent.resolve(htmlName) : Paths.get(htmlName);
  }

  /** 解析后的命令行选项。 */
  static final class Options {
    Path input;
    Path output;
    boolean stdout;
    boolean help;
    WmfConversionStrategy wmfStrategy = WmfConversionStrategy.AUTO;
    String imageMagickPath;
    String tmpDir;
    String latexUrl;

    /**
     * 解析命令行参数。
     *
     * @param args 原始命令行参数
     * @return 解析后的选项
     * @throws UsageException 参数非法时抛出
     */
    static Options parse(String[] args) {
      Options opts = new Options();
      int i = 0;
      while (i < args.length) {
        String arg = args[i];
        String name = arg;
        String inline = null;
        int eq = arg.indexOf('=');
        if (arg.startsWith("--") && eq > 0) {
          name = arg.substring(0, eq);
          inline = arg.substring(eq + 1);
        }

        switch (name) {
          case "-h", "--help":
            opts.help = true;
            break;
          case "--stdout":
            opts.stdout = true;
            break;
          case "-i", "--input":
            opts.input = parsePath(consume(args, i, inline, name));
            if (inline == null) {
              i++;
            }
            break;
          case "-o", "--output":
            opts.output = parsePath(consume(args, i, inline, name));
            if (inline == null) {
              i++;
            }
            break;
          case "--wmf-strategy":
            opts.wmfStrategy = parseWmfStrategy(consume(args, i, inline, name));
            if (inline == null) {
              i++;
            }
            break;
          case "--imagemagick-path":
            opts.imageMagickPath = consume(args, i, inline, name);
            if (inline == null) {
              i++;
            }
            break;
          case "--tmp-dir":
            opts.tmpDir = consume(args, i, inline, name);
            if (inline == null) {
              i++;
            }
            break;
          case "--latex-url":
            opts.latexUrl = consume(args, i, inline, name);
            if (inline == null) {
              i++;
            }
            break;
          default:
            if (arg.startsWith("-")) {
              throw new UsageException("未知参数: " + arg);
            }
            if (opts.input != null) {
              throw new UsageException("多余的参数: " + arg);
            }
            opts.input = parsePath(arg);
            break;
        }
        i++;
      }
      return opts;
    }

    /** 读取参数值：优先取 {@code --key=value} 内联值，否则取下一个参数。 */
    private static String consume(String[] args, int index, String inline, String name) {
      if (inline != null) {
        return inline;
      }
      if (index + 1 >= args.length) {
        throw new UsageException("参数 " + name + " 缺少值");
      }
      return args[index + 1];
    }
  }

  /** 解析 WMF 转换策略，忽略大小写。 */
  private static WmfConversionStrategy parseWmfStrategy(String value) {
    String v = value.trim().toUpperCase(Locale.ROOT);
    return switch (v) {
      case "AUTO" -> WmfConversionStrategy.AUTO;
      case "IMAGEMAGICK" -> WmfConversionStrategy.IMAGEMAGICK;
      case "POWERSHELL" -> WmfConversionStrategy.POWERSHELL;
      case "NONE" -> WmfConversionStrategy.NONE;
      default -> throw new UsageException(
          "未知的 WMF 策略: " + value + "（可选: AUTO, IMAGEMAGICK, POWERSHELL, NONE）");
    };
  }

  /** 将字符串转换为路径，非法路径抛出使用异常。 */
  private static Path parsePath(String value) {
    try {
      return Paths.get(value);
    } catch (InvalidPathException e) {
      throw new UsageException("非法的路径: " + value);
    }
  }

  /** 命令行参数解析错误。 */
  private static final class UsageException extends RuntimeException {
    UsageException(String message) {
      super(message);
    }
  }
}

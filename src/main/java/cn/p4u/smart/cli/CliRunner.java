package cn.p4u.smart.cli;

import cn.p4u.smart.converter.ConversionConfig;
import cn.p4u.smart.converter.ConversionConfig.ImageMode;
import cn.p4u.smart.converter.ConversionResult;
import cn.p4u.smart.converter.DocxConverter;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

/**
 * 命令行入口类，基于 picocli 框架将 .docx 文件转换为 HTML。
 * <p>
 * 核心职责：解析命令行参数，构建 ConversionConfig，调用 DocxConverter 完成转换，
 * 并将结果写入文件或输出到 stdout。
 * <p>
 * 主要使用场景：作为项目的可执行 Jar 入口，供用户通过命令行执行转换操作。
 */
@Command(name = "docxToHtml4j", mixinStandardHelpOptions = true,
        description = "Convert .docx files to HTML with high-fidelity style preservation.")
public class CliRunner implements Callable<Integer> {

    /** 输入的 .docx 文件路径，必填参数 */
    @Parameters(index = "0", description = "Input .docx file")
    private Path inputFile;

    /** 输出 HTML 文件路径，可选，不指定时结果输出到 stdout */
    @Option(names = {"-o", "--output"}, description = "Output HTML file (default: stdout)")
    private Path outputFile;

    /** 图片嵌入方式：base64（内嵌）或 link（外部链接），默认 base64 */
    @Option(names = "--image-mode", description = "Image embedding: base64 or link (default: base64)",
            defaultValue = "base64")
    private String imageMode;

    /** 链接模式下图片保存的目录路径，默认 "images" */
    @Option(names = "--image-dir", description = "Directory for linked images (default: images)",
            defaultValue = "images")
    private String imageDir;

    /** 是否保留解压的临时目录用于调试，默认不保留 */
    @Option(names = "--keep-temp", description = "Keep extracted temp directory for debugging")
    private boolean keepTemp;

    /**
     * 执行命令行转换逻辑。
     * 校验输入文件 → 构建 ConversionConfig → 调用 DocxConverter.convert → 输出结果。
     *
     * @return 0 表示成功，1 表示输入文件不存在
     * @throws Exception 转换过程中可能抛出异常
     */
    @Override
    public Integer call() throws Exception {
        // 校验输入文件是否存在
        if (!Files.exists(inputFile)) {
            System.err.println("Input file not found: " + inputFile);
            return 1;
        }

        // 解析图片嵌入模式：link 为外部链接模式，其余均默认 base64 内嵌模式
        ImageMode mode = "link".equalsIgnoreCase(imageMode) ? ImageMode.LINK : ImageMode.BASE64;
        // 构建转换配置，extractedDir 设为 null（由转换器内部自动创建）
        ConversionConfig config = new ConversionConfig(mode, Paths.get(imageDir), null, keepTemp);

        // 执行三阶段转换管线：解压 → 解析 → 渲染
        ConversionResult result = DocxConverter.convert(inputFile, config);
        String html = result.html();

        // 根据是否指定输出文件，决定写入文件或打印到 stdout
        if (outputFile != null) {
            Files.writeString(outputFile, html);
            System.out.println("Written to " + outputFile);
        } else {
            System.out.println(html);
        }

        // 如果启用了 --keep-temp，打印临时目录路径以便调试
        if (keepTemp && result.extractedDir().isPresent()) {
            System.out.println("Extracted dir: " + result.extractedDir().get());
        }

        return 0;
    }

    /**
     * 程序入口方法，通过 picocli 的 CommandLine 启动命令行解析。
     *
     * @param args 命令行参数数组
     */
    public static void main(String[] args) {
        System.exit(new CommandLine(new CliRunner()).execute(args));
    }
}

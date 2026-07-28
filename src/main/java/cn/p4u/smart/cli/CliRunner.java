package cn.p4u.smart.cli;

import cn.p4u.smart.converter.ConversionConfig;
import cn.p4u.smart.converter.ConversionResult;
import cn.p4u.smart.converter.DocxConverter;
import cn.p4u.smart.renderer.Image2Base64Resolver;
import cn.p4u.smart.renderer.Image2OssResolver;
import cn.p4u.smart.renderer.ImageUriResolver;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
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

    @Option(names = "--image-resolver", description = "Image resolver: base64 (default) or oss",
            defaultValue = "base64")
    private String imageResolver;

    @Option(names = "--oss-endpoint", description = "OSS endpoint (required when --image-resolver=oss)")
    private String ossEndpoint;

    @Option(names = "--oss-bucket", description = "OSS bucket name (required when --image-resolver=oss)")
    private String ossBucket;

    @Option(names = "--oss-access-key", description = "OSS access key (required when --image-resolver=oss)")
    private String ossAccessKey;

    @Option(names = "--oss-secret-key", description = "OSS secret key (required when --image-resolver=oss)")
    private String ossSecretKey;

    @Option(names = "--oss-base-path", description = "OSS object key prefix")
    private String ossBasePath;

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

        ImageUriResolver resolver;
        if ("oss".equalsIgnoreCase(imageResolver)) {
            if (ossEndpoint == null || ossBucket == null || ossAccessKey == null || ossSecretKey == null) {
                System.err.println("Error: --oss-endpoint, --oss-bucket, --oss-access-key, and --oss-secret-key are required when --image-resolver=oss");
                return 1;
            }
            Image2OssResolver.OssConfig ossConfig = new Image2OssResolver.OssConfig(
                    ossEndpoint, ossBucket, ossAccessKey, ossSecretKey,
                    ossBasePath != null ? ossBasePath : "");
            resolver = new Image2OssResolver(ossConfig);
        } else {
            resolver = new Image2Base64Resolver();
        }
        ConversionConfig config = new ConversionConfig(resolver, null, keepTemp);

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

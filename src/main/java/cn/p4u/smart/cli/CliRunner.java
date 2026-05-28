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
import java.util.concurrent.Callable;

@Command(name = "docxToHtml4j", mixinStandardHelpOptions = true,
        description = "Convert .docx files to HTML with high-fidelity style preservation.")
public class CliRunner implements Callable<Integer> {

    @Parameters(index = "0", description = "Input .docx file")
    private Path inputFile;

    @Option(names = {"-o", "--output"}, description = "Output HTML file (default: stdout)")
    private Path outputFile;

    @Option(names = "--image-mode", description = "Image embedding: base64 or link (default: base64)",
            defaultValue = "base64")
    private String imageMode;

    @Option(names = "--image-dir", description = "Directory for linked images (default: images)",
            defaultValue = "images")
    private String imageDir;

    @Option(names = "--keep-temp", description = "Keep extracted temp directory for debugging")
    private boolean keepTemp;

    @Override
    public Integer call() throws Exception {
        if (!Files.exists(inputFile)) {
            System.err.println("Input file not found: " + inputFile);
            return 1;
        }

        ImageMode mode = "link".equalsIgnoreCase(imageMode) ? ImageMode.LINK : ImageMode.BASE64;
        var config = new ConversionConfig(mode, Path.of(imageDir), null, keepTemp);

        ConversionResult result = DocxConverter.convert(inputFile, config);
        String html = result.html();

        if (outputFile != null) {
            Files.writeString(outputFile, html);
            System.out.println("Written to " + outputFile);
        } else {
            System.out.println(html);
        }

        if (keepTemp && result.extractedDir().isPresent()) {
            System.out.println("Extracted dir: " + result.extractedDir().get());
        }

        return 0;
    }

    public static void main(String[] args) {
        System.exit(new CommandLine(new CliRunner()).execute(args));
    }
}

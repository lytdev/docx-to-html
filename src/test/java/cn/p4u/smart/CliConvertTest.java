package cn.p4u.smart;

import cn.p4u.smart.converter.ConversionConfig;
import cn.p4u.smart.converter.ConversionResult;
import cn.p4u.smart.converter.DocxConverter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

class CliConvertTest {

  @Test
  void testConvert() throws IOException {
    boolean keepTemp = false;
    Path inputFile = Paths.get("C:\\DevCode\\agile-hub\\docxToHtml4j\\demo.docx");
    Path outputFile = Paths.get("C:\\DevCode\\agile-hub\\docxToHtml4j\\demo.html");
    // Build resolver — base64 by default, OSS if configured
    cn.p4u.smart.renderer.Image2Base64Resolver resolver = new cn.p4u.smart.renderer.Image2Base64Resolver();
    // 构建转换配置，extractedDir 设为 null（由转换器内部自动创建）
    ConversionConfig config = new ConversionConfig(resolver, null, keepTemp);

    // 执行三阶段转换管线：解压 → 解析 → 渲染
    ConversionResult result = DocxConverter.convert(inputFile, config);
    String html = result.html();

    // 根据是否指定输出文件，决定写入文件或打印到 stdout
    Files.writeString(outputFile, html);
  }
}

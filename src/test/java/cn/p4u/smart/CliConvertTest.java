package cn.p4u.smart;

import cn.p4u.smart.converter.ConversionConfig;
import cn.p4u.smart.converter.ConversionResult;
import cn.p4u.smart.converter.DocxConverter;
import cn.p4u.smart.util.Jdk8Helpers;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

class CliConvertTest {

  @Test
  void testConvert() throws IOException {
    String imageDir = "";
    boolean keepTemp = false;
    Path inputFile = Paths.get("C:\\DevCode\\agile-hub\\docxToHtml4j\\《电气工程专业实训教程》终审导入.docx");
    Path outputFile = Paths.get("C:\\DevCode\\agile-hub\\docxToHtml4j\\《电气工程专业实训教程》终审导入.html");
    // 解析图片嵌入模式：link 为外部链接模式，其余均默认 base64 内嵌模式
    ConversionConfig.ImageMode mode = ConversionConfig.ImageMode.BASE64;
    // 构建转换配置，extractedDir 设为 null（由转换器内部自动创建）
    ConversionConfig config = new ConversionConfig(mode, Paths.get(imageDir), null, keepTemp);

    // 执行三阶段转换管线：解压 → 解析 → 渲染
    ConversionResult result = DocxConverter.convert(inputFile, config);
    String html = result.html();

    // 根据是否指定输出文件，决定写入文件或打印到 stdout
    if (outputFile != null) {
      Jdk8Helpers.writeString(outputFile, html);
    } else {
      System.out.println(html);
    }
  }
}

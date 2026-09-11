package cn.p4u.dth;

import cn.p4u.dth.converter.ConversionConfig;
import cn.p4u.dth.converter.DocxConverter;
import cn.p4u.dth.renderer.Image2Base64Resolver;
import cn.p4u.dth.renderer.WmfConversionStrategy;

import java.io.FileInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Paths;

public class PublicDocxConvertTest {

  public static void main(String[] args) throws Exception {
    convertInputStreamToHtmlStrTest();
  }

  public static void convertInputStreamToHtmlStrTest() throws Exception {
    String tmpDir = "E:\\_tmp\\word\\";
    String docxPath = tmpDir + "word不同类别测试.docx";
    String htmlPath = tmpDir + "word不同类别测试.html";
    FileInputStream fileInputStream = new FileInputStream(docxPath);
    ConversionConfig conversionConfig =
        new ConversionConfig(
            new Image2Base64Resolver(),
            tmpDir,
            WmfConversionStrategy.IMAGEMAGICK,
            "C:\\DevRepo\\ImageMagick\\ImageMagick-7.1.2-Q16\\magick.exe");
    String htmlContent =
        DocxConverter.convert(
            fileInputStream,
            conversionConfig,
            (count, total, record) -> {
              System.out.println("进度:" + calcProgress(count, total));
              System.out.println("type:" + record.getType());
              System.out.println("data:" + record.getData().toString());
            });
    Files.write(Paths.get(htmlPath), htmlContent.getBytes());
  }

  /**
   * 计算处理进度
   *
   * @param current
   * @param total
   * @return
   */
  public static String calcProgress(int current, int total) {
    if (total == 0) {
      return "0.00%";
    }
    BigDecimal c = BigDecimal.valueOf(current);
    BigDecimal t = BigDecimal.valueOf(total);
    // 乘以100再除以总数，保留2位小数，四舍五入
    BigDecimal percent = c.multiply(BigDecimal.valueOf(100)).divide(t, 2, RoundingMode.HALF_UP);
    return percent + "%";
  }
}

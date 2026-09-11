package cn.p4u.dth;

import cn.p4u.dth.converter.DocxConverter;
import cn.p4u.dth.renderer.Image2Base64Resolver;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

public class PublicDocxConvertTest {

  @Test
  public void convertsInputStreamToHtmlStrTest() throws Exception {
    String tmpDir = "E:\\_tmp\\word\\";
    String docxPath = tmpDir + "公式手动设置样式和编辑器默认样式测.docx";
    String htmlPath = tmpDir + "公式手动设置样式和编辑器默认样式测.html";
    FileInputStream fileInputStream = new FileInputStream(docxPath);
    String htmlContent =
        DocxConverter.convert(
            fileInputStream,
            new Image2Base64Resolver(),
            tmpDir,
            (count, total, record) -> {
              System.out.println("count:" + count);
              System.out.println("total:" + total);
              System.out.println("record:" + record);
            });
    Files.write(Paths.get(htmlPath), htmlContent.getBytes());
  }
}

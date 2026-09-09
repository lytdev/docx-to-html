package cn.p4u.smart;

import cn.p4u.smart.converter.DocxConverter;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

import cn.p4u.smart.renderer.Image2Base64Resolver;
import org.junit.jupiter.api.Test;

public class PublicDocxConvertTest {

  @Test
  public void convertsInputStreamToHtmlStrTest() throws Exception {
    String tmpDir = "E:\\_tmp\\word\\";
    String docxPath = tmpDir + "项目8厚煤层开采技术.docx";
    String htmlPath = tmpDir + "项目8厚煤层开采技术.html";
    FileInputStream fileInputStream = new FileInputStream(docxPath);
    String htmlContent = DocxConverter.convert(fileInputStream, new Image2Base64Resolver(), tmpDir);
    Files.write(Paths.get(htmlPath), htmlContent.getBytes());
  }
}

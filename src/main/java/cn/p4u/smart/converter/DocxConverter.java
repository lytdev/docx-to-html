package cn.p4u.smart.converter;

import cn.p4u.smart.DocxConversionException;
import cn.p4u.smart.extractor.DocxExtractor;
import cn.p4u.smart.extractor.ExtractedDocx;
import cn.p4u.smart.model.DocumentModel;
import cn.p4u.smart.parser.DocumentParser;
import cn.p4u.smart.renderer.HtmlRenderer;
import cn.p4u.smart.renderer.ImageUriResolver;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Objects;

/**
 * docx 转 HTML 的高层入口类，串联完整的转换管线。
 *
 * <p>核心职责：按顺序执行三阶段管线（解压 → 解析 → 渲染）， 管理临时目录的生命周期，并直接返回转换后的 HTML 字符串。
 *
 * <p>主要使用场景：作为 Spring Boot 等服务端应用中的公开 API 入口。
 */
public final class DocxConverter {

  // 私有构造，纯静态工具类
  private DocxConverter() {}

  /**
   * 执行 .docx 到 HTML 的完整转换。
   *
   * <p>执行流程：
   *
   * <ol>
   *   <li>调用 DocxExtractor.extract() 从输入流解压 .docx 到临时目录
   *   <li>调用 DocumentParser.parse() 将 XML 解析为 DocumentModel
   *   <li>调用 HtmlRenderer.render() 将 DocumentModel 渲染为 HTML 字符串
   *   <li>在 finally 块中调用 DocxExtractor.cleanup() 清理临时目录
   * </ol>
   *
   * @param docxStream 输入的 .docx 数据流；本方法读取但不关闭该流
   * @param config 转换配置，包含图片资源处理器和公式渲染地址
   * @return 转换后的完整 HTML 字符串
   * @throws DocxConversionException 解压、解析或渲染失败时抛出
   */
  public static String convert(InputStream docxStream, ConversionConfig config) {
    return convertInternal(docxStream, config, config.tmpDir());
  }

  private static String convertInternal(
      InputStream docxStream, ConversionConfig config, String tempRoot) {
    Objects.requireNonNull(docxStream, "docxStream");
    Objects.requireNonNull(config, "config");

    // 阶段一：解压 .docx 到临时目录
    // ExtractedDocx 实现 AutoCloseable，退出 try 块时会自动删除本次解压目录。
    try (ExtractedDocx extracted = tempRoot == null
        ? DocxExtractor.open(docxStream)
        : DocxExtractor.open(docxStream, Path.of(tempRoot))) {
      Path extractedDir = extracted.rootDirectory();
      // 阶段二：解析 XML 文件构建文档模型
      DocumentModel model = DocumentParser.parse(extractedDir);
      // 阶段三：渲染文档模型为 HTML
      return HtmlRenderer.render(model, config, extractedDir);
    }
  }

  /**
   * 使用指定的图片资源处理器转换 DOCX。
   *
   * @param docxStream 输入的 .docx 数据流；本方法读取但不关闭该流
   * @param imageUriResolver 图片资源处理器，可编码为 Base64 或上传到对象存储
   * @return 转换后的完整 HTML 字符串
   */
  public static String convert(InputStream docxStream, ImageUriResolver imageUriResolver) {
    return convert(docxStream, new ConversionConfig(imageUriResolver));
  }

  /**
   * 使用指定的图片资源处理器转换 DOCX。
   *
   * @param docxStream 输入的 .docx 数据流；本方法读取但不关闭该流
   * @param imageUriResolver 图片资源处理器，可编码为 Base64 或上传到对象存储
   * @param tmpDir 临时目录
   * @return 转换后的完整 HTML 字符串
   */
  public static String convert(
      InputStream docxStream, ImageUriResolver imageUriResolver, String tmpDir) {
    return convert(docxStream, new ConversionConfig(imageUriResolver, tmpDir));
  }

  /**
   * 使用默认 Base64 图片资源处理器转换 DOCX。
   *
   * @param docxStream 输入的 .docx 数据流；本方法读取但不关闭该流
   * @return 转换后的完整 HTML 字符串
   */
  public static String convert(InputStream docxStream) {
    return convert(docxStream, ConversionConfig.defaults());
  }
}

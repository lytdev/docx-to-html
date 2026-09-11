package cn.p4u.dth.converter;

import cn.p4u.dth.DocxConversionException;
import cn.p4u.dth.extractor.DocxExtractor;
import cn.p4u.dth.extractor.ExtractedDocx;
import cn.p4u.dth.model.ContentBlock;
import cn.p4u.dth.model.DocumentModel;
import cn.p4u.dth.parser.DocumentParser;
import cn.p4u.dth.renderer.HtmlRenderer;
import cn.p4u.dth.renderer.ImageUriResolver;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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
    return convert(docxStream, config, null);
  }

  /**
   * 转换 DOCX，并实时报告正文内容的解析进度。
   *
   * @param docxStream 输入的 DOCX 数据流；本方法读取但不关闭该流
   * @param config 转换配置
   * @param callback 进度回调；传入 null 表示不监听进度
   * @return 转换后的完整 HTML 字符串
   */
  public static String convert(
      InputStream docxStream,
      ConversionConfig config,
      FileParseCallback<ContentBlock> callback) {
    Objects.requireNonNull(config, "config");
    return convertInternal(docxStream, config, config.tmpDir(), callback);
  }

  private static String convertInternal(
      InputStream docxStream, ConversionConfig config, String tempRoot,
      FileParseCallback<ContentBlock> callback) {
    Objects.requireNonNull(docxStream, "docxStream");
    Objects.requireNonNull(config, "config");

    // 阶段一：解压 .docx 到临时目录
    // ExtractedDocx 实现 AutoCloseable，退出 try 块时会自动删除本次解压目录。
    AtomicInteger parsedCount = new AtomicInteger();
    AtomicBoolean errorReported = new AtomicBoolean();
    FileParseCallback<ContentBlock> trackingCallback = callback == null ? null
        : new FileParseCallback<ContentBlock>() {
          @Override
          public void onLineParsed(int count, int total, CallBackRecord<ContentBlock> record) {
            parsedCount.set(count);
            callback.onLineParsed(count, total, record);
          }

          @Override
          public void onError(Exception ex, int line) {
            errorReported.set(true);
            callback.onError(ex, line);
          }
        };
    boolean parsingStarted = false;
    boolean parsingCompleted = false;
    String html;
    int total;
    try (ExtractedDocx extracted = tempRoot == null
        ? DocxExtractor.open(docxStream)
        : DocxExtractor.open(docxStream, Path.of(tempRoot))) {
      Path extractedDir = extracted.rootDirectory();
      // 阶段二：解析 XML 文件构建文档模型
      parsingStarted = true;
      DocumentModel model = DocumentParser.parse(extractedDir, trackingCallback);
      parsingCompleted = true;
      total = model.content().size();
      // 阶段三：渲染文档模型为 HTML
      html = HtmlRenderer.render(model, config, extractedDir);
    } catch (RuntimeException ex) {
      if (callback != null && !errorReported.get()) {
        int line = !parsingStarted || parsedCount.get() == 0 ? 0
            : parsingCompleted ? parsedCount.get() : parsedCount.get() + 1;
        try {
          callback.onError(ex, line);
        } catch (RuntimeException callbackError) {
          ex.addSuppressed(callbackError);
        }
      }
      throw ex;
    }
    if (callback != null) callback.onComplete(total, "文件处理完成");
    return html;
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
   * 使用指定图片处理器转换，并报告解析进度。
   *
   * @param docxStream 输入的 DOCX 数据流；本方法读取但不关闭该流
   * @param imageUriResolver 图片资源地址解析器
   * @param callback 进度回调；传入 {@code null} 表示不监听进度
   * @return 转换后的完整 HTML 字符串
   */
  public static String convert(
      InputStream docxStream,
      ImageUriResolver imageUriResolver,
      FileParseCallback<ContentBlock> callback) {
    return convert(docxStream, new ConversionConfig(imageUriResolver), callback);
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
   * 使用指定图片处理器和临时目录转换，并报告解析进度。
   *
   * @param docxStream 输入的 DOCX 数据流；本方法读取但不关闭该流
   * @param imageUriResolver 图片资源地址解析器
   * @param tmpDir 临时目录根路径；为 {@code null} 时使用系统临时目录
   * @param callback 进度回调；传入 {@code null} 表示不监听进度
   * @return 转换后的完整 HTML 字符串
   */
  public static String convert(
      InputStream docxStream,
      ImageUriResolver imageUriResolver,
      String tmpDir,
      FileParseCallback<ContentBlock> callback) {
    return convert(docxStream, new ConversionConfig(imageUriResolver, tmpDir), callback);
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

  /**
   * 使用默认配置转换，并报告解析进度。
   *
   * @param docxStream 输入的 DOCX 数据流；本方法读取但不关闭该流
   * @param callback 进度回调；传入 {@code null} 表示不监听进度
   * @return 转换后的完整 HTML 字符串
   */
  public static String convert(
      InputStream docxStream,
      FileParseCallback<ContentBlock> callback) {
    return convert(docxStream, ConversionConfig.defaults(), callback);
  }
}

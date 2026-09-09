package cn.p4u.smart.parser;

import java.io.StringReader;
import java.nio.file.Path;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * 创建安全 DOM 解析器的统一工厂。
 *
 * <p>以前每个解析器都各自配置 DocumentBuilderFactory，容易遗漏某个安全选项。
 * 现在所有 OOXML 文件都从这里创建解析器：禁用 DOCTYPE、外部实体和外部 Schema，
 * 同时开启命名空间支持。这是工厂模式的一个简单应用。</p>
 */
final class SecureXmlDocuments {
  private static final String DISALLOW_DOCTYPE =
      "http://apache.org/xml/features/disallow-doctype-decl";

  private SecureXmlDocuments() {}

  /** 安全地解析一个 XML 文件。调用方负责决定解析失败时是抛异常还是降级。 */
  static Document parse(Path xmlFile) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    factory.setXIncludeAware(false);
    factory.setExpandEntityReferences(false);
    factory.setFeature(DISALLOW_DOCTYPE, true);
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

    DocumentBuilder builder = factory.newDocumentBuilder();
    // 即使底层解析器忽略某个开关，空解析器也会阻止外部实体读取本机或网络资源。
    builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
    // 默认错误处理器会把预期的格式错误直接打印到控制台；这里改为交给调用方统一处理。
    builder.setErrorHandler(new DefaultHandler() {
      @Override
      public void error(SAXParseException exception) throws SAXParseException {
        throw exception;
      }

      @Override
      public void fatalError(SAXParseException exception) throws SAXParseException {
        throw exception;
      }
    });
    return builder.parse(xmlFile.toFile());
  }
}

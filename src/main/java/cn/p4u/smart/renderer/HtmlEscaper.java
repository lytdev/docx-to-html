package cn.p4u.smart.renderer;

/** 集中处理 HTML 转义，避免不同渲染器使用不一致的规则。 */
final class HtmlEscaper {
  private HtmlEscaper() {}

  /** 转义普通文本节点。 */
  static String text(String value) {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }

  /** 转义由双引号包裹的 HTML 属性值。 */
  static String attribute(String value) {
    return value.replace("&", "&amp;").replace("\"", "&quot;");
  }
}

package cn.p4u.smart.model;

/**
 * 段落内联元素的标记接口（marker interface）。
 * <p>
 * 核心职责：为段落中的各种内联元素（文本、图片、公式、超链接、形状等）
 * 提供统一的类型标识，使 ParagraphBlock.elements() 列表可以容纳不同类型的内联元素。
 * <p>
 * 主要使用场景：DocumentParser 解析段落内容时，将文本运行、图片、公式、超链接、形状等
 * 统一作为 ParagraphElement 存入段落元素列表；HtmlRenderer 遍历时根据 instanceof 分派渲染。
 * <p>
 * 已知实现：TextRun、ImageElement、MathElement、HyperlinkElement、ShapeElement。
 */
public interface ParagraphElement {
}

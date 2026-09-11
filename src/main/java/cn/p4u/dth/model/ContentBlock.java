package cn.p4u.dth.model;

/**
 * 文档内容块的标记接口（marker interface）。
 * <p>
 * 核心职责：为文档中的顶层内容块（如段落、表格）提供统一的类型标识，
 * 使 DocumentModel.content() 列表可以容纳不同类型的块级元素。
 * <p>
 * 主要使用场景：DocumentParser 解析文档后，将段落和表格统一作为 ContentBlock
 * 存入 DocumentModel 的内容列表；HtmlRenderer 遍历时根据 instanceof 分派渲染。
 * <p>
 * 已知实现：ParagraphBlock（段落）、TableBlock（表格）。
 */
public interface ContentBlock {
}

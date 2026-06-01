package cn.p4u.smart.model;

/**
 * 图片和形状的文字环绕模式枚举。
 * <p>
 * 核心职责：定义文档中图片/形状与周围文字的排版关系，
 * 对应 OOXML 中 wp:anchor 下的各种 wrap 子元素。
 * <p>
 * 主要使用场景：
 * <ul>
 *   <li>DocumentParser 解析 w:drawing 中 wp:inline/wp:anchor 的 wrap 类型</li>
 *   <li>HtmlRenderer 根据环绕模式生成对应的 CSS float/display 样式</li>
 * </ul>
 */
public enum WrapMode {
    /** 行内嵌入，不浮动，对应 wp:inline 或 wrapNone */
    INLINE,
    /** 文字环绕在左侧，对应 wrapSquare wrapText="right" 或 wrapTight */
    LEFT,
    /** 文字环绕在右侧，对应 wrapSquare wrapText="left" */
    RIGHT,
    /** 上下环绕，独占一行居中，对应 wrapTopAndBottom */
    TOP_AND_BOTTOM
}

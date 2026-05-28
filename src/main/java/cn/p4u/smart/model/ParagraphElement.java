package cn.p4u.smart.model;

public sealed interface ParagraphElement permits TextRun, ImageElement, MathElement, HyperlinkElement {
}

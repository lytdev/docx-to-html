package cn.p4u.smart.model;

public sealed interface ContentBlock permits ParagraphBlock, TableBlock {
}

package cn.p4u.smart.model;

import java.util.List;

public record HyperlinkElement(String url, List<TextRun> runs) implements ParagraphElement {
}

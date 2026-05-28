package cn.p4u.smart.model;

import java.util.List;

public record ParagraphBlock(String styleId, String alignment, Indentation indentation,
                             List<ParagraphElement> elements) implements ContentBlock {
}

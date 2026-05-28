package cn.p4u.smart.model;

import java.util.List;

public record TableCell(List<ParagraphBlock> paragraphs,
                        int colspan, int rowspan,
                        String width, String borderWidth, String borderColor,
                        String bgColor, boolean visibility) {
}

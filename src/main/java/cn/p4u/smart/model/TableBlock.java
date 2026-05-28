package cn.p4u.smart.model;

import java.util.List;

public record TableBlock(List<TableRow> rows,
                         String width, String borderWidth, String borderColor,
                         boolean visibility) implements ContentBlock {
}

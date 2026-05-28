package cn.p4u.smart.model;

public record ImageElement(String mediaPath, String mimeType,
                           int width, int height,
                           WrapMode wrapMode) implements ParagraphElement {
}

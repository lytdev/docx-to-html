package cn.p4u.smart.model;

public record TextRun(String text, FontSpec font,
                      boolean bold, boolean italic, boolean underline, boolean strike,
                      String highlight, String shading,
                      boolean superscript, boolean subscript,
                      String styleId) implements ParagraphElement {
}

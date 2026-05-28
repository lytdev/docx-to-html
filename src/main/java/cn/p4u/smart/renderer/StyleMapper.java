package cn.p4u.smart.renderer;

import cn.p4u.smart.model.*;
import java.util.*;

public final class StyleMapper {

    private static final Map<String, String> HIGHLIGHT_COLORS;
    static {
        var m = new HashMap<String, String>();
        m.put("yellow", "#FFFF00");
        m.put("green", "#00FF00");
        m.put("cyan", "#00FFFF");
        m.put("magenta", "#FF00FF");
        m.put("blue", "#0000FF");
        m.put("red", "#FF0000");
        m.put("darkBlue", "#00008B");
        m.put("darkCyan", "#008B8B");
        m.put("darkGreen", "#006400");
        m.put("darkMagenta", "#8B008B");
        m.put("darkRed", "#8B0000");
        m.put("darkYellow", "#808000");
        m.put("darkGray", "#808080");
        m.put("lightGray", "#C0C0C0");
        m.put("black", "#000000");
        HIGHLIGHT_COLORS = Collections.unmodifiableMap(m);
    }

    private StyleMapper() {}

    public static String runStyle(TextRun run) {
        var parts = new ArrayList<String>();
        var font = run.font();

        if (font != null) {
            if (font.name() != null && !font.name().isEmpty()) {
                parts.add("font-family: " + font.name());
            }
            if (font.size() != null && !font.size().isEmpty()) {
                String pt = halfPointsToPt(font.size());
                if (pt != null) parts.add("font-size: " + pt);
            }
            if (font.color() != null && !font.color().isEmpty()) {
                parts.add("color: " + (font.color().startsWith("#") ? "" : "#") + font.color());
            }
        }
        if (run.bold()) parts.add("font-weight: bold");
        if (run.italic()) parts.add("font-style: italic");

        var decorations = new ArrayList<String>();
        if (run.underline()) decorations.add("underline");
        if (run.strike()) decorations.add("line-through");
        if (!decorations.isEmpty()) parts.add("text-decoration: " + String.join(" ", decorations));

        if (run.highlight() != null && !run.highlight().isEmpty()) {
            String color = HIGHLIGHT_COLORS.getOrDefault(run.highlight(), "#" + run.highlight());
            parts.add("background-color: " + color);
        } else if (run.shading() != null && !run.shading().isEmpty()
                && !"auto".equalsIgnoreCase(run.shading())) {
            parts.add("background-color: " + (run.shading().startsWith("#") ? "" : "#") + run.shading());
        }

        if (run.superscript()) {
            parts.add("vertical-align: super");
            parts.add("font-size: smaller");
        } else if (run.subscript()) {
            parts.add("vertical-align: sub");
            parts.add("font-size: smaller");
        }

        return String.join("; ", parts);
    }

    public static String paragraphStyle(ParagraphBlock para) {
        var parts = new ArrayList<String>();
        if (para.alignment() != null && !para.alignment().isEmpty()) {
            parts.add("text-align: " + para.alignment());
        }
        if (para.indentation() != null) {
            var ind = para.indentation();
            appendTwips(parts, "margin-left", ind.left());
            appendTwips(parts, "margin-right", ind.right());
            appendTwips(parts, "text-indent", ind.firstLine());
        }
        return String.join("; ", parts);
    }

    public static String tableStyle(TableBlock table) {
        var parts = new ArrayList<String>();
        if (table.width() != null) parts.add("width: " + table.width());
        if (table.borderWidth() != null) {
            String bc = table.borderColor();
            if (bc == null || "auto".equalsIgnoreCase(bc)) bc = "000";
            parts.add("border: " + eighthsToPt(table.borderWidth()) + " solid #" + bc);
        }
        parts.add("border-collapse: collapse");
        parts.add("visibility: " + (table.visibility() ? "visible" : "hidden"));
        return String.join("; ", parts);
    }

    public static String cellStyle(TableCell cell) {
        var parts = new ArrayList<String>();
        if (cell.width() != null) parts.add("width: " + cell.width());
        if (cell.borderWidth() != null) {
            String bc = cell.borderColor();
            if (bc == null || "auto".equalsIgnoreCase(bc)) bc = "000";
            parts.add("border: " + eighthsToPt(cell.borderWidth()) + " solid #" + bc);
        } else {
            parts.add("border: 1px solid #000");
        }
        if (cell.bgColor() != null && !"auto".equalsIgnoreCase(cell.bgColor())) {
            parts.add("background-color: " + (cell.bgColor().startsWith("#") ? "" : "#") + cell.bgColor());
        }
        parts.add("visibility: " + (cell.visibility() ? "visible" : "hidden"));
        return String.join("; ", parts);
    }

    private static String halfPointsToPt(String halfPoints) {
        try {
            int hp = Integer.parseInt(halfPoints);
            return (hp / 2) + "pt";
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String eighthsToPt(String eighths) {
        try {
            double val = Integer.parseInt(eighths) / 8.0;
            return val + "pt";
        } catch (NumberFormatException e) {
            return "1pt";
        }
    }

    private static void appendTwips(List<String> parts, String prop, String twips) {
        if (twips != null && !twips.isEmpty()) {
            try {
                int tw = Integer.parseInt(twips);
                parts.add(prop + ": " + (tw / 20.0) + "pt");
            } catch (NumberFormatException ignored) {}
        }
    }
}

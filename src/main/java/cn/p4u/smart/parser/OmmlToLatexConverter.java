package cn.p4u.smart.parser;

import org.w3c.dom.*;
import java.util.logging.Logger;

public final class OmmlToLatexConverter {

    private static final Logger LOG = Logger.getLogger(OmmlToLatexConverter.class.getName());
    private static final String M = "http://schemas.openxmlformats.org/officeDocument/2006/math";

    private OmmlToLatexConverter() {}

    public static String convert(Element mathEl) {
        if (mathEl == null) return "";
        try {
            return convertNode(mathEl).toString();
        } catch (Exception e) {
            LOG.warning("OMML→LaTeX conversion failed: " + e.getMessage());
            return "";
        }
    }

    private static StringBuilder convertNode(Node node) {
        if (!(node instanceof Element el)) return new StringBuilder();

        return switch (el.getLocalName()) {
            case "oMath", "oMathPara" -> convertChildren(el);
            case "r" -> convertRun(el);
            case "f" -> convertFraction(el);
            case "sSup" -> convertSuperscript(el);
            case "sSub" -> convertSubscript(el);
            case "sSubSup" -> convertSubSuperscript(el);
            case "rad" -> convertRadical(el);
            case "d" -> convertDelimiter(el);
            case "nary" -> convertNary(el);
            case "acc" -> convertAccent(el);
            case "bar" -> convertBar(el);
            case "eqArr" -> convertEqArray(el);
            case "m" -> convertMatrix(el);
            default -> convertChildren(el);
        };
    }

    private static StringBuilder convertChildren(Element parent) {
        var sb = new StringBuilder();
        var children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element el && M.equals(el.getNamespaceURI())) {
                sb.append(convertNode(el));
            }
        }
        return sb;
    }

    private static StringBuilder convertRun(Element rEl) {
        var children = rEl.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element el && "t".equals(el.getLocalName())) {
                return new StringBuilder(latexEscape(el.getTextContent()));
            }
        }
        return new StringBuilder();
    }

    private static StringBuilder convertFraction(Element fEl) {
        var num = findChild(fEl, "num");
        var den = findChild(fEl, "den");
        return new StringBuilder("\\frac{")
                .append(num != null ? convertChildren(num) : "")
                .append("}{")
                .append(den != null ? convertChildren(den) : "")
                .append("}");
    }

    private static StringBuilder convertSuperscript(Element el) {
        var base = findChild(el, "e");
        var sup = findChild(el, "sup");
        return new StringBuilder()
                .append(base != null ? convertChildren(base) : "")
                .append("^{")
                .append(sup != null ? convertChildren(sup) : "")
                .append("}");
    }

    private static StringBuilder convertSubscript(Element el) {
        var base = findChild(el, "e");
        var sub = findChild(el, "sub");
        return new StringBuilder()
                .append(base != null ? convertChildren(base) : "")
                .append("_{")
                .append(sub != null ? convertChildren(sub) : "")
                .append("}");
    }

    private static StringBuilder convertSubSuperscript(Element el) {
        var base = findChild(el, "e");
        var sub = findChild(el, "sub");
        var sup = findChild(el, "sup");
        return new StringBuilder()
                .append(base != null ? convertChildren(base) : "")
                .append("_{")
                .append(sub != null ? convertChildren(sub) : "")
                .append("}^{")
                .append(sup != null ? convertChildren(sup) : "")
                .append("}");
    }

    private static StringBuilder convertRadical(Element el) {
        var deg = findChild(el, "deg");
        var e = findChild(el, "e");
        if (deg == null) {
            return new StringBuilder("\\sqrt{")
                    .append(e != null ? convertChildren(e) : "")
                    .append("}");
        }
        return new StringBuilder("\\sqrt[")
                .append(convertChildren(deg))
                .append("]{")
                .append(e != null ? convertChildren(e) : "")
                .append("}");
    }

    private static StringBuilder convertDelimiter(Element el) {
        var dPr = findChild(el, "dPr");
        String open = "(";
        String close = ")";
        if (dPr != null) {
            var begChr = findChild(dPr, "begChr");
            var endChr = findChild(dPr, "endChr");
            if (begChr != null) open = getAttrVal(begChr);
            if (endChr != null) close = getAttrVal(endChr);
        }
        var sb = new StringBuilder(latexDelimiter(open));
        var children = el.getChildNodes();
        boolean first = true;
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element ce && "e".equals(ce.getLocalName())) {
                if (!first) sb.append(" & ");
                sb.append(convertChildren(ce));
                first = false;
            }
        }
        sb.append(latexDelimiter(close));
        return sb;
    }

    private static StringBuilder convertNary(Element el) {
        var naryPr = findChild(el, "naryPr");
        String operator = "\\sum";
        if (naryPr != null) {
            var chrEl = findChild(naryPr, "chr");
            if (chrEl != null) {
                String chr = getAttrVal(chrEl);
                operator = switch (chr) {
                    case "∏" -> "\\prod";
                    case "∫" -> "\\int";
                    case "⋃" -> "\\bigcup";
                    case "⋂" -> "\\bigcap";
                    default -> operator;
                };
            }
        }
        var sub = findChild(el, "sub");
        var sup = findChild(el, "sup");
        var e = findChild(el, "e");
        var sb = new StringBuilder(operator);
        if (sub != null) sb.append("_{").append(convertChildren(sub)).append("}");
        if (sup != null) sb.append("^{").append(convertChildren(sup)).append("}");
        sb.append(" ").append(e != null ? convertChildren(e) : "");
        return sb;
    }

    private static StringBuilder convertAccent(Element el) {
        var accPr = findChild(el, "accPr");
        String accent = "\\hat";
        if (accPr != null) {
            var chrEl = findChild(accPr, "chr");
            if (chrEl != null) {
                String chr = getAttrVal(chrEl);
                accent = switch (chr) {
                    case "̃" -> "\\tilde";
                    case "⃗" -> "\\vec";
                    case "̅" -> "\\overline";
                    case "̇" -> "\\dot";
                    case "̈" -> "\\ddot";
                    default -> accent;
                };
            }
        }
        var e = findChild(el, "e");
        return new StringBuilder(accent).append("{")
                .append(e != null ? convertChildren(e) : "")
                .append("}");
    }

    private static StringBuilder convertBar(Element el) {
        var barPr = findChild(el, "barPr");
        boolean isUnder = false;
        if (barPr != null) {
            var pos = findChild(barPr, "pos");
            if (pos != null) {
                String val = getAttrVal(pos);
                if ("bot".equals(val)) isUnder = true;
            }
        }
        var e = findChild(el, "e");
        String cmd = isUnder ? "\\underbrace" : "\\overline";
        return new StringBuilder(cmd).append("{")
                .append(e != null ? convertChildren(e) : "")
                .append("}");
    }

    private static StringBuilder convertEqArray(Element el) {
        var sb = new StringBuilder("\\begin{aligned}\n");
        var children = el.getChildNodes();
        boolean first = true;
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element ce && "e".equals(ce.getLocalName())) {
                if (!first) sb.append(" \\\\\\\\\n");
                sb.append(convertChildren(ce));
                first = false;
            }
        }
        sb.append("\n\\end{aligned}");
        return sb;
    }

    private static StringBuilder convertMatrix(Element el) {
        var sb = new StringBuilder("\\begin{matrix}\n");
        var rows = el.getElementsByTagNameNS(M, "mr");
        for (int i = 0; i < rows.getLength(); i++) {
            if (i > 0) sb.append(" \\\\\\\\\n");
            var row = (Element) rows.item(i);
            var cells = row.getElementsByTagNameNS(M, "e");
            for (int j = 0; j < cells.getLength(); j++) {
                if (j > 0) sb.append(" & ");
                sb.append(convertChildren((Element) cells.item(j)));
            }
        }
        sb.append("\n\\end{matrix}");
        return sb;
    }

    // --- Helpers ---

    private static Element findChild(Element parent, String localName) {
        var nodes = parent.getElementsByTagNameNS(M, localName);
        return nodes.getLength() > 0 ? (Element) nodes.item(0) : null;
    }

    private static String getAttrVal(Element chrEl) {
        String val = chrEl.getAttributeNS(M, "val");
        return val.isEmpty() ? chrEl.getAttribute("m:val") : val;
    }

    private static String latexEscape(String text) {
        return text
                .replace("\\", "\\\\")
                .replace("{", "\\{")
                .replace("}", "\\}")
                .replace("_", "\\_")
                .replace("^", "\\^")
                .replace("&", "\\&")
                .replace("#", "\\#")
                .replace("$", "\\$")
                .replace("%", "\\%")
                .replace("~", "\\textasciitilde{}");
    }

    private static String latexDelimiter(String chr) {
        return switch (chr) {
            case "(" -> "\\left(";
            case ")" -> "\\right)";
            case "[" -> "\\left[";
            case "]" -> "\\right]";
            case "{" -> "\\left\\{";
            case "}" -> "\\right\\}";
            case "‖" -> "\\left\\|";
            case "|" -> "\\left|";
            case "⟨" -> "\\left\\langle";
            case "⟩" -> "\\right\\rangle";
            case "" -> "\\left.";
            default -> "\\left" + chr;
        };
    }
}

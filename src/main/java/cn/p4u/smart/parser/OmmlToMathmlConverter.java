package cn.p4u.smart.parser;

import org.w3c.dom.*;

/**
 * OMML (Office Math Markup Language) �� MathML ��ת������
 * <p>
 * �� OOXML �ĵ��е� m:oMath ��ʽԪ��ת��Ϊ MathML ��ʽ��
 * MathML �ɱ��ִ������ԭ����Ⱦ��Chrome/Firefox/Safari���������ⲿ������
 */
public final class OmmlToMathmlConverter {

    private static final String M = "http://schemas.openxmlformats.org/officeDocument/2006/math";
    private static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";

    private OmmlToMathmlConverter() {}

    /**
     * �� OMML ��ʽԪ��ת��Ϊ MathML XML �ַ�����
     * @param mathEl m:oMath �� m:oMathPara Ԫ��
     * @return MathML XML �ַ�����������Ϊ null �򷵻ؿ��ַ���
     */
    public static String convert(Element mathEl) {
        if (mathEl == null) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("<math xmlns=\"http://www.w3.org/1998/Math/MathML\"");
        // 提取公式字体大小：优先 w:szCs（复杂脚本字号），其次 w:sz
        String fontSize = extractFontSize(mathEl);
        if (fontSize != null) {
            sb.append(" style=\"font-size: ").append(fontSize).append("pt\"");
        }
        sb.append(">");
        convertNode(mathEl, sb);
        sb.append("</math>");
        return sb.toString();
    }

    private static void convertNode(Node node, StringBuilder sb) {
        if (!(node instanceof Element)) return;
        Element el = (Element) node;
        if (!M.equals(el.getNamespaceURI())) {
            NodeList children = el.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) convertNode(children.item(i), sb);
            return;
        }
        String n = el.getLocalName();
        if ("oMath".equals(n) || "oMathPara".equals(n)) { convertChildren(el, sb); return; }
        if ("r".equals(n)) {
            NodeList tNodes = el.getElementsByTagNameNS(M, "t");
            if (tNodes.getLength() > 0) {
                String text = escapeXml(tNodes.item(0).getTextContent());
                // 读取 m:rPr 格式属性
                boolean isNor = hasMathRunProp(el, "nor");     // <m:nor/> → 普通文字格式
                String styVal = getMathStyVal(el);              // <m:sty val="p"/"i"/"b"/"bi"/"bs"/>
                boolean styItalic = "i".equals(styVal) || "bi".equals(styVal);
                boolean styBold = "b".equals(styVal) || "bi".equals(styVal) || "bs".equals(styVal);
                // 读取 w:rPr 格式属性（从 m:r 自身和父容器 m:ctrlPr 继承）
                boolean wItalic = hasWrPrBoldOrItalicOrCtrlPr(el, "i");  // <w:i/>
                boolean wBold = hasWrPrBoldOrItalicOrCtrlPr(el, "b");    // <w:b/>
                // 读取 w:rPr/w:rFonts 字体信息（含 m:ctrlPr 继承）
                String fontFamily = getWrPrFontFamilyOrCtrlPr(el);

                // 确定最终斜体和粗体
                boolean effectiveItalic;
                boolean effectiveBold;
                if (isNor) {
                    // m:nor → 用文字格式，但 m:sty=i/bi 仍可覆盖为斜体，m:sty=b/bi/bs 覆盖为粗体
                    effectiveItalic = wItalic || styItalic;
                    effectiveBold = wBold || styBold;
                } else if (styItalic || styBold) {
                    // m:sty 明确指定了斜体或粗体（无 m:nor 时）
                    effectiveItalic = styItalic;
                    effectiveBold = styBold;
                } else {
                    // 无 m:nor 且无 m:sty 覆盖 → 默认数学斜体，粗体看 w:b
                    effectiveItalic = true;
                    effectiveBold = wBold;
                }

                // 组装 MathML <mi> 及其属性
                // MathML 规范：<mi> 单字符默认斜体，多字符默认直立。
                // 多字符斜体需拆分为逐个单字符 <mi> 确保跨浏览器正确渲染。
                String miAttr;
                String miStyle;
                if (effectiveBold && effectiveItalic) {
                    miAttr = " mathvariant=\"bold-italic\"";
                    miStyle = null;
                } else if (effectiveBold && !effectiveItalic) {
                    miAttr = " mathvariant=\"normal\"";
                    miStyle = "font-weight:bold";
                } else if (effectiveItalic) {
                    miAttr = "";
                    miStyle = null;
                } else {
                    miAttr = " mathvariant=\"normal\"";
                    miStyle = null;
                }
                // font-family
                StringBuilder stylePart = new StringBuilder();
                if (miStyle != null) stylePart.append(miStyle);
                if (fontFamily != null) {
                    if (stylePart.length() > 0) stylePart.append(";");
                    stylePart.append("font-family:").append(fontFamily);
                }
                String fullStyle = stylePart.length() > 0 ? " style=\"" + stylePart + "\"" : "";

                // 斜体多字符：拆分为逐个单字符 <mi>（MathML 单字符 <mi> 默认斜体）
                if (effectiveItalic && text.length() > 1) {
                    for (int k = 0; k < text.length(); k++) {
                        char ch = text.charAt(k);
                        sb.append("<mi").append(miAttr).append(fullStyle).append(">")
                           .append(ch == ' ' ? " " : String.valueOf(ch))
                           .append("</mi>");
                    }
                } else {
                    sb.append("<mi").append(miAttr).append(fullStyle).append(">")
                       .append(text).append("</mi>");
                }
            }
            return;
        }
        if ("f".equals(n)) {
            sb.append("<mfrac><mrow>");
            Element num = findChild(el, "num"); if (num != null) convertChildren(num, sb);
            sb.append("</mrow><mrow>");
            Element den = findChild(el, "den"); if (den != null) convertChildren(den, sb);
            sb.append("</mrow></mfrac>");
            return;
        }
        if ("sSup".equals(n)) {
            sb.append("<msup>");
            if (findChild(el, "e") != null) { sb.append("<mrow>"); convertChildren(findChild(el, "e"), sb); sb.append("</mrow>"); }
            if (findChild(el, "sup") != null) { sb.append("<mrow>"); convertChildren(findChild(el, "sup"), sb); sb.append("</mrow>"); }
            sb.append("</msup>");
            return;
        }
        if ("sSub".equals(n)) {
            sb.append("<msub>");
            if (findChild(el, "e") != null) { sb.append("<mrow>"); convertChildren(findChild(el, "e"), sb); sb.append("</mrow>"); }
            if (findChild(el, "sub") != null) { sb.append("<mrow>"); convertChildren(findChild(el, "sub"), sb); sb.append("</mrow>"); }
            sb.append("</msub>");
            return;
        }
        if ("sSubSup".equals(n)) {
            sb.append("<msubsup>");
            if (findChild(el, "e") != null) { sb.append("<mrow>"); convertChildren(findChild(el, "e"), sb); sb.append("</mrow>"); }
            if (findChild(el, "sub") != null) { sb.append("<mrow>"); convertChildren(findChild(el, "sub"), sb); sb.append("</mrow>"); }
            if (findChild(el, "sup") != null) { sb.append("<mrow>"); convertChildren(findChild(el, "sup"), sb); sb.append("</mrow>"); }
            sb.append("</msubsup>");
            return;
        }
        if ("rad".equals(n)) {
            Element deg = findChild(el, "deg");
            if (deg != null) {
                sb.append("<mroot><mrow>"); if (findChild(el, "e") != null) convertChildren(findChild(el, "e"), sb);
                sb.append("</mrow><mrow>"); convertChildren(deg, sb); sb.append("</mrow></mroot>");
            } else {
                sb.append("<msqrt><mrow>"); if (findChild(el, "e") != null) convertChildren(findChild(el, "e"), sb);
                sb.append("</mrow></msqrt>");
            }
            return;
        }
        // n-ary 运算符（∑、∫、∏ 等）
        if ("nary".equals(n)) {
            sb.append("<mrow>");
            Element naryPr = findChild(el, "naryPr");
            String chr = "∑";  // 默认求和符号
            if (naryPr != null) {
                Element chrEl = findChild(naryPr, "chr");
                if (chrEl != null) {
                    String c = chrEl.getAttributeNS(M, "val");
                    if (c != null && !c.isEmpty()) chr = c;
                }
            }
            Element sub = findChild(el, "sub");
            Element sup = findChild(el, "sup");
            Element e = findChild(el, "e");
            String opTag;
            if (sub != null && sup != null) {
                opTag = "munderover";
            } else if (sub != null) {
                opTag = "munder";
            } else if (sup != null) {
                opTag = "mover";
            } else {
                opTag = null;
            }
            if (opTag != null) {
                sb.append("<").append(opTag).append("><mo>").append(escapeXml(chr)).append("</mo>");
                if (sub != null) { sb.append("<mrow>"); convertChildren(sub, sb); sb.append("</mrow>"); }
                if (sup != null) { sb.append("<mrow>"); convertChildren(sup, sb); sb.append("</mrow>"); }
                sb.append("</").append(opTag).append(">");
                if (e != null) convertChildren(e, sb);
            } else {
                sb.append("<mo>").append(escapeXml(chr)).append("</mo>");
                if (e != null) convertChildren(e, sb);
            }
            sb.append("</mrow>");
            return;
        }
        // 定界符（括号等）
        if ("d".equals(n)) {
            sb.append("<mrow>");
            Element dPr = findChild(el, "dPr");
            String open = "(";
            String close = ")";
            if (dPr != null) {
                Element begChr = findChild(dPr, "begChr");
                Element endChr = findChild(dPr, "endChr");
                if (begChr != null) {
                    String v = begChr.getAttributeNS(M, "val");
                    if (v != null && !v.isEmpty()) open = v;
                }
                if (endChr != null) {
                    String v = endChr.getAttributeNS(M, "val");
                    if (v != null && !v.isEmpty()) close = v;
                }
            }
            sb.append("<mo>").append(escapeXml(open)).append("</mo>");
            if (findChild(el, "e") != null) convertChildren(findChild(el, "e"), sb);
            sb.append("<mo>").append(escapeXml(close)).append("</mo>");
            sb.append("</mrow>");
            return;
        }
        // 重音符号（帽子、波浪号等）
        if ("acc".equals(n)) {
            sb.append("<mover>");
            if (findChild(el, "e") != null) { sb.append("<mrow>"); convertChildren(findChild(el, "e"), sb); sb.append("</mrow>"); }
            Element accPr = findChild(el, "accPr");
            String accent = "̂";  // hat
            if (accPr != null) {
                Element chrEl = findChild(accPr, "chr");
                if (chrEl != null) {
                    String c = chrEl.getAttributeNS(M, "val");
                    if (c != null && !c.isEmpty()) accent = c;
                }
            }
            sb.append("<mo>").append(escapeXml(accent)).append("</mo>");
            sb.append("</mover>");
            return;
        }
        // 横线（上划线等）
        if ("bar".equals(n)) {
            sb.append("<mover>");
            if (findChild(el, "e") != null) { sb.append("<mrow>"); convertChildren(findChild(el, "e"), sb); sb.append("</mrow>"); }
            sb.append("<mo>¯</mo>");
            sb.append("</mover>");
            return;
        }
        // 等式数组
        if ("eqArr".equals(n)) {
            sb.append("<mtable>");
            NodeList eNodes = el.getElementsByTagNameNS(M, "e");
            for (int i = 0; i < eNodes.getLength(); i++) {
                sb.append("<mtr><mtd><mrow>");
                convertChildren((Element) eNodes.item(i), sb);
                sb.append("</mrow></mtd></mtr>");
            }
            sb.append("</mtable>");
            return;
        }
        // 极限（lim sup/lim inf/lim）
        if ("lim".equals(n) || "limLow".equals(n)) {
            sb.append("<munder>");
            sb.append("<mrow><mo>lim</mo>");
            if (findChild(el, "e") != null) convertChildren(findChild(el, "e"), sb);
            sb.append("</mrow>");
            if (findChild(el, "lim") != null) { sb.append("<mrow>"); convertChildren(findChild(el, "lim"), sb); sb.append("</mrow>"); }
            if (findChild(el, "sub") != null) { sb.append("<mrow>"); convertChildren(findChild(el, "sub"), sb); sb.append("</mrow>"); }
            sb.append("</munder>");
            return;
        }
        // 默认递归
        convertChildren(el, sb);
    }

    private static void convertChildren(Element parent, StringBuilder sb) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element) {
                Element el = (Element) child;
                if (M.equals(el.getNamespaceURI())) convertNode(el, sb);
            }
        }
    }

    /**
     * 仅搜索直接子元素（不递归查找后代），避免嵌套同名元素被误匹配。
     * 例如 <m:sSup> 包含 <m:e> 和 <m:sup>，当 <m:e> 内部嵌套另一个 <m:sSup> 时，
     * getElementsByTagNameNS 会错误地返回嵌套的 <m:sup>。
     */
    private static Element findChild(Element parent, String localName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element) {
                Element el = (Element) child;
                if (M.equals(el.getNamespaceURI()) && localName.equals(el.getLocalName())) {
                    return el;
                }
            }
        }
        return null;
    }

    /**
     * 检查 m:r 元素中 m:rPr 是否包含指定的子元素（如 nor、sty）。
     */
    private static boolean hasMathRunProp(Element rEl, String propName) {
        NodeList rPrNodes = rEl.getElementsByTagNameNS(M, "rPr");
        if (rPrNodes.getLength() == 0) return false;
        Element rPr = (Element) rPrNodes.item(0);
        return rPr.getElementsByTagNameNS(M, propName).getLength() > 0;
    }

    /**
     * 检查 m:r 元素中 w:rPr 是否设置了斜体（w:i）。
     * @return true 如果 w:i 存在且 val 不为 "0"/"false"
     */
    /**
     * 检查 m:r 元素中 w:rPr 里 w:i 或 w:b 是否启用，若 m:r 自身未设置则回退到父容器的 m:ctrlPr。
     * @param rEl   m:r 元素
     * @param prop  "i" 或 "b"
     * @return true 如果属性存在且不为 0/false
     */
    private static boolean hasWrPrBoldOrItalicOrCtrlPr(Element rEl, String prop) {
        // 先检查 m:r 自身的 w:rPr
        NodeList wrPrNodes = rEl.getElementsByTagNameNS(W, "rPr");
        Element wrPr = wrPrNodes.getLength() > 0 ? (Element) wrPrNodes.item(0) : null;
        if (wrPr != null) {
            NodeList nodes = wrPr.getElementsByTagNameNS(W, prop);
            if (nodes.getLength() > 0) {
                String val = ((Element) nodes.item(0)).getAttributeNS(W, "val");
                return val.isEmpty() || !("0".equals(val) || "false".equals(val));
            }
        }
        // 回退到父容器（m:num/m:den/m:e/m:sup/m:sub等）的 m:ctrlPr/w:rPr
        return parentCtrlPrHasProp(rEl, prop);
    }

    /**
     * 检查父容器中 m:ctrlPr/w:rPr 是否包含指定属性。
     */
    private static boolean parentCtrlPrHasProp(Element rEl, String prop) {
        Node parent = rEl.getParentNode();
        if (!(parent instanceof Element)) return false;
        Element parentEl = (Element) parent;
        if (!M.equals(parentEl.getNamespaceURI())) return false;
        // 查找直接的 m:ctrlPr 子元素
        Element ctrlPr = findChild(parentEl, "ctrlPr");
        if (ctrlPr == null) return false;
        NodeList wrPrNodes = ctrlPr.getElementsByTagNameNS(W, "rPr");
        if (wrPrNodes.getLength() == 0) return false;
        Element wrPr = (Element) wrPrNodes.item(0);
        NodeList nodes = wrPr.getElementsByTagNameNS(W, prop);
        if (nodes.getLength() == 0) return false;
        String val = ((Element) nodes.item(0)).getAttributeNS(W, "val");
        return val.isEmpty() || !("0".equals(val) || "false".equals(val));
    }

    /**
     * 获取 m:r 元素中 m:rPr 的 m:sty val 属性值（"p"/"i"/"b"/"bi"/"bs"等），不存在返回 null。
     */
    private static String getMathStyVal(Element rEl) {
        NodeList rPrNodes = rEl.getElementsByTagNameNS(M, "rPr");
        if (rPrNodes.getLength() == 0) return null;
        Element rPr = (Element) rPrNodes.item(0);
        NodeList styNodes = rPr.getElementsByTagNameNS(M, "sty");
        if (styNodes.getLength() == 0) return null;
        String val = ((Element) styNodes.item(0)).getAttributeNS(M, "val");
        return val.isEmpty() ? null : val;
    }

    /**
     * 从 OMML 公式中提取字体大小（w:szCs 半磅值 → pt）。
     * 优先检查 m:oMathPr/m:ctrlPr，其次检查第一个 m:r 的 w:rPr。
     */
    private static String extractFontSize(Element mathEl) {
        // 1) 检查 m:oMathPr 中的 ctrlPr
        Element oMathPr = findChild(mathEl, "oMathPr");
        if (oMathPr != null) {
            String sz = extractSzFromCtrlPr(oMathPr);
            if (sz != null) return sz;
        }
        // 2) 检查第一个 m:r 中的 w:szCs 或 w:sz（半磅 → pt 除以 2）
        NodeList rNodes = mathEl.getElementsByTagNameNS(M, "r");
        for (int i = 0; i < rNodes.getLength(); i++) {
            Element rEl = (Element) rNodes.item(i);
            NodeList wrPrNodes = rEl.getElementsByTagNameNS(W, "rPr");
            if (wrPrNodes.getLength() > 0) {
                Element wrPr = (Element) wrPrNodes.item(0);
                // 优先 w:szCs（复杂脚本字号），其次 w:sz（半磅 → pt 除以 2）
                String sz = getSzFromWrPr(wrPr, "szCs");
                if (sz == null) sz = getSzFromWrPr(wrPr, "sz");
                if (sz != null) return sz;
            }
        }
        // 未找到任何字号，使用最小默认值 14pt
        return String.valueOf((long) MIN_MATH_FONT_SIZE);
    }

    private static String extractSzFromCtrlPr(Element parent) {
        NodeList ctrlPrNodes = parent.getElementsByTagNameNS(M, "ctrlPr");
        if (ctrlPrNodes.getLength() == 0) return null;
        Element ctrlPr = (Element) ctrlPrNodes.item(0);
        NodeList wrPrNodes = ctrlPr.getElementsByTagNameNS(W, "rPr");
        if (wrPrNodes.getLength() == 0) return null;
        Element wrPr = (Element) wrPrNodes.item(0);
        String sz = getSzFromWrPr(wrPr, "szCs");
        if (sz == null) sz = getSzFromWrPr(wrPr, "sz");
        return sz;
    }

    /** 公式字体最小字号（pt） */
    private static final double MIN_MATH_FONT_SIZE = 14.0;

    private static String getSzFromWrPr(Element wrPr, String szName) {
        NodeList szNodes = wrPr.getElementsByTagNameNS(W, szName);
        if (szNodes.getLength() == 0) return null;
        String val = ((Element) szNodes.item(0)).getAttributeNS(W, "val");
        if (val.isEmpty()) return null;
        try {
            double pt = Long.parseLong(val) / 2.0;  // 半磅 → pt
            if (pt < MIN_MATH_FONT_SIZE) pt = MIN_MATH_FONT_SIZE;
            if (pt == Math.floor(pt) && !Double.isInfinite(pt)) {
                return String.valueOf((long) pt);
            }
            return String.valueOf(pt);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 从 m:r 元素的 w:rPr/w:rFonts 中提取字体族（如 "Times New Roman"），
     * 若 m:r 自身未设置则回退到父容器的 m:ctrlPr。
     */
    private static String getWrPrFontFamilyOrCtrlPr(Element rEl) {
        // 先检查 m:r 自身的 w:rFonts
        String own = getWrPrFontFamily(rEl);
        if (own != null) return own;
        // 回退到父容器的 m:ctrlPr
        Node parent = rEl.getParentNode();
        if (!(parent instanceof Element)) return null;
        Element parentEl = (Element) parent;
        if (!M.equals(parentEl.getNamespaceURI())) return null;
        Element ctrlPr = findChild(parentEl, "ctrlPr");
        if (ctrlPr == null) return null;
        NodeList wrPrNodes = ctrlPr.getElementsByTagNameNS(W, "rPr");
        if (wrPrNodes.getLength() == 0) return null;
        Element wrPr = (Element) wrPrNodes.item(0);
        return getFontFamilyFromWrPr(wrPr);
    }

    /**
     * 从 m:r 元素的 w:rPr/w:rFonts 中提取字体族（如 "Times New Roman"）。
     */
    private static String getWrPrFontFamily(Element rEl) {
        NodeList wrPrNodes = rEl.getElementsByTagNameNS(W, "rPr");
        if (wrPrNodes.getLength() == 0) return null;
        return getFontFamilyFromWrPr((Element) wrPrNodes.item(0));
    }

    /** 从 w:rPr 元素中提取 w:rFonts 字体族 */
    private static String getFontFamilyFromWrPr(Element wrPr) {
        NodeList rFontsNodes = wrPr.getElementsByTagNameNS(W, "rFonts");
        if (rFontsNodes.getLength() == 0) return null;
        Element rFonts = (Element) rFontsNodes.item(0);
        // 优先拉丁字体：ascii > hAnsi
        String latin = getNonEmptyAttr(rFonts, "ascii");
        if (latin == null) latin = getNonEmptyAttr(rFonts, "hAnsi");
        // 东亚字体作为后备
        String ea = getNonEmptyAttr(rFonts, "eastAsia");
        if (latin != null && ea != null) {
            return "'" + latin + "', '" + ea + "'";
        } else if (latin != null) {
            return "'" + latin + "'";
        } else if (ea != null) {
            return "'" + ea + "'";
        }
        return null;
    }

    /** 获取元素的非空 W 命名空间属性值，不存在或为空返回 null */
    private static String getNonEmptyAttr(Element el, String localName) {
        String val = el.getAttributeNS(W, localName);
        return (val != null && !val.isEmpty()) ? val : null;
    }

    private static String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}

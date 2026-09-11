package cn.p4u.dth.parser;

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
                String text = tNodes.item(0).getTextContent();
                // 读取 m:rPr 格式属性
                boolean isNor = hasMathRunProp(el, "nor");     // <m:nor/> → 普通文字格式
                String styVal = getMathStyVal(el);              // <m:sty val="p"/"i"/"b"/"bi"/"bs"/>
                boolean styItalic = "i".equals(styVal) || "bi".equals(styVal);
                boolean styBold = "b".equals(styVal) || "bi".equals(styVal) || "bs".equals(styVal);
                // 读取 w:rPr 格式属性（从 m:r 自身和父容器 m:ctrlPr 继承）
                // 只把 Word 的显式西文格式视为用户设置的样式。
                // w:iCs/w:bCs 是复杂脚本格式，Word 可能把它写在直体拉丁文本（如 real/ideal）上，
                // 不能据此把拉丁公式文本渲染为斜体/粗体。
                Boolean wItalic = hasWrPrFormattingOrCtrlPr(el, "i");
                boolean wBold = Boolean.TRUE.equals(hasWrPrFormattingOrCtrlPr(el, "b"));
                // 读取 w:rPr/w:rFonts 字体信息（含 m:ctrlPr 继承）
                String fontFamily = getWrPrFontFamilyOrCtrlPr(el);

                // 确定最终斜体和粗体
                boolean effectiveItalic;
                boolean effectiveBold;
                if (isNor) {
                    // 普通文字不使用数学默认斜体，按 Word 文字格式显示。
                    effectiveItalic = Boolean.TRUE.equals(wItalic);
                    effectiveBold = wBold;
                } else if (styVal != null) {
                    // m:sty 明确指定样式（包括 p=直体）
                    effectiveItalic = styItalic;
                    effectiveBold = styBold;
                } else {
                    // OMML 数学变量默认斜体。“未声明”与 w:i=false 必须区分，
                    // 否则 Word 省略默认属性时，σ、n 等变量就会丢失斜体。
                    effectiveItalic = wItalic == null || wItalic;
                    effectiveBold = wBold;
                }

                appendStyledIdentifiers(sb, text, effectiveItalic, effectiveBold, fontFamily, !isNor);
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
            Element base = findChild(el, "e");
            Element sup = findChild(el, "sup");
            // sSup 始终是右上角上标。即使基元素内部是上花括号，也不能把上标
            // 改成居中的顶注；demo.docx 中“乘法优先”就位于整个括号结构的右上角。
            sb.append("<msup>");
            if (base != null) { sb.append("<mrow>"); convertChildren(base, sb); sb.append("</mrow>"); }
            if (sup != null) { sb.append("<mrow>"); convertChildren(sup, sb); sb.append("</mrow>"); }
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
            Element limLoc = naryPr == null ? null : findChild(naryPr, "limLoc");
            String limitLocation = limLoc == null ? "" : limLoc.getAttributeNS(M, "val");
            boolean sideLimits = "subSup".equals(limitLocation);
            boolean stackedLimits = "undOvr".equals(limitLocation);
            // Word 的上下型极限需要大算符排版；仅在当前运算符范围启用，避免改变整段布局。
            if (stackedLimits) sb.append("<mstyle displaystyle=\"true\">");
            String opTag;
            if (sub != null && sup != null) {
                opTag = sideLimits ? "msubsup" : "munderover";
            } else if (sub != null) {
                opTag = sideLimits ? "msub" : "munder";
            } else if (sup != null) {
                opTag = sideLimits ? "msup" : "mover";
            } else {
                opTag = null;
            }
            if (opTag != null) {
                sb.append("<").append(opTag).append("><mo");
                // 禁止浏览器在行内环境下把上下型极限自动挪到右侧。
                if (stackedLimits) sb.append(" largeop=\"true\" movablelimits=\"false\"");
                sb.append(">").append(escapeXml(chr)).append("</mo>");
                if (sub != null) { sb.append("<mrow>"); convertChildren(sub, sb); sb.append("</mrow>"); }
                if (sup != null) { sb.append("<mrow>"); convertChildren(sup, sb); sb.append("</mrow>"); }
                sb.append("</").append(opTag).append(">");
                if (stackedLimits) sb.append("</mstyle>");
                if (e != null) convertChildren(e, sb);
            } else {
                sb.append("<mo>").append(escapeXml(chr)).append("</mo>");
                if (stackedLimits) sb.append("</mstyle>");
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
            // m:dPr/m:ctrlPr 的 w:i 是公式对象的控制格式，不表示括号、竖线等
            // 定界符本身需要倾斜。定界符始终保持直体，仅继承字体和粗体。
            Element delimiterWrPr = getCtrlPrWrPr(dPr);
            boolean delimiterBold = isWrPrPropertyEnabled(delimiterWrPr, "b");
            String delimiterFont = delimiterWrPr != null ? getFontFamilyFromWrPr(delimiterWrPr) : null;
            appendStyledOperator(sb, open, false, delimiterBold, delimiterFont);
            if (findChild(el, "e") != null) convertChildren(findChild(el, "e"), sb);
            appendStyledOperator(sb, close, false, delimiterBold, delimiterFont);
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
        // 组合字符：Word 用它表示表达式上方/下方的可伸缩花括号等符号。
        if ("groupChr".equals(n)) {
            Element properties = findChild(el, "groupChrPr");
            String position = "top";
            String character = "";
            if (properties != null) {
                Element pos = findChild(properties, "pos");
                if (pos != null) {
                    String value = pos.getAttributeNS(M, "val");
                    if (!value.isEmpty()) position = value;
                }
                Element chr = findChild(properties, "chr");
                if (chr != null) character = chr.getAttributeNS(M, "val");
            }

            boolean below = "bot".equals(position);
            if (character.isEmpty()) character = below ? "⏟" : "⏞";
            sb.append(below ? "<munder accentunder=\"true\">" : "<mover accent=\"true\">");
            sb.append("<mrow>");
            Element expression = findChild(el, "e");
            if (expression != null) convertChildren(expression, sb);
            sb.append("</mrow><mo stretchy=\"true\">")
                    .append(escapeXml(character)).append("</mo>")
                    .append(below ? "</munder>" : "</mover>");
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
        Element rPr = findChild(rEl, "rPr");
        Element prop = rPr == null ? null : findChild(rPr, propName);
        if (prop == null) return false;
        String val = prop.getAttributeNS(M, "val");
        return !("0".equals(val) || "false".equalsIgnoreCase(val) || "off".equalsIgnoreCase(val));
    }

    /**
     * 检查 m:r 元素中 w:rPr 的一组等价格式属性是否启用；若 m:r 自身未设置，
     * 则回退到父容器的 m:ctrlPr。
     * @param rEl  m:r 元素
     * @param props 等价属性名，例如 "i"、"iCs"
     * @return true 如果属性存在且不为 0/false
     */
    private static Boolean hasWrPrFormattingOrCtrlPr(Element rEl, String... props) {
        // 先检查 m:r 自身的 w:rPr
        NodeList wrPrNodes = rEl.getElementsByTagNameNS(W, "rPr");
        Element wrPr = wrPrNodes.getLength() > 0 ? (Element) wrPrNodes.item(0) : null;
        if (wrPr != null) {
            Boolean value = getEnabledWrPrProperty(wrPr, props);
            if (value != null) {
                return value;
            }
        }
        // 回退到父容器（m:num/m:den/m:e/m:sup/m:sub等）的 m:ctrlPr/w:rPr
        return parentCtrlPrHasProp(rEl, props);
    }

    /**
     * 检查父容器中 m:ctrlPr/w:rPr 是否包含指定属性。
     */
    private static Boolean parentCtrlPrHasProp(Element rEl, String... props) {
        Node parent = rEl.getParentNode();
        if (!(parent instanceof Element)) return null;
        Element parentEl = (Element) parent;
        if (!M.equals(parentEl.getNamespaceURI())) return null;
        // 查找直接的 m:ctrlPr 子元素
        Element ctrlPr = findChild(parentEl, "ctrlPr");
        if (ctrlPr == null) return null;
        NodeList wrPrNodes = ctrlPr.getElementsByTagNameNS(W, "rPr");
        if (wrPrNodes.getLength() == 0) return null;
        Element wrPr = (Element) wrPrNodes.item(0);
        Boolean value = getEnabledWrPrProperty(wrPr, props);
        return value;
    }

    /**
     * 读取一组等价的 Word run 属性，例如西文字体的 w:i 和复杂脚本的 w:iCs。
     * 返回 null 表示这些属性均未声明；任一属性启用则返回 true。
     */
    private static Boolean getEnabledWrPrProperty(Element wrPr, String... props) {
        boolean declared = false;
        for (String prop : props) {
            NodeList nodes = wrPr.getElementsByTagNameNS(W, prop);
            if (nodes.getLength() == 0) continue;
            declared = true;
            String val = ((Element) nodes.item(0)).getAttributeNS(W, "val");
            if (val.isEmpty() || !("0".equalsIgnoreCase(val)
                    || "false".equalsIgnoreCase(val)
                    || "off".equalsIgnoreCase(val))) {
                return true;
            }
        }
        return declared ? Boolean.FALSE : null;
    }

    /**
     * 判断 w:rPr 中的单个布尔格式属性是否显式启用。
     */
    private static boolean isWrPrPropertyEnabled(Element wrPr, String prop) {
        if (wrPr == null) return false;
        return Boolean.TRUE.equals(getEnabledWrPrProperty(wrPr, prop));
    }

    /**
     * 获取公式对象属性（如 m:dPr）中的 m:ctrlPr/w:rPr。
     */
    private static Element getCtrlPrWrPr(Element propertyEl) {
        if (propertyEl == null) return null;
        Element ctrlPr = findChild(propertyEl, "ctrlPr");
        if (ctrlPr == null) return null;
        NodeList wrPrNodes = ctrlPr.getElementsByTagNameNS(W, "rPr");
        return wrPrNodes.getLength() > 0 ? (Element) wrPrNodes.item(0) : null;
    }

    /**
     * 输出带有显式 Word 字体格式的 MathML 运算符/定界符。
     */
    private static void appendStyledOperator(StringBuilder sb, String text,
                                             boolean italic, boolean bold, String fontFamily) {
        StringBuilder style = new StringBuilder();
        if (bold && italic) {
            style.append("font-weight:bold;font-style:italic");
        } else if (bold) {
            style.append("font-weight:bold");
        } else if (italic) {
            style.append("font-style:italic");
        }
        if (fontFamily != null) {
            if (style.length() > 0) style.append(";");
            style.append("font-family:").append(fontFamily);
        }

        // mathvariant=normal 防止浏览器将字符替换为数学专用字形，确保 Word 字体生效。
        sb.append("<mo mathvariant=\"normal\"");
        if (style.length() > 0) {
            sb.append(" style=\"").append(style).append("\"");
        }
        sb.append(">").append(escapeXml(text)).append("</mo>");
    }

    /**
     * 输出公式文本。Word 会把容器 ctrlPr 的斜体格式附加到子运行，但该格式不能
     * 应用于运行中混排的括号、斜杠和竖线等定界符。
     */
    private static void appendStyledIdentifiers(StringBuilder sb, String text,
                                                boolean italic, boolean bold, String fontFamily,
                                                boolean mathMode) {
        if (mathMode && (italic || bold)) {
            // 数学格式只作用于数学字母，不把同一运行里的数字、运算符和中文一起倾斜。
            text.codePoints().forEach(cp -> appendStyledIdentifier(sb,
                    new String(Character.toChars(cp)), italic && isMathLetter(cp),
                    bold && isMathLetter(cp), fontFamily));
            return;
        }
        int codePointCount = text.codePointCount(0, text.length());
        boolean split = italic && (codePointCount > 1 || containsUprightDelimiter(text));
        if (!split) {
            appendStyledIdentifier(sb, text, italic && !containsUprightDelimiter(text), bold, fontFamily);
            return;
        }

        text.codePoints().forEach(codePoint -> appendStyledIdentifier(
                sb,
                new String(Character.toChars(codePoint)),
                !isUprightDelimiter(codePoint),
                bold,
                fontFamily));
    }

    private static boolean isMathLetter(int cp) {
        Character.UnicodeScript script = Character.UnicodeScript.of(cp);
        return Character.isLetter(cp) && (script == Character.UnicodeScript.LATIN
                || script == Character.UnicodeScript.GREEK);
    }

    private static void appendStyledIdentifier(StringBuilder sb, String text,
                                               boolean italic, boolean bold, String fontFamily) {
        StringBuilder style = new StringBuilder();
        if (bold) style.append("font-weight:bold");
        if (italic) {
            if (style.length() > 0) style.append(";");
            style.append("font-style:italic");
        }
        if (fontFamily != null) {
            if (style.length() > 0) style.append(";");
            style.append("font-family:").append(fontFamily);
        }

        // mathvariant=normal 防止浏览器自动替换数学字形，字体和倾斜均由 CSS 控制。
        sb.append("<mi mathvariant=\"normal\"");
        if (style.length() > 0) sb.append(" style=\"").append(style).append("\"");
        sb.append(">").append(escapeXml(text)).append("</mi>");
    }

    private static boolean containsUprightDelimiter(String text) {
        return text.codePoints().anyMatch(OmmlToMathmlConverter::isUprightDelimiter);
    }

    private static boolean isUprightDelimiter(int codePoint) {
        return switch (codePoint) {
            case '/', '\\', '|', '(', ')', '[', ']', '{', '}', '<', '>',
                    '\u2016', '\u2045', '\u2046', '\u2215', '\u2223', '\u2225',
                    '\u2308', '\u2309', '\u230A', '\u230B', '\u2329', '\u232A',
                    '\u27E8', '\u27E9', '\u27EA', '\u27EB',
                    '\u3010', '\u3011', '\u3014', '\u3015', '\u3016', '\u3017',
                    '\u3018', '\u3019', '\u301A', '\u301B',
                    '\uFF08', '\uFF09', '\uFF0F', '\uFF3B', '\uFF3D',
                    '\uFF5B', '\uFF5C', '\uFF5D' -> true;
            default -> false;
        };
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
        // 优先拉丁/复杂脚本字体：ascii > hAnsi > cs。
        // Word 公式经常只写 cs（例如 Times New Roman）和 eastAsia（例如宋体）；
        // 如果跳过 cs，拉丁字符会被错误地渲染成 eastAsia 字体。
        String latin = getNonEmptyAttr(rFonts, "ascii");
        if (latin == null) latin = getNonEmptyAttr(rFonts, "hAnsi");
        if (latin == null) latin = getNonEmptyAttr(rFonts, "cs");
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

package cn.p4u.smart.parser;

import org.w3c.dom.*;
import java.util.logging.Logger;

/**
 * OMML (Office Math Markup Language) 到 LaTeX 的转换器。
 *
 * <p>核心职责：将 OOXML 文档中由命名空间 {@code m:} 标记的数学元素（OMML）
 * 递归地转换为等效的 LaTeX 字符串，以便在 HTML 输出中通过 MathJax/KaTeX 等引擎渲染公式。</p>
 *
 * <p>主要使用场景：{@link DocumentParser} 在解析 .docx 文档遇到 {@code <m:oMath>}
 * 或 {@code <m:oMathPara>} 元素时，调用本类的 {@link #convert(Element)} 方法完成数学公式的转换，
 * 转换结果最终写入 {@link MathElement} 模型对象。</p>
 *
 * <p>支持的 OMML 元素包括：分数(f)、上标(sSup)、下标(sSub)、上下标(sSubSup)、
 * 根式(rad)、定界符(d)、N 元运算符(nary)、重音符号(acc)、横线(bar)、
 * 等式数组(eqArr)、矩阵(m) 以及普通文本运行(r)。</p>
 */
public final class OmmlToLatexConverter {

    private static final Logger LOG = Logger.getLogger(OmmlToLatexConverter.class.getName());

    /** OMML 命名空间 URI，用于通过命名空间限定查找子元素 */
    private static final String M = "http://schemas.openxmlformats.org/officeDocument/2006/math";
    /** WordprocessingML 命名空间 URI，用于查找 w:rPr 中的 w:i、w:b */
    private static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";

    /** 私有构造函数，防止实例化——本类仅提供静态方法 */
    private OmmlToLatexConverter() {}

    /**
     * 将 OMML 数学元素转换为 LaTeX 字符串。
     *
     * <p>这是本类唯一的公开入口方法。调用方只需传入 OMML 根元素，
     * 即可获得完整的 LaTeX 公式文本。如果转换过程中出现异常，会记录警告日志并返回空字符串，
     * 避免因单个公式失败而中断整个文档的转换流程。</p>
     *
     * @param mathEl OMML 数学元素（通常为 {@code <m:oMath>} 或 {@code <m:oMathPara>}）
     * @return 对应的 LaTeX 字符串；若输入为 null 或转换失败则返回空字符串
     */
    public static String convert(Element mathEl) {
        if (mathEl == null) return "";
        try {
            return convertNode(mathEl).toString();
        } catch (Exception e) {
            LOG.warning("OMML→LaTeX conversion failed: " + e.getMessage());
            return "";
        }
    }

    /**
     * 递归地将单个 DOM 节点转换为 LaTeX 片段。
     *
     * <p>根据元素的 localName 分发到对应的转换方法；
     * 对于未专门处理的元素，默认递归转换其所有子元素。</p>
     *
     * @param node 要转换的 DOM 节点，非 Element 节点将被忽略并返回空 StringBuilder
     * @return 包含 LaTeX 文本的 StringBuilder
     */
    private static StringBuilder convertNode(Node node) {
        // 只处理 Element 节点，文本节点和属性节点等不参与 OMML 结构转换
        if (!(node instanceof Element)) return new StringBuilder();
        Element el = (Element) node;

        String localName = el.getLocalName();
        // oMath / oMathPara 是公式容器，不产生额外 LaTeX 标记，直接递归处理子元素
        if ("oMath".equals(localName) || "oMathPara".equals(localName)) {
            return convertChildren(el);
        } else if ("r".equals(localName)) {
            return convertRun(el);
        } else if ("f".equals(localName)) {
            return convertFraction(el);
        } else if ("sSup".equals(localName)) {
            return convertSuperscript(el);
        } else if ("sSub".equals(localName)) {
            return convertSubscript(el);
        } else if ("sSubSup".equals(localName)) {
            return convertSubSuperscript(el);
        } else if ("rad".equals(localName)) {
            return convertRadical(el);
        } else if ("d".equals(localName)) {
            return convertDelimiter(el);
        } else if ("nary".equals(localName)) {
            return convertNary(el);
        } else if ("acc".equals(localName)) {
            return convertAccent(el);
        } else if ("bar".equals(localName)) {
            return convertBar(el);
        } else if ("eqArr".equals(localName)) {
            return convertEqArray(el);
        } else if ("m".equals(localName)) {
            return convertMatrix(el);
        } else {
            // 未识别的 OMML 元素，按 fallback 策略递归处理子元素，尽量保留内容
            return convertChildren(el);
        }
    }

    /**
     * 递归转换父元素下所有属于 OMML 命名空间的子元素，拼接为连续的 LaTeX 文本。
     *
     * <p>只处理命名空间为 {@link #M} 的 Element 子节点，
     * 非 OMML 命名空间的节点（如 w: 元素）会被跳过。</p>
     *
     * @param parent 父元素
     * @return 拼接所有子元素转换结果的 StringBuilder
     */
    private static StringBuilder convertChildren(Element parent) {
        StringBuilder sb = new StringBuilder();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element) {
                Element el = (Element) child;
                // 仅处理 OMML 命名空间的元素，跳过其他命名空间（如 w:）的混入节点
                if (M.equals(el.getNamespaceURI())) {
                    sb.append(convertNode(el));
                }
            }
        }
        return sb;
    }

    /**
     * 转换 OMML 文本运行元素 ({@code <m:r>})，提取其中的文本内容并进行 LaTeX 转义。
     *
     * <p>OMML 的 {@code <m:r>} 结构与 Word 的 {@code <w:r>} 类似，
     * 实际文本存放在子元素 {@code <m:t>} 中。</p>
     *
     * <p>检查 {@code <m:rPr>} 中的格式属性：
     * <ul>
     *   <li>{@code <m:nor/>} — 正常/直立文本，对应 LaTeX 的 {@code \mathrm{}}</li>
     *   <li>{@code <m:sty m:val="b"/>} — 粗体，对应 LaTeX 的 {@code \mathbf{}}</li>
     *   <li>{@code <m:sty m:val="bi"/>} — 粗斜体，对应 LaTeX 的 {@code \mathbf{}}（LaTeX 中 \mathbf 带斜体）</li>
     * </ul>
     *
     * @param rEl {@code <m:r>} 元素
     * @return 带格式的 LaTeX 文本；若无 {@code <m:t>} 子元素则返回空 StringBuilder
     */
    private static StringBuilder convertRun(Element rEl) {
        // 检查 m:rPr 属性：<m:nor/> 表示使用普通文本格式（非数学斜体）
        boolean isNor = false;     // <m:nor/> → 使用 w:rPr 的文字格式
        boolean isStyBold = false; // <m:sty m:val="b"/"bi"/> → 粗体
        // 检查 w:rPr 属性：当 m:nor 存在时决定实际字体样式
        boolean wItalic = false;   // <w:i/> → 斜体
        boolean wBold = false;     // <w:b/> → 粗体

        NodeList children = rEl.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (!(child instanceof Element)) continue;
            Element el = (Element) child;
            if ("rPr".equals(el.getLocalName())) {
                NodeList rPrChildren = el.getChildNodes();
                for (int j = 0; j < rPrChildren.getLength(); j++) {
                    Node rprChild = rPrChildren.item(j);
                    if (!(rprChild instanceof Element)) continue;
                    Element rprEl = (Element) rprChild;
                    String ln = rprEl.getLocalName();
                    if ("nor".equals(ln)) {
                        isNor = true;
                    } else if ("sty".equals(ln)) {
                        String styVal = rprEl.getAttributeNS(M, "val");
                        if ("b".equals(styVal) || "bi".equals(styVal) || "bs".equals(styVal)) {
                            isStyBold = true;
                        }
                    }
                }
            } else if (W.equals(el.getNamespaceURI()) && "rPr".equals(el.getLocalName())) {
                // 解析 w:rPr 中的 w:i、w:b（注意 attr 用 W namespace）
                NodeList wrPrChildren = el.getChildNodes();
                for (int j = 0; j < wrPrChildren.getLength(); j++) {
                    Node wrChild = wrPrChildren.item(j);
                    if (!(wrChild instanceof Element)) continue;
                    Element wrEl = (Element) wrChild;
                    String ln = wrEl.getLocalName();
                    if ("i".equals(ln)) {
                        String val = wrEl.getAttributeNS(W, "val");
                        // val 为空、true、1 表示斜体；0、false 表示非斜体
                        wItalic = val.isEmpty() || !("0".equals(val) || "false".equals(val));
                    } else if ("b".equals(ln)) {
                        String val = wrEl.getAttributeNS(W, "val");
                        wBold = val.isEmpty() || !("0".equals(val) || "false".equals(val));
                    }
                }
            }
        }

        // 提取 m:t 文本
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element) {
                Element el = (Element) child;
                if ("t".equals(el.getLocalName())) {
                    String text = latexEscape(el.getTextContent());
                    // 判断最终样式
                    boolean effectiveItalic;
                    boolean effectiveBold;
                    if (isNor) {
                        // m:nor → 使用文字格式（w:rPr 的 i/b）
                        effectiveItalic = wItalic;
                        effectiveBold = wBold || isStyBold;
                    } else {
                        // 无 m:nor → 默认数学斜体
                        effectiveItalic = true;
                        effectiveBold = wBold || isStyBold;
                    }
                    if (effectiveBold && effectiveItalic) {
                        return new StringBuilder("\\mathbf{").append(text).append("}");
                    } else if (effectiveBold) {
                        return new StringBuilder("\\mathbf{").append(text).append("}");
                    } else if (!effectiveItalic) {
                        return new StringBuilder("\\mathrm{").append(text).append("}");
                    }
                    return new StringBuilder(text);
                }
            }
        }
        return new StringBuilder();
    }

    /**
     * 转换分数元素 ({@code <m:f>})，生成 {@code \frac{分子}{分母}} 格式的 LaTeX。
     *
     * @param fEl {@code <m:f>} 元素
     * @return 包含 \frac 命令的 LaTeX 片段
     */
    private static StringBuilder convertFraction(Element fEl) {
        Element num = findChild(fEl, "num");
        Element den = findChild(fEl, "den");
        return new StringBuilder("\\frac{")
                .append(num != null ? convertChildren(num) : "")
                .append("}{")
                .append(den != null ? convertChildren(den) : "")
                .append("}");
    }

    /**
     * 转换上标元素 ({@code <m:sSup>})，生成 {@code 基^{上标}} 格式的 LaTeX。
     *
     * @param el {@code <m:sSup>} 元素
     * @return 包含上标的 LaTeX 片段
     */
    private static StringBuilder convertSuperscript(Element el) {
        Element base = findChild(el, "e");
        Element sup = findChild(el, "sup");
        return new StringBuilder()
                .append(base != null ? convertChildren(base) : "")
                .append("^{")
                .append(sup != null ? convertChildren(sup) : "")
                .append("}");
    }

    /**
     * 转换下标元素 ({@code <m:sSub>})，生成 {@code 基_{下标}} 格式的 LaTeX。
     *
     * @param el {@code <m:sSub>} 元素
     * @return 包含下标的 LaTeX 片段
     */
    private static StringBuilder convertSubscript(Element el) {
        Element base = findChild(el, "e");
        Element sub = findChild(el, "sub");
        return new StringBuilder()
                .append(base != null ? convertChildren(base) : "")
                .append("_{")
                .append(sub != null ? convertChildren(sub) : "")
                .append("}");
    }

    /**
     * 转换上下标元素 ({@code <m:sSubSup>})，生成 {@code 基_{下标}^{上标}} 格式的 LaTeX。
     *
     * @param el {@code <m:sSubSup>} 元素
     * @return 同时包含下标和上标的 LaTeX 片段
     */
    private static StringBuilder convertSubSuperscript(Element el) {
        Element base = findChild(el, "e");
        Element sub = findChild(el, "sub");
        Element sup = findChild(el, "sup");
        return new StringBuilder()
                .append(base != null ? convertChildren(base) : "")
                .append("_{")
                .append(sub != null ? convertChildren(sub) : "")
                .append("}^{")
                .append(sup != null ? convertChildren(sup) : "")
                .append("}");
    }

    /**
     * 转换根式元素 ({@code <m:rad>})。
     *
     * <p>若无度数({@code <m:deg>})则生成 {@code \sqrt{被开方数}}；
     * 若有度数则生成 {@code \sqrt[度数]{被开方数}}。</p>
     *
     * @param el {@code <m:rad>} 元素
     * @return 包含 \sqrt 命令的 LaTeX 片段
     */
    private static StringBuilder convertRadical(Element el) {
        Element deg = findChild(el, "deg");
        Element e = findChild(el, "e");
        // 无度数时为平方根，使用简写形式 \sqrt{...}
        if (deg == null) {
            return new StringBuilder("\\sqrt{")
                    .append(e != null ? convertChildren(e) : "")
                    .append("}");
        }
        // 有度数时为 n 次方根，使用 \sqrt[n]{...} 形式
        return new StringBuilder("\\sqrt[")
                .append(convertChildren(deg))
                .append("]{")
                .append(e != null ? convertChildren(e) : "")
                .append("}");
    }

    /**
     * 转换定界符元素 ({@code <m:d>})，生成带有左右定界符的 LaTeX 表达式。
     *
     * <p>读取 {@code <m:dPr>} 中的 {@code begChr} 和 {@code endChr} 属性
     * 确定左右定界符字符，并通过 {@link #latexDelimiter(String)} 映射为 LaTeX 的
     * {@code \left} / {@code \right} 定界符命令。定界符内的多个 {@code <m:e>}
     * 元素以 {@code &} 连接（对应 LaTeX 的多列分隔）。</p>
     *
     * @param el {@code <m:d>} 元素
     * @return 包含 \left...\right 定界符的 LaTeX 片段
     */
    private static StringBuilder convertDelimiter(Element el) {
        Element dPr = findChild(el, "dPr");
        // 默认定界符为圆括号
        String open = "(";
        String close = ")";
        if (dPr != null) {
            Element begChr = findChild(dPr, "begChr");
            Element endChr = findChild(dPr, "endChr");
            // begChr/endChr 的 val 属性指定实际的定界符字符
            if (begChr != null) open = getAttrVal(begChr);
            if (endChr != null) close = getAttrVal(endChr);
        }
        StringBuilder sb = new StringBuilder(latexDelimiter(open));
        NodeList children = el.getChildNodes();
        boolean first = true;
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element) {
                Element ce = (Element) child;
                if ("e".equals(ce.getLocalName())) {
                    // 多个 <m:e> 之间用 & 分隔，对应 LaTeX 中定界符内的多列排版
                    if (!first) sb.append(" & ");
                    sb.append(convertChildren(ce));
                    first = false;
                }
            }
        }
        sb.append(latexDelimiter(close));
        return sb;
    }

    /**
     * 转换 N 元运算符元素 ({@code <m:nary>})，如求和、求积、积分等。
     *
     * <p>根据 {@code <m:naryPr>} 中的 {@code chr} 属性确定运算符类型，
     * 将 Unicode 运算符字符映射为对应的 LaTeX 命令。
     * 若无法识别则默认使用 {@code \sum}。</p>
     *
     * @param el {@code <m:nary>} 元素
     * @return 包含运算符及上下限的 LaTeX 片段
     */
    private static StringBuilder convertNary(Element el) {
        Element naryPr = findChild(el, "naryPr");
        // 默认为求和运算符
        String operator = "\\sum";
        if (naryPr != null) {
            Element chrEl = findChild(naryPr, "chr");
            if (chrEl != null) {
                String chr = getAttrVal(chrEl);
                // 将 Unicode 运算符字符映射为 LaTeX 命令
                if ("∏".equals(chr)) {
                    operator = "\\prod";
                } else if ("∫".equals(chr)) {
                    operator = "\\int";
                } else if ("⋃".equals(chr)) {
                    operator = "\\bigcup";
                } else if ("⋂".equals(chr)) {
                    operator = "\\bigcap";
                }
            }
        }
        Element sub = findChild(el, "sub");
        Element sup = findChild(el, "sup");
        Element e = findChild(el, "e");
        StringBuilder sb = new StringBuilder(operator);
        // 上下限分别映射为 LaTeX 的下标和上标
        if (sub != null) sb.append("_{").append(convertChildren(sub)).append("}");
        if (sup != null) sb.append("^{").append(convertChildren(sup)).append("}");
        sb.append(" ").append(e != null ? convertChildren(e) : "");
        return sb;
    }

    /**
     * 转换重音符号元素 ({@code <m:acc>})，如帽子、波浪号、向量箭头等。
     *
     * <p>根据 {@code <m:accPr>} 中的 {@code chr} 属性确定重音类型，
     * 将 Unicode 重音字符映射为对应的 LaTeX 命令。
     * 若无法识别则默认使用 {@code \hat}。</p>
     *
     * @param el {@code <m:acc>} 元素
     * @return 包含重音命令的 LaTeX 片段
     */
    private static StringBuilder convertAccent(Element el) {
        Element accPr = findChild(el, "accPr");
        // 默认为帽子重音
        String accent = "\\hat";
        if (accPr != null) {
            Element chrEl = findChild(accPr, "chr");
            if (chrEl != null) {
                String chr = getAttrVal(chrEl);
                // 将 Unicode 重音字符映射为 LaTeX 命令
                if ("̃".equals(chr)) {
                    accent = "\\tilde";
                } else if ("⃗".equals(chr)) {
                    accent = "\\vec";
                } else if ("̅".equals(chr)) {
                    accent = "\\overline";
                } else if ("̇".equals(chr)) {
                    accent = "\\dot";
                } else if ("̈".equals(chr)) {
                    accent = "\\ddot";
                }
            }
        }
        Element e = findChild(el, "e");
        return new StringBuilder(accent).append("{")
                .append(e != null ? convertChildren(e) : "")
                .append("}");
    }

    /**
     * 转换横线元素 ({@code <m:bar>})，生成上横线或下花括号。
     *
     * <p>根据 {@code <m:barPr>} 中的 {@code pos} 属性判断横线位置：
     * {@code bot} 生成 {@code \\underbrace{...}}，否则生成 {@code \\overline{...}}。</p>
     *
     * @param el {@code <m:bar>} 元素
     * @return 包含横线命令的 LaTeX 片段
     */
    private static StringBuilder convertBar(Element el) {
        Element barPr = findChild(el, "barPr");
        boolean isUnder = false;
        if (barPr != null) {
            Element pos = findChild(barPr, "pos");
            if (pos != null) {
                String val = getAttrVal(pos);
                // pos="bot" 表示横线在下方
                if ("bot".equals(val)) isUnder = true;
            }
        }
        Element e = findChild(el, "e");
        // 下方横线使用 \\underbrace，上方横线使用 \\overline
        String cmd = isUnder ? "\\underbrace" : "\\overline";
        return new StringBuilder(cmd).append("{")
                .append(e != null ? convertChildren(e) : "")
                .append("}");
    }

    /**
     * 转换等式数组元素 ({@code <m:eqArr>})，生成 {@code \begin{aligned}...\end{aligned}} 环境。
     *
     * <p>每个 {@code <m:e>} 子元素对应等式数组中的一行，
     * 行之间以 {@code \\\\} 分隔。</p>
     *
     * @param el {@code <m:eqArr>} 元素
     * @return 包含 aligned 环境的 LaTeX 片段
     */
    private static StringBuilder convertEqArray(Element el) {
        StringBuilder sb = new StringBuilder("\\begin{aligned}\n");
        NodeList children = el.getChildNodes();
        boolean first = true;
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element) {
                Element ce = (Element) child;
                if ("e".equals(ce.getLocalName())) {
                    // 等式行之间用 \\ 换行分隔
                    if (!first) sb.append(" \\\\\\\\\n");
                    sb.append(convertChildren(ce));
                    first = false;
                }
            }
        }
        sb.append("\n\\end{aligned}");
        return sb;
    }

    /**
     * 转换矩阵元素 ({@code <m:m>})，生成 {@code \begin{matrix}...\end{matrix}} 环境。
     *
     * <p>遍历 {@code <m:mr>}（矩阵行）和 {@code <m:e>}（矩阵单元格），
     * 行间以 {@code \\\\} 分隔，列间以 {@code &} 分隔。</p>
     *
     * @param el {@code <m:m>} 元素
     * @return 包含 matrix 环境的 LaTeX 片段
     */
    private static StringBuilder convertMatrix(Element el) {
        StringBuilder sb = new StringBuilder("\\begin{matrix}\n");
        // 通过命名空间限定查找所有矩阵行 <m:mr>
        NodeList rows = el.getElementsByTagNameNS(M, "mr");
        for (int i = 0; i < rows.getLength(); i++) {
            // 行间用 \\ 换行
            if (i > 0) sb.append(" \\\\\\\\\n");
            Element row = (Element) rows.item(i);
            // 查找当前行中的所有单元格 <m:e>
            NodeList cells = row.getElementsByTagNameNS(M, "e");
            for (int j = 0; j < cells.getLength(); j++) {
                // 列间用 & 分隔
                if (j > 0) sb.append(" & ");
                sb.append(convertChildren((Element) cells.item(j)));
            }
        }
        sb.append("\n\\end{matrix}");
        return sb;
    }

    // --- 辅助方法 ---

    /**
     * 在父元素的 OMML 命名空间子元素中，按 localName 查找第一个匹配的子元素。
     *
     * <p>使用 {@code getElementsByTagNameNS} 进行命名空间限定查找，
     * 避免 default 命名空间导致误匹配。</p>
     *
     * @param parent    父元素
     * @param localName 目标子元素的本地名称（不含命名空间前缀）
     * @return 第一个匹配的子元素；若未找到则返回 null
     */
    private static Element findChild(Element parent, String localName) {
        // 仅搜索直接子元素，避免嵌套同名元素误匹配
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
     * 获取 OMML 元素的 {@code val} 属性值。
     *
     * <p>某些 XML 解析器会将带命名空间前缀的属性（如 {@code m:val}）和
     * 不带前缀的属性（如 {@code val}）区分对待，因此依次尝试两种方式读取，
     * 确保兼容不同的 DOM 实现。</p>
     *
     * @param chrEl 包含 val 属性的元素
     * @return val 属性的文本值；若属性不存在则返回空字符串
     */
    private static String getAttrVal(Element chrEl) {
        // 优先使用命名空间限定方式读取属性
        String val = chrEl.getAttributeNS(M, "val");
        // 若命名空间限定方式未获取到值，再尝试带前缀的方式
        return val.isEmpty() ? chrEl.getAttribute("m:val") : val;
    }

    /**
     * 对文本内容进行 LaTeX 特殊字符转义。
     *
     * <p>LaTeX 中有 10 个特殊字符（\ { } _ ^ & # $ % ~）具有语法含义，
     * 若它们作为数学公式中的普通文本出现，必须添加反斜杠前缀进行转义，
     * 否则会导致 LaTeX 解析错误或公式渲染失败。</p>
     *
     * @param text 原始文本
     * @return 转义后的文本，可直接嵌入 LaTeX 公式
     */
    private static String latexEscape(String text) {
        return text
                .replace("\\", "\\\\")   // 反斜杠是 LaTeX 的转义前缀，本身需要双重转义
                .replace("{", "\\{")     // 花括号用于分组，需要转义
                .replace("}", "\\}")
                .replace("_", "\\_")     // 下划线是下标语法，需要转义
                .replace("^", "\\^")     // 插入符是上标语法，需要转义
                .replace("&", "\\&")     // & 是列分隔符，需要转义
                .replace("#", "\\#")     // # 是参数占位符，需要转义
                .replace("$", "\\$")     // $ 是数学模式定界符，需要转义
                .replace("%", "\\%")     // % 是注释符号，需要转义
                .replace("~", "\\textasciitilde{}"); // ~ 是不间断空格，LaTeX 中用命令表示波浪号
    }

    /**
     * 将定界符字符映射为 LaTeX 的 {@code \left} / {@code \right} 命令形式。
     *
     * <p>使用 {@code \left} / {@code \right} 包裹定界符的目的是让定界符
     * 根据内容高度自动调整大小，这对于包含分数、根式等高层结构的公式尤为重要。
     * 空字符串表示省略定界符，映射为 {@code \left.} / {@code \right.}（不可见定界符）。</p>
     *
     * @param chr 定界符字符
     * @return 带有 \left 或 \right 前缀的 LaTeX 定界符命令
     */
    private static String latexDelimiter(String chr) {
        // 常见定界符的完整映射：圆括号、方括号、花括号、双竖线、单竖线、尖括号
        if ("(".equals(chr)) return "\\left(";
        if (")".equals(chr)) return "\\right)";
        if ("[".equals(chr)) return "\\left[";
        if ("]".equals(chr)) return "\\right]";
        if ("{".equals(chr)) return "\\left\\{";    // 花括号在 LaTeX 中需要双重转义
        if ("}".equals(chr)) return "\\right\\}";
        if ("‖".equals(chr)) return "\\left\\|";    // 双竖线定界符
        if ("|".equals(chr)) return "\\left|";       // 单竖线绝对值定界符
        if ("⟨".equals(chr)) return "\\left\\langle"; // 左尖括号
        if ("⟩".equals(chr)) return "\\right\\rangle"; // 右尖括号
        // 空字符串表示该侧无定界符，使用不可见的 \left. 或 \right. 占位
        if ("".equals(chr)) return "\\left.";
        // 未识别的字符，直接追加在 \left 后面作为自定义定界符
        return "\\left" + chr;
    }
}

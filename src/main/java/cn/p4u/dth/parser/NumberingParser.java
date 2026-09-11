package cn.p4u.dth.parser;

import org.w3c.dom.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * OOXML 编号定义解析器。
 *
 * <p>解析 .docx 解压后 word/numbering.xml 文件，提取每个 numId 对应的列表编号格式（numFmt），
 * 例如 "decimal"（有序数字列表）、"bullet"（无序圆点列表）、"lowerLetter" 等。</p>
 *
 * <p>核心职责：将 OOXML 中 abstractNum → lvl → numFmt 的定义链与 num → abstractNumId 的
 * 引用链关联起来，最终输出 numId → numFmt 的映射，供 DocumentParser 在解析段落时
 * 判断列表项类型（有序/无序）使用。</p>
 *
 * <p>主要使用场景：DocumentParser 解析 w:numPr 元素时，通过 numId 查询此解析器输出的映射，
 * 确定 w:numFmt 值，从而在 ParagraphBlock 中标记列表格式。</p>
 */
public final class NumberingParser {

    /** OOXML wordprocessingml 命名空间 URI */
    private static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";

    /** 私有构造，工具类不允许实例化 */
    private NumberingParser() {}

    /**
     * 解析 numbering.xml 文件，构建 numId → numFmt 的映射。
     *
     * <p>解析过程分两步：</p>
     * <ol>
     *   <li>遍历所有 abstractNum 元素，提取每个 abstractNumId 下第 0 级（ilvl="0"）的 numFmt；</li>
     *   <li>遍历所有 num 元素，通过其 abstractNumId 引用找到对应 numFmt，建立 numId → numFmt 映射。</li>
     * </ol>
     *
     * <p>若文件不存在或解析失败，返回空 Map，不抛出异常。</p>
     *
     * @param numberingFile numbering.xml 文件的路径（从 .docx 解压后的临时目录中获取）
     * @return 映射表，key 为 numId（字符串），value 为 numFmt（如 "decimal"、"bullet"、"lowerLetter" 等）；
     *         若文件不存在或解析出错则返回空 Map
     */
    public static Map<String, String> parse(Path numberingFile) {
        if (!Files.exists(numberingFile)) {
            return Collections.emptyMap();
        }
        try {
            // 由统一工厂完成命名空间和 XXE 防护配置。
            Document doc = SecureXmlDocuments.parse(numberingFile);

            // 第一步：构建 abstractNumId → numFmt 的映射
            // abstractNum 定义了编号的抽象格式（如数字、圆点等），每个 abstractNum 可有多级列表（lvl），
            // 这里只取第 0 级（ilvl="0"）的 numFmt，因为大多数列表项只用第 0 级
            Map<String, String> abstractFmt = new LinkedHashMap<String, String>();
            NodeList absNodes = doc.getElementsByTagNameNS(W, "abstractNum");
            for (int i = 0; i < absNodes.getLength(); i++) {
                Element absEl = (Element) absNodes.item(i);
                String absId = absEl.getAttributeNS(W, "abstractNumId");
                if (absId.isEmpty()) continue;
                NodeList lvlNodes = absEl.getElementsByTagNameNS(W, "lvl");
                for (int j = 0; j < lvlNodes.getLength(); j++) {
                    Element lvl = (Element) lvlNodes.item(j);
                    String ilvl = lvl.getAttributeNS(W, "ilvl");
                    // 只取 ilvl="0" 的级别，即列表的第一级编号格式
                    if ("0".equals(ilvl)) {
                        NodeList fmtNodes = lvl.getElementsByTagNameNS(W, "numFmt");
                        if (fmtNodes.getLength() > 0) {
                            String fmt = ((Element) fmtNodes.item(0)).getAttributeNS(W, "val");
                            if (!fmt.isEmpty()) {
                                abstractFmt.put(absId, fmt);
                            }
                        }
                        // 找到第 0 级后即可停止遍历该 abstractNum 的后续级别
                        break;
                    }
                }
            }

            // 第二步：构建 numId → numFmt 的映射
            // num 是文档段落实际引用的编号实例，通过 abstractNumId 引用 abstractNum 的格式定义。
            // 这一解析将间接引用转换为直接映射，使消费者无需关心 abstractNum 层。
            Map<String, String> result = new LinkedHashMap<String, String>();
            NodeList numNodes = doc.getElementsByTagNameNS(W, "num");
            for (int i = 0; i < numNodes.getLength(); i++) {
                Element numEl = (Element) numNodes.item(i);
                String numId = numEl.getAttributeNS(W, "numId");
                if (numId.isEmpty()) continue;
                // 取 num 元素下的 abstractNumId 引用值
                NodeList refNodes = numEl.getElementsByTagNameNS(W, "abstractNumId");
                if (refNodes.getLength() > 0) {
                    String refId = ((Element) refNodes.item(0)).getAttributeNS(W, "val");
                    // 通过 abstractNumId 在第一步的映射中查找对应的 numFmt
                    if (abstractFmt.containsKey(refId)) {
                        result.put(numId, abstractFmt.get(refId));
                    }
                }
            }
            return result;
        } catch (Exception e) {
            // 解析失败时返回空映射，避免因编号定义缺失导致整个转换流程中断
            return Collections.emptyMap();
        }
    }
}

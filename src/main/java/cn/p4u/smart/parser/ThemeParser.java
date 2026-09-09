package cn.p4u.smart.parser;

import cn.p4u.smart.model.ThemeDef;
import cn.p4u.smart.model.ThemeDef.ColorScheme;
import cn.p4u.smart.model.ThemeDef.FontScheme;
import org.w3c.dom.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * OOXML 主题文件（theme1.xml）解析器，负责从 .docx 包内的主题文件中提取颜色方案和字体方案。
 * <p>
 * 核心职责：
 * <ul>
 *   <li>解析主题文件中的 &lt;a:clrScheme&gt; 元素，提取 12 个标准主题颜色（dk1/lt1/dk2/lt2/accent1-6/hlink/folHlink）</li>
 *   <li>解析主题文件中的 &lt;a:fontScheme&gt; 元素，提取主/辅字体方案的西文、东亚、复杂脚本字体及脚本覆盖</li>
 *   <li>构造不可变的 {@link ThemeDef} 对象供下游 {@link DocumentParser} 使用</li>
 * </ul>
 * <p>
 * 主要使用场景：{@link DocumentParser} 在解析样式时，通过本解析器获取主题定义，
 * 再调用 {@link ThemeDef} 上的方法将主题引用（如 themeColor="accent1"）解析为具体颜色值。
 */
public final class ThemeParser {

    /** DrawingML 命名空间 URI，主题文件中所有元素均属于此命名空间 */
    private static final String A = "http://schemas.openxmlformats.org/drawingml/2006/main";

    private ThemeParser() {}

    /**
     * 解析指定路径的主题文件，构建 {@link ThemeDef} 对象。
     * <p>
     * 如果主题文件不存在或解析过程中出现任何异常，返回一个颜色方案和字体方案均为 null 的
     * {@link ThemeDef}，保证调用方不会因缺少主题文件而中断。
     *
     * @param themeFile 主题文件的路径（通常为解压后 word/theme/theme1.xml），Path 类型
     * @return 解析得到的 {@link ThemeDef}，不会为 null；解析失败时颜色和字体方案字段为 null
     */
    public static ThemeDef parse(Path themeFile) {
        // 文件不存在时返回空的 ThemeDef，而非抛异常，保证管线健壮性
        if (!Files.exists(themeFile)) {
            return new ThemeDef(null, null);
        }
        try {
            // 由统一工厂完成命名空间和 XXE 防护配置。
            Document doc = SecureXmlDocuments.parse(themeFile);

            // 分别解析颜色方案和字体方案
            ColorScheme colors = parseColorScheme(doc);
            FontScheme fonts = parseFontScheme(doc);
            return new ThemeDef(colors, fonts);
        } catch (Exception e) {
            // 解析失败时静默降级，返回空的 ThemeDef，避免因主题文件格式问题导致转换中断
            return new ThemeDef(null, null);
        }
    }

    /**
     * 从主题 DOM 文档中解析颜色方案。
     * <p>
     * 查找 &lt;a:clrScheme&gt; 元素，逐个提取 12 个标准颜色槽位的值。
     * 如果文档中不存在 clrScheme 元素，返回 null。
     *
     * @param doc 已解析的 DOM 文档对象，Document 类型
     * @return 包含 12 个主题颜色的 {@link ColorScheme}，无颜色方案时返回 null
     */
    private static ColorScheme parseColorScheme(Document doc) {
        NodeList clrNodes = doc.getElementsByTagNameNS(A, "clrScheme");
        if (clrNodes.getLength() == 0) return null;
        Element clrEl = (Element) clrNodes.item(0);

        // 依次提取 12 个标准颜色槽位，顺序与 ColorScheme 构造器的参数顺序一致
        return new ColorScheme(
                resolveColor(clrEl, "dk1"),
                resolveColor(clrEl, "lt1"),
                resolveColor(clrEl, "dk2"),
                resolveColor(clrEl, "lt2"),
                resolveColor(clrEl, "accent1"),
                resolveColor(clrEl, "accent2"),
                resolveColor(clrEl, "accent3"),
                resolveColor(clrEl, "accent4"),
                resolveColor(clrEl, "accent5"),
                resolveColor(clrEl, "accent6"),
                resolveColor(clrEl, "hlink"),
                resolveColor(clrEl, "folHlink"));
    }

    /**
     * 从颜色方案元素中解析指定名称的颜色槽位值。
     * <p>
     * 每个颜色槽位可能包含两种子元素之一：
     * <ul>
     *   <li>&lt;a:sysClr&gt; — 系统颜色引用，优先使用 lastClr 属性作为实际颜色值</li>
     *   <li>&lt;a:srgbClr&gt; — 标准 RGB 颜色，直接取 val 属性</li>
     * </ul>
     *
     * @param clrScheme 颜色方案的根元素（&lt;a:clrScheme&gt;），Element 类型
     * @param name      颜色槽位名称（如 "dk1"、"accent1"、"hlink"），String 类型
     * @return 十六进制颜色值字符串（如 "5B9BD5"），未找到时返回 null
     */
    private static String resolveColor(Element clrScheme, String name) {
        NodeList nodes = clrScheme.getElementsByTagNameNS(A, name);
        if (nodes.getLength() == 0) return null;
        Element container = (Element) nodes.item(0);

        // 优先尝试系统颜色：<a:sysClr val="windowText" lastClr="000000"/>
        NodeList sysClrNodes = container.getElementsByTagNameNS(A, "sysClr");
        if (sysClrNodes.getLength() > 0) {
            Element sysEl = (Element) sysClrNodes.item(0);
            // lastClr 是系统颜色在主题中的推荐值，比 val 更可靠
            String lastClr = sysEl.getAttribute("lastClr");
            if (!lastClr.isEmpty()) return lastClr;
            return null;
        }

        // 其次尝试标准 RGB 颜色：<a:srgbClr val="5B9BD5"/>
        NodeList srgbNodes = container.getElementsByTagNameNS(A, "srgbClr");
        if (srgbNodes.getLength() > 0) {
            String val = ((Element) srgbNodes.item(0)).getAttribute("val");
            if (!val.isEmpty()) return val;
        }
        return null;
    }

    /**
     * 从主题 DOM 文档中解析字体方案。
     * <p>
     * 查找 &lt;a:fontScheme&gt; 元素，提取主字体（majorFont，标题用）和辅字体（minorFont，正文用）
     * 的西文、东亚、复杂脚本字体名及脚本覆盖映射。
     * 如果文档中不存在 fontScheme 元素，返回 null。
     *
     * @param doc 已解析的 DOM 文档对象，Document 类型
     * @return 包含主/辅字体方案的 {@link FontScheme}，无字体方案时返回 null
     */
    private static FontScheme parseFontScheme(Document doc) {
        NodeList fontNodes = doc.getElementsByTagNameNS(A, "fontScheme");
        if (fontNodes.getLength() == 0) return null;
        Element fontEl = (Element) fontNodes.item(0);

        // majorFont 为标题字体方案，minorFont 为正文字体方案
        NodeList majorNodes = fontEl.getElementsByTagNameNS(A, "majorFont");
        NodeList minorNodes = fontEl.getElementsByTagNameNS(A, "minorFont");

        Element majorEl = majorNodes.getLength() > 0 ? (Element) majorNodes.item(0) : null;
        Element minorEl = minorNodes.getLength() > 0 ? (Element) minorNodes.item(0) : null;

        // 分别提取三类字体名和脚本覆盖，构造 FontScheme
        return new FontScheme(
                getLatin(majorEl), getEastAsia(majorEl), getCs(majorEl),
                getLatin(minorEl), getEastAsia(minorEl), getCs(minorEl),
                getScriptOverrides(majorEl),
                getScriptOverrides(minorEl));
    }

    /**
     * 从字体元素中提取西文字体名（&lt;a:latin&gt; 的 typeface 属性）。
     *
     * @param fontEl 字体元素（&lt;a:majorFont&gt; 或 &lt;a:minorFont&gt;），可为 null
     * @return 西文字体名（如 "Calibri"），未找到时返回 null
     */
    private static String getLatin(Element fontEl) {
        if (fontEl == null) return null;
        NodeList nodes = fontEl.getElementsByTagNameNS(A, "latin");
        if (nodes.getLength() > 0) {
            String val = ((Element) nodes.item(0)).getAttribute("typeface");
            if (!val.isEmpty()) return val;
        }
        return null;
    }

    /**
     * 从字体元素中提取东亚字体名（&lt;a:ea&gt; 的 typeface 属性）。
     *
     * @param fontEl 字体元素（&lt;a:majorFont&gt; 或 &lt;a:minorFont&gt;），可为 null
     * @return 东亚字体名（如 "微软雅黑"），未找到时返回 null
     */
    private static String getEastAsia(Element fontEl) {
        if (fontEl == null) return null;
        NodeList nodes = fontEl.getElementsByTagNameNS(A, "ea");
        if (nodes.getLength() > 0) {
            String val = ((Element) nodes.item(0)).getAttribute("typeface");
            if (!val.isEmpty()) return val;
        }
        return null;
    }

    /**
     * 从字体元素中提取复杂脚本字体名（&lt;a:cs&gt; 的 typeface 属性）。
     * <p>
     * 复杂脚本字体用于阿拉伯语、希伯来语等从右向左书写的语言。
     *
     * @param fontEl 字体元素（&lt;a:majorFont&gt; 或 &lt;a:minorFont&gt;），可为 null
     * @return 复杂脚本字体名，未找到时返回 null
     */
    private static String getCs(Element fontEl) {
        if (fontEl == null) return null;
        NodeList nodes = fontEl.getElementsByTagNameNS(A, "cs");
        if (nodes.getLength() > 0) {
            String val = ((Element) nodes.item(0)).getAttribute("typeface");
            if (!val.isEmpty()) return val;
        }
        return null;
    }

    /**
     * 从字体元素中提取脚本覆盖（script override）映射。
     * <p>
     * 主题文件中 &lt;a:majorFont&gt; / &lt;a:minorFont&gt; 下可以有多个 &lt;a:font&gt; 子元素，
     * 每个通过 script 属性指定适用的书写系统（如 "Hans" 表示简体中文），
     * typeface 属性指定该脚本对应的字体名。这些覆盖优先于默认的西文/东亚/复杂脚本字体。
     *
     * @param fontEl 字体元素（&lt;a:majorFont&gt; 或 &lt;a:minorFont&gt;），可为 null
     * @return 脚本覆盖的不可变 Map（key 为 script 值，value 为 typeface 值），
     *         fontEl 为 null 时返回空 Map
     */
    private static Map<String, String> getScriptOverrides(Element fontEl) {
        if (fontEl == null) return Collections.emptyMap();
        HashMap<String, String> overrides = new HashMap<String, String>();
        NodeList nodes = fontEl.getElementsByTagNameNS(A, "font");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element f = (Element) nodes.item(i);
            String script = f.getAttribute("script");
            String typeface = f.getAttribute("typeface");
            // 只记录同时具备 script 和 typeface 的有效条目
            if (!script.isEmpty() && !typeface.isEmpty()) {
                overrides.put(script, typeface);
            }
        }
        // 返回不可变视图，防止外部修改，保证 ThemeDef 的不可变性
        return Collections.unmodifiableMap(overrides);
    }
}

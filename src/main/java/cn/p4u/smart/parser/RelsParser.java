package cn.p4u.smart.parser;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;
import org.w3c.dom.*;

/**
 * OOXML 关系文件（.rels）解析器。
 *
 * <p>核心职责：解析 .docx 包中的 _rels/.rels 等 .rels 文件，
 * 提取 Relationship 元素的 Id、Target 和 Type 属性，
 * 构建以 rId 为键的关系映射表。</p>
 *
 * <p>主要使用场景：DocumentParser 在解析主文档、页眉页脚等部件时，
 * 通过本类查找图片引用、超链接目标等关系信息；
 * 也可用于解析 document.xml.rels 等子部件的关系文件。</p>
 */
public final class RelsParser {

    private static final Logger LOG = Logger.getLogger(RelsParser.class.getName());

    /**
     * 关系条目，表示一个 &lt;Relationship&gt; 元组。
     *
     * <p>包含目标路径（Target）和关系类型简称（Type），
     * 以 rId 为键存入映射表供后续查找使用。</p>
     */
    public static final class Rel {

        /** 关系目标路径，相对于当前 .rels 文件所在目录 */
        private final String target;
        /** 关系类型简称，从完整 URI 中提取的最后一段 */
        private final String type;

        /**
         * 构造关系条目。
         *
         * @param target 目标路径（如 "media/image1.png"）
         * @param type   关系类型简称（如 "image"）
         */
        public Rel(String target, String type) {
            this.target = target;
            this.type = type;
        }

        /**
         * 获取关系目标路径。
         *
         * @return 目标路径字符串
         */
        public String target() { return target; }

        /**
         * 获取关系类型简称。
         *
         * @return 类型简称字符串
         */
        public String type() { return type; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Rel)) return false;
            Rel that = (Rel) o;
            return Objects.equals(target, that.target)
                    && Objects.equals(type, that.type);
        }

        @Override
        public int hashCode() {
            return Objects.hash(target, type);
        }

        @Override
        public String toString() {
            return "Rel[target=" + target + ", type=" + type + "]";
        }
    }

    /** 私有构造，工具类禁止实例化 */
    private RelsParser() {}

    /**
     * 解指定的 .rels 文件，返回以 rId 为键的关系映射表。
     *
     * <p>如果文件不存在或解析失败，返回空映射表而非 null，
     * 以便调用方安全遍历而无需空检查。</p>
     *
     * @param relsFile .rels 文件路径（如 _rels/.rels 或 word/_rels/document.xml.rels）
     * @return 不可变的关系映射表，键为 rId（如 "rId1"），值为 {@link Rel}；解析失败时返回空映射表
     */
    public static Map<String, Rel> parse(Path relsFile) {
        // 文件不存在时直接返回空映射，避免后续抛异常
        if (!java.nio.file.Files.exists(relsFile)) {
            LOG.warning("Relationships file not found: " + relsFile);
            return Collections.emptyMap();
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            // 禁止 DOCTYPE 声明，防止 XXE 攻击
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            // 启用安全处理，限制 XML 解析的资源消耗
            factory.setFeature("http://javax.xml.XMLConstants/feature/secure-processing", true);
            javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
            // 设置空 EntityResolver，阻止外部实体解析，进一步防御 XXE
            builder.setEntityResolver(new org.xml.sax.EntityResolver() {
                @Override
                public org.xml.sax.InputSource resolveEntity(String publicId, String systemId) {
                    return new org.xml.sax.InputSource(new java.io.StringReader(""));
                }
            });
            Document doc = builder.parse(relsFile.toFile());

            Map<String, Rel> rels = new HashMap<String, Rel>();
            // 提取所有 <Relationship> 元素
            NodeList nodes = doc.getElementsByTagName("Relationship");
            for (int i = 0; i < nodes.getLength(); i++) {
                Element el = (Element) nodes.item(i);
                String id = el.getAttribute("Id");
                String target = el.getAttribute("Target");
                // 完整的 Type 是长 URI，如 http://schemas.openxmlformats.org/officeDocument/2006/relationships/image
                String fullType = el.getAttribute("Type");
                // 将完整 URI 截取为短名称，如 "image"，便于后续按类型筛选
                String type = shortType(fullType);
                rels.put(id, new Rel(target, type));
            }
            // 返回不可变视图，防止调用方意外修改
            return Collections.unmodifiableMap(rels);
        } catch (Exception e) {
            LOG.warning("Failed to parse relationships: " + e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * 从完整的关系类型 URI 中提取短名称。
     *
     * <p>例如将
     * "http://schemas.openxmlformats.org/officeDocument/2006/relationships/image"
     * 截取为 "image"。</p>
     *
     * @param fullType 完整的关系类型 URI
     * @return URI 最后一个 '/' 之后的部分；若无 '/' 则原样返回
     */
    private static String shortType(String fullType) {
        // 取最后一个 '/' 的位置，截取其后的短类型名
        int slash = fullType.lastIndexOf('/');
        return slash >= 0 ? fullType.substring(slash + 1) : fullType;
    }
}

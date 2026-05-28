package cn.p4u.smart.parser;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;
import org.w3c.dom.*;

public final class RelsParser {

    private static final Logger LOG = Logger.getLogger(RelsParser.class.getName());

    public record Rel(String target, String type) {}

    private RelsParser() {}

    public static Map<String, Rel> parse(Path relsFile) {
        if (!java.nio.file.Files.exists(relsFile)) {
            LOG.warning("Relationships file not found: " + relsFile);
            return Map.of();
        }
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://javax.xml.XMLConstants/feature/secure-processing", true);
            var builder = factory.newDocumentBuilder();
            builder.setEntityResolver((publicId, systemId) -> new org.xml.sax.InputSource(new java.io.StringReader("")));
            var doc = builder.parse(relsFile.toFile());
            var rels = new HashMap<String, Rel>();
            var nodes = doc.getElementsByTagName("Relationship");
            for (int i = 0; i < nodes.getLength(); i++) {
                var el = (Element) nodes.item(i);
                String id = el.getAttribute("Id");
                String target = el.getAttribute("Target");
                String fullType = el.getAttribute("Type");
                String type = shortType(fullType);
                rels.put(id, new Rel(target, type));
            }
            return Collections.unmodifiableMap(rels);
        } catch (Exception e) {
            LOG.warning("Failed to parse relationships: " + e.getMessage());
            return Map.of();
        }
    }

    private static String shortType(String fullType) {
        int slash = fullType.lastIndexOf('/');
        return slash >= 0 ? fullType.substring(slash + 1) : fullType;
    }
}

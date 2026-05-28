package cn.p4u.smart.model;

import java.util.List;
import java.util.Map;

public record DocumentModel(Map<String, StyleDef> styles, List<ContentBlock> content) {
}

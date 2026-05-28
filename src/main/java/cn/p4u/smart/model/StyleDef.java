package cn.p4u.smart.model;

import java.util.Map;

public record StyleDef(String styleId, String name, String basedOn,
                       Map<String, String> runProps,
                       Map<String, String> paragraphProps) {
}

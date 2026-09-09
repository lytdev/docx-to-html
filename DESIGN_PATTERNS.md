# 设计模式与代码阅读指南

本项目参考 GoF 23 种设计模式，但只使用能解决真实问题的模式。设计模式的目的不是增加类，
而是让变化集中在稳定边界之后。初学者建议按照“入口 → 解压 → 解析 → 渲染”的顺序阅读。

## 主流程

```text
DocxConverter（外观）
  └─ ExtractedDocx（自动管理临时目录）
      └─ DocumentParser（DOCX XML → DocumentModel）
          └─ HtmlRenderer（DocumentModel → HTML）
```

## 本次采用的模式

| 模式 | 代码位置 | 解决的问题 |
| --- | --- | --- |
| Facade（外观） | `DocxConverter`、`WmfConverter` | 对外提供少量稳定入口，隐藏内部步骤 |
| Builder（建造者） | `ConversionConfig.Builder` | 避免多个 String/可选参数组成难读的构造器调用 |
| Strategy（策略） | `ImageUriResolver`、`WmfRasterizer` | Base64、OSS、ImageMagick、PowerShell 可以独立替换 |
| Factory（工厂） | `WmfRasterizerFactory`、`SecureXmlDocuments` | 集中创建策略和安全 XML 解析器 |
| Chain of Responsibility（责任链） | `MathHtmlRenderer` | 按固定优先级选择公式的第一种可用渲染方式 |
| Composite（组合） | `ShapeElement.children()` | 单个形状和组合形状使用相同的树形模型 |

项目还使用了常见但不属于 GoF 23 种模式的 Null Object：
`DisabledWmfRasterizer` 表示“不执行 WMF 转换”，从而避免调用方反复判断 null。

## 为什么没有使用全部 23 种模式

模式会带来新的抽象和类。如果某个功能只有一个稳定实现，强行加入 Abstract Factory、Mediator、
Memento 等模式只会增加理解成本。本项目遵循以下判断方式：

1. 先确认哪里经常变化，例如图片存储方式、WMF 转换工具。
2. 再为变化点建立接口或工厂。
3. 对稳定且简单的代码保持直接实现。
4. 每次重构都用原有测试和真实 DOCX 验证输出行为。

## 添加新功能的推荐位置

- 新增图片存储方式：实现 `ImageUriResolver`。
- 新增 WMF 转换工具：实现 `WmfRasterizer`，并在 `WmfRasterizerFactory` 注册。
- 新增公式降级方式：在 `MathHtmlRenderer` 内增加一个 `RenderStep` 实现，并放入责任链合适位置。
- 新增 OOXML 解析入口：统一调用 `SecureXmlDocuments.parse()`，不要自行创建不安全的 DOM 解析器。
- 新增转换参数：优先加入 `ConversionConfig.Builder`，并为旧构造器保留兼容默认值。

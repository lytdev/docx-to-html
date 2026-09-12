# docx-to-html

`docx-to-html` 是一个面向 Java 21 和 Spring Boot 项目的 DOCX 转 HTML 组件。调用方传入 DOCX 文件的数据流，组件完成解压、解析和渲染后，直接返回完整的 HTML 字符串，不会主动写入 HTML 文件。

## 功能概览

- 文本：字体、字号、颜色、粗体、斜体、下划线、删除线、上下标、高亮和底纹。
- 段落：对齐、缩进、标题层级和编号列表。
- 表格：边框、底色、行列合并、内部边框和三线表等常见样式。
- 图片：普通图片、行内图片、图片说明、替代文本以及自定义图片地址。
- 公式：OMML、MathML、LaTeX、MathType/OLE 公式和公式预览图片。
- 图形：常见 DrawingML/VML 图形转 SVG。
- 后处理：图片与图注组合为 `figure`、相邻同属性 `span` 合并。
- 进度回调：同步反馈顶层段落和表格的解析进度、异常及完成状态。

## 环境要求

- JDK 21
- Maven 3.8 或更高版本
- 输入文件必须是 `.docx`，不支持旧版二进制 `.doc` 文件
- WMF/EMF 转换为可选能力，具体环境要求参见“WMF/EMF 图片转换”一节

## 引入项目

项目坐标如下：

```xml
<dependency>
    <groupId>cn.p4u.agile</groupId>
    <artifactId>docx-to-html</artifactId>
    <version>1.0.0</version>
</dependency>
```

如果依赖尚未发布到远程 Maven 仓库，可以先在源码目录执行：

```shell
mvn clean install
```

然后在 Spring Boot 项目的 `pom.xml` 中使用上述依赖坐标。

## 快速开始

最简单的调用方式会使用默认配置：图片转为 Base64，WMF/EMF 自动选择可用的转换方式。

```java
import cn.p4u.dth.converter.DocxConverter;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

try (InputStream input = Files.newInputStream(Path.of("demo.docx"))) {
    String html = DocxConverter.convert(input);
    // html 是完整的 HTML 字符串，可直接返回给前端或交给业务系统存储。
}
```

`DocxConverter` 会读取数据流，但不会关闭调用方传入的 `InputStream`，因此应由调用方负责关闭。

## 命令行调用示例
```bash
/usr/jvm/dragonwell-21/bin/java -jar docx-to-html-{version}-cli.jar /opt/tmp/demo-linux.docx
```
```bash
/usr/jvm/dragonwell-21/bin/java -jar docx-to-html-{version}-cli.jar -i /opt/tmp/demo-linux.docx --stdout > report.html
```

```bash
/usr/jvm/dragonwell-21/bin/java -jar docx-to-html-{version}-cli.jar -i /opt/tmp/demo-linux.docx -o /opt/tmp/demo-linux.html
```

```bash
/usr/jvm/dragonwell-21/bin/java -jar docx-to-html-{version}-cli.jar -i /opt/tmp/demo-linux.docx -o /opt/tmp/demo-linux.html --wmf-strategy IMAGEMAGICK --imagemagick-path /usr/bin/magick --tmp-dir /opt/tmp/
```

> 支持参数：
* `-i`, `--input` `<path>`         输入 `.docx` 文件路径（必填，也可作为位置参数）
* `-o`, `--output` `<path>`        输出 `.html` 文件路径（默认：与输入同目录同名）
* `--stdout`                       将 `HTML` 输出到标准输出而非文件
* `--wmf-strategy` `<name>`        `WMF/EMF` 转换策略：`AUTO` | `IMAGEMAGICK` | `POWERSHELL` | `NONE`（默认 `AUTO`）
* `--imagemagick-path` `<path>`    `ImageMagick` 可执行文件路径（`Linux` 上如 `/usr/bin/magick`）
* `--tmp-dir` `<path>`             临时目录根路径（默认：系统临时目录）
* `--latex-url` `<url>`            `LaTeX` 公式渲染地址模板
* `-h`, `--help`                   显示本帮助


## Spring Boot 调用示例

### 定义转换配置

```java
import cn.p4u.dth.converter.ConversionConfig;
import cn.p4u.dth.renderer.WmfConversionStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DocxConvertConfiguration {

    @Bean
    public ConversionConfig docxConversionConfig() {
        return ConversionConfig.builder()
                .tmpDir("D:/app-temp/docx")
                .wmfStrategy(WmfConversionStrategy.AUTO)
                .build();
    }
}
```

### 在业务服务中转换上传文件

```java
import cn.p4u.dth.converter.ConversionConfig;
import cn.p4u.dth.converter.DocxConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@Service
public class DocxConvertService {

    private final ConversionConfig conversionConfig;

    public DocxConvertService(ConversionConfig conversionConfig) {
        this.conversionConfig = conversionConfig;
    }

    public String convert(MultipartFile file) throws IOException {
        try (InputStream input = file.getInputStream()) {
            return DocxConverter.convert(input, conversionConfig);
        }
    }
}
```

转换是同步执行的。大文件转换如果放在 Web 请求中，应结合业务需求设置请求超时，或放入异步任务中处理。

## 转换配置

可使用 `ConversionConfig.builder()` 组合多个可选参数：

```java
import cn.p4u.dth.converter.ConversionConfig;
import cn.p4u.dth.renderer.Image2Base64Resolver;
import cn.p4u.dth.renderer.WmfConversionStrategy;

ConversionConfig config = ConversionConfig.builder()
        .imageUriResolver(new Image2Base64Resolver())
        .tmpDir("D:/app-temp/docx")
        .latexRenderUrl("https://latex.codecogs.com/svg.image?{latex}")
        .wmfStrategy(WmfConversionStrategy.IMAGEMAGICK)
        .imageMagickPath("C:/Program Files/ImageMagick-7.1.1-Q16-HDRI/magick.exe")
        .build();
```

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `imageUriResolver` | `Image2Base64Resolver` | 决定图片如何转换为 HTML 中的 URI |
| `tmpDir` | 系统临时目录 | 指定解压临时目录的根目录 |
| `latexRenderUrl` | CodeCogs SVG 地址 | 没有公式图片和 MathML 时使用的在线 LaTeX 渲染地址；可设为 `null` 禁用 |
| `wmfStrategy` | `AUTO` | 指定 WMF/EMF 的转换策略 |
| `imageMagickPath` | 空 | ImageMagick 可执行文件路径；未设置时尝试系统命令或兼容的系统属性 |

每次转换会在 `tmpDir` 下创建唯一的 `docx2html-*` 子目录。转换成功或发生异常时都会自动清理该子目录，不会删除调用方配置的根目录。

## 图片资源处理

### 默认 Base64

默认的 `Image2Base64Resolver` 将图片写入 `img` 的 Data URI，不需要额外部署图片文件：

```java
String html = DocxConverter.convert(inputStream);
```

Base64 使用方便，但会增大 HTML 字符串。如果文档图片较多，建议实现 `ImageUriResolver`，将图片复制到静态资源目录或上传到对象存储。

### 自定义图片地址

```java
import cn.p4u.dth.renderer.ImageUriResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

public class StaticImageResolver implements ImageUriResolver {

    private final Path outputDir;
    private final String publicPrefix;

    public StaticImageResolver(Path outputDir, String publicPrefix) {
        this.outputDir = outputDir;
        this.publicPrefix = publicPrefix;
    }

    @Override
    public ResolveResult resolve(Path source, String mimeType) throws IOException {
        Files.createDirectories(outputDir);

        String originalName = source.getFileName().toString();
        String suffix = originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf('.'))
                : "";
        String targetName = UUID.randomUUID() + suffix;
        Files.copy(source, outputDir.resolve(targetName), StandardCopyOption.REPLACE_EXISTING);

        return new ResolveResult(publicPrefix + "/" + targetName, mimeType);
    }
}
```

调用示例：

```java
ImageUriResolver resolver = new StaticImageResolver(
        Path.of("D:/app/static/docx-images"),
        "/docx-images"
);

String html = DocxConverter.convert(inputStream, resolver);
```

传给 `resolve` 的 `source` 位于本次转换的临时目录中。自定义实现必须在方法返回前完成复制或上传，不能把该临时路径保存下来供以后使用。

## WMF/EMF 图片转换

`WmfConversionStrategy` 支持以下策略：

| 策略 | 行为 |
| --- | --- |
| `AUTO` | 先尝试 ImageMagick，失败后在 Windows 上尝试 PowerShell/System.Drawing |
| `IMAGEMAGICK` | 使用 ImageMagick；Linux 的中文 WMF 使用下述兼容渲染分支 |
| `POWERSHELL` | 使用 Windows PowerShell 和 System.Drawing |
| `NONE` | 不转换 WMF/EMF，保留原资源交给图片解析器处理 |

`imageMagickPath` 应填写 ImageMagick 的可执行文件路径，而不是安装目录。例如：

```java
ConversionConfig config = ConversionConfig.builder()
        .wmfStrategy(WmfConversionStrategy.IMAGEMAGICK)
        .imageMagickPath("C:/Program Files/ImageMagick-7.1.1-Q16-HDRI/magick.exe")
        .build();
```

### Rocky Linux 的中文公式 WMF

当 `AUTO` 选中 ImageMagick 或指定 `IMAGEMAGICK` 时，仅在 Linux 上检测 WMF 字体对象。
含 `charset=134` 或 `136` 的 WMF 使用 `poi-scratchpad 5.5.1` 和 Java2D 渲染为透明 PNG，
分别按 CP936/GBK、Big5 解码 `TextOut`/`ExtTextOut`。DBCS 的逐字节 Dx 间距合并为逐字符间距。
带 Dx 的文本逐字符按逻辑坐标定位，并按全部 Dx 的总和更新绘图位置，支持 MathType 将分散的
字母、运算符放在同一条记录及连续输出中文下标的公式，避免字体 tracking 近似导致错位。
原 WMF 字节不会改写；Windows、EMF 和不含中文字符集的 WMF 继续使用原来的转换路径。
这也意味着单独在命令行执行 `magick input.wmf output.png` 不会获得本项目的兼容处理。

Rocky Linux 的应用运行环境（容器部署时为容器内部）需要 `fontconfig`、`freetype` 及中文字体，
例如 Noto Sans CJK SC 或有使用许可的宋体。安装字体后刷新缓存、重启 Java 进程，并检查：

```bash
fc-cache -fv
fc-list :lang=zh family
```

默认优先使用原字体，缺少中文字形时使用 Java `Dialog` 逻辑字体回退。
也可通过 JVM 参数指定已安装的字体族（不是字体文件路径）：

```text
-Djava.awt.headless=true -Ddocx2html.wmf.cjkFont="Noto Sans CJK SC"
```

如果字体仍不能显示实际文字，转换会记录缺字原因并失败，遵循原有图片解析器降级行为。
该分支只处理明确声明中文字符集的 WMF，不猜测 ANSI/DEFAULT 的编码；
含字形索引或 PDY 等暂不支持的文本标志时会报告错误。复杂公式仍应使用实际文件核对布局，
尤其需安装其使用的数学符号字体。

## 解析进度回调

三参数 `convert` 方法支持传入 `FileParseCallback<ContentBlock>`。回调与转换过程在同一线程同步执行，适合更新任务进度、记录日志或向业务层发送进度事件。

```java
import cn.p4u.dth.converter.CallBackRecord;
import cn.p4u.dth.converter.ConversionConfig;
import cn.p4u.dth.converter.DocxConverter;
import cn.p4u.dth.converter.FileParseCallback;
import cn.p4u.dth.model.ContentBlock;

FileParseCallback<ContentBlock> callback = new FileParseCallback<>() {
    @Override
    public void onLineParsed(
            int count,
            int total,
            CallBackRecord<ContentBlock> record) {
        System.out.printf("解析进度：%d/%d，当前类型：%s%n",
                count, total, record.getType());
    }

    @Override
    public void onError(Exception ex, int line) {
        System.err.printf("第 %d 项解析失败：%s%n", line, ex.getMessage());
    }

    @Override
    public void onComplete(int total, String message) {
        System.out.printf("%s，共解析 %d 项%n", message, total);
    }
};

String html = DocxConverter.convert(
        inputStream,
        ConversionConfig.defaults(),
        callback
);
```

进度统计规则：

- DOCX 正文中的每个顶层段落 `w:p` 计为一项。
- 每个顶层表格 `w:tbl` 计为一项，表格内部的段落不重复计数。
- `sectPr` 等非正文结构不计入 `total`。
- `count` 从 1 开始递增，成功解析一项后调用一次 `onLineParsed`。
- `record.getType()` 当前为 `paragraph` 或 `table`，`record.getData()` 分别对应 `ParagraphBlock` 或 `TableBlock`。
- 单项解析异常的 `line` 从 1 开始；解压或文档结构异常无法定位到具体项时，`line` 为 0。
- 只有解析和 HTML 渲染全部成功后才调用 `onComplete`。

回调重点反映文档结构的解析进度，不代表图片上传、外部 WMF 转换等附加操作各自的内部进度。

## 常用调用方法

| 调用方法 | 用途 |
| --- | --- |
| `convert(InputStream)` | 使用全部默认配置 |
| `convert(InputStream, FileParseCallback)` | 默认配置并监听进度 |
| `convert(InputStream, ConversionConfig)` | 使用完整自定义配置 |
| `convert(InputStream, ConversionConfig, FileParseCallback)` | 自定义配置并监听进度 |
| `convert(InputStream, ImageUriResolver)` | 只替换图片资源处理方式 |
| `convert(InputStream, ImageUriResolver, FileParseCallback)` | 自定义图片处理并监听进度 |
| `convert(InputStream, ImageUriResolver, String)` | 自定义图片处理和临时目录 |
| `convert(InputStream, ImageUriResolver, String, FileParseCallback)` | 自定义图片处理、临时目录并监听进度 |

所有方法均返回 `String`，内容是带 `DOCTYPE`、`head` 和 `body` 的完整 HTML 文档。

## HTML 输出约定

- 普通块级图片：`class="image-block image-item" data-type="image"`。
- 与同一父元素中的文字混排的嵌入型图片：`class="image-inline image-item" data-type="image"`。
- 公式预览图片：`class="formula-item formula-image" data-type="formula"`。
- 可提取 LaTeX 的公式图片会包含 `data-latex` 属性。
- 图片存在图注时输出 `figure > img + figcaption`，`figcaption` 中只保留纯文本。
- 图片存在名称、别名或图注时，会为 `img` 生成合适的 `alt` 属性；图注优先。
- 相邻且 `style`、`id`、`class` 完全相同的 `span` 会合并。

## 项目框架

项目采用“解压、解析、渲染”三阶段流水线，每一阶段都有明确职责：

```text
InputStream
    │
    ▼
DocxConverter                 对外统一入口，组织完整转换流程
    │
    ▼
DocxExtractor                 安全解压 DOCX 到临时目录
    │
    ▼
DocumentParser                读取 OOXML，构建中间文档模型
    │
    ▼
DocumentModel                 与 HTML 无关的结构化模型
    │
    ▼
HtmlRenderer                  将模型渲染并进行 HTML 后处理
    │
    ▼
HTML String
```

### 主要包结构

| 包名 | 职责 |
| --- | --- |
| `cn.p4u.dth.converter` | 对外门面、转换配置、进度回调和 WMF 策略选择 |
| `cn.p4u.dth.extractor` | DOCX ZIP 解压、临时目录生命周期和路径安全校验 |
| `cn.p4u.dth.parser` | 文档、样式、主题、编号、关系、公式和图形解析 |
| `cn.p4u.dth.model` | 段落、文本、图片、公式、表格和图形等中间模型 |
| `cn.p4u.dth.renderer` | HTML、CSS、图片 URI、公式以及 DOM 后处理 |

### 中间模型

```text
DocumentModel
├── ParagraphBlock
│   ├── TextRun
│   ├── HyperlinkElement
│   ├── ImageElement
│   ├── MathElement
│   └── ShapeElement
└── TableBlock
    └── TableRow
        └── TableCell
            └── ParagraphBlock
```

解析器先把 DOCX 内容转换成与展示方式无关的 `DocumentModel`，渲染器再生成 HTML。这种分层使公式、图片、样式和表格逻辑可以分别测试和维护。

### 主要设计和扩展点

- `DocxConverter`：门面模式，对调用方隐藏解压、解析和渲染细节。
- `ConversionConfig.Builder`：构建器模式，集中管理可选配置。
- `ImageUriResolver`：策略模式，可替换 Base64、本地静态资源或对象存储实现。
- `WmfRasterizer`：策略模式，不同环境使用不同的 WMF/EMF 转换器。
- `WmfRasterizerFactory`：工厂模式，根据配置选择转换实现。
- `MathHtmlRenderer`：责任链模式，依次尝试公式图片、MathML、在线 LaTeX 和文本回退。
- `FigureCaptionProcessor`：识别相邻图注并生成语义化 `figure`。
- `AdjacentSpanProcessor`：合并属性一致的相邻 `span`，精简输出 HTML。
- `SecureXmlDocuments`：集中创建安全的 XML 解析器，禁用外部实体和外部 DTD。

更多面向初学者的设计模式说明见 [DESIGN_PATTERNS.md](DESIGN_PATTERNS.md)。

## 异常、资源与并发

- `convert` 发生异常时会向调用方抛出异常，不会返回半成品 HTML。
- 使用回调时，异常会先触发 `onError`，之后仍由 `convert` 抛给调用方处理。
- 调用方负责关闭传入的 `InputStream`。
- 解压产生的临时子目录由转换器自动清理。
- `ConversionConfig` 可安全复用；自定义 `ImageUriResolver` 是否支持并发取决于其自身实现。
- 回调对象如果会被多线程任务共享，应由业务方保证线程安全。

## 构建与测试

先确认 Maven 使用 JDK 21：

```shell
mvn -version
```

常用命令：

```shell
# 编译
mvn compile

# 运行全部单元测试
mvn test

# 打包 JAR
mvn package
```

构建结果为：

```text
target/docx-to-html-1.0.0.jar
```

## 已知限制

- 只支持 OOXML `.docx`，不支持旧版 `.doc`。
- MathType/OLE 的 LaTeX 是从 MTEF 数据重建的，不一定等同于作者最初输入的 LaTeX；不支持或数据不一致时会保留公式预览图片。
- WMF/EMF 的转换结果依赖所选策略以及运行环境中可用的 ImageMagick 或 Windows 图形能力。
- 未识别的自定义图形预设会使用矩形作为回退显示。
- 在线 LaTeX 渲染地址只生成图片 URL，实际加载时需要客户端能够访问对应服务；离线场景可设为 `null` 或使用自有服务。

## License

项目的 Maven 元数据声明使用 [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)。

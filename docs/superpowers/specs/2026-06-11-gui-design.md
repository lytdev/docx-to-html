# GUI 界面设计规格

## 概述

为 docxToHtml4j 添加 Swing GUI 界面，用户通过按钮选择 .docx 文件，转换后 HTML 输出到同目录。GUI 可通过 GraalVM Native Image 打包为 Windows exe。

## 架构

新增 `cn.p4u.dth.gui.GuiRunner` 类，与 `CliRunner` 平级。`GuiRunner` 继承 `JFrame`，内部调用 `DocxConverter.convert()` ——不对现有管道代码做任何修改。

```
GuiRunner (JFrame)
  ├── 文件选择: JFileChooser (filter=.docx)
  ├── 路径显示: JTextField (不可编辑)
  ├── 浏览按钮: JButton
  ├── 进度条:   JProgressBar (indeterminate 模式)
  ├── 状态标签: JLabel (正常/错误信息)
  └── 转换按钮: JButton
```

转换在 `SwingWorker` 中执行，避免阻塞 EDT。`SwingWorker` 的 `done()` 方法中处理成功/失败。

## 交互流程

### 状态转换表

| 状态 | 路径框 | 浏览按钮 | 转换按钮 | 进度条 | 状态区 |
|------|--------|----------|----------|--------|--------|
| 初始 | "未选择文件" | 启用 | 禁用 | 隐藏 | 空 |
| 已选文件 | 显示路径 | 启用 | 启用 | 隐藏 | 空 |
| 转换中 | 显示路径 | 禁用 | 禁用 | 显示(indeterminate) | "转换中..." |
| 成功 | 显示路径 | 启用 | 禁用 | 隐藏 | 弹窗提示 |
| 失败 | 显示路径 | 启用 | 启用 | 隐藏 | 行内红色错误 |

### 操作步骤

1. 用户点击"浏览..."按钮 → 弹出 JFileChooser（过滤 .docx）
2. 选择文件后路径显示在路径框中，"转换"按钮启用
3. 用户点击"转换"按钮 → 浏览和转换按钮禁用，进度条显示（indeterminate），状态标签显示"转换中..."
4. SwingWorker 在后台线程调用 `DocxConverter.convert(inputPath, config)`
5. 完成后：
   - **成功**：弹出 JDialog，含输出路径和"打开文件位置"按钮
   - **失败**：行内显示红色错误信息，恢复按钮状态允许重试

## 成功弹窗

自定义 `JDialog`（非 JOptionPane，因需"打开文件位置"按钮）：
- 标题："转换完成"
- 信息："输出文件: {absolutePath}"
- 按钮1："打开文件位置" — 调用 `Desktop.open()` 打开文件所在目录
- 按钮2："确定" — 关闭弹窗，主窗口回到已选文件状态（转换按钮禁用）

## 错误处理

- 行内红色 `JLabel`，显示 `DocxConversionException.getMessage()`
- 文件不存在或非 .docx 后缀：在浏览选择时就拦截提示，不进入转换流程
- 转换失败后恢复浏览和转换按钮为启用状态，允许重新选择或重试

## 输出路径规则

输入 `C:\Docs\report.docx` → 输出 `C:\Docs\report.html`。如同名文件已存在则直接覆盖。

## 转换配置

使用 `ConversionConfig.base64Defaults()` —— 图片以 base64 内嵌，无需处理外部图片目录。不向用户暴露任何配置项。

## GraalVM Native Image 打包

新增 Maven profile `native`，使用 `org.graalvm.buildtools:native-maven-plugin`：
- 输出：`target/docx2html.exe`
- mainClass：`cn.p4u.dth.gui.GuiRunner`
- 需要 reachability metadata 配置文件：
  - `reflect-config.json`（Swing 类反射）
  - `resource-config.json`（图标等资源）
- Windows 专属参数：`-H:windowsApiType=gui` 避免弹出控制台窗口
- 打包命令：`mvn -Pnative package`

## 窗口属性

- 标题："docx → HTML 转换器"
- 大小：480×220，不可缩放 (`setResizable(false)`)
- 居中显示 (`setLocationRelativeTo(null)`)
- 系统原生 Look & Feel (`UIManager.getSystemLookAndFeelClassName()`)
- 关闭操作：`EXIT_ON_CLOSE`

## 文件清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `src/main/java/cn/p4u/dth/gui/GuiRunner.java` | 新增 | Swing 主窗口，含所有 GUI 逻辑 |
| `src/main/resources/META-INF/native-image/reflect-config.json` | 新增 | GraalVM 反射配置 |
| `src/main/resources/META-INF/native-image/resource-config.json` | 新增 | GraalVM 资源配置 |
| `pom.xml` | 修改 | 新增 `native` Maven profile |

## 约束

- 保持 Java 8 兼容：不使用 lambda、stream、var 等 JDK 9+ 特性
- 不新增运行时依赖（Swing 为 JDK 内置）
- 不修改现有 DocxConverter / HtmlRenderer 等管道代码
- GraalVM Native Image 打包为可选功能，不影响正常 `mvn compile` / `mvn test`

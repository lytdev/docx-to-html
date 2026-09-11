# GUI Runner 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 docxToHtml4j 添加 Swing GUI 界面，经典表单布局，可打包为 GraalVM Native Image exe。

**Architecture:** 新增 `cn.p4u.dth.gui.GuiRunner` 类继承 JFrame，内部使用 SwingWorker 执行转换。输出路径计算抽取为可测试的静态方法。GraalVM 打包通过 Maven profile `native` 实现，不影响常规构建。

**Tech Stack:** Java 8 Swing, SwingWorker, JFileChooser, Desktop.open(), GraalVM Native Maven Plugin

---

### Task 1: 输出路径计算逻辑

**Files:**
- Create: `src/main/java/cn/p4u/dth/gui/GuiRunner.java`
- Create: `src/test/java/cn/p4u/dth/gui/GuiRunnerTest.java`

这段逻辑是纯函数，独立于 GUI 组件，先写测试确保正确性。

- [ ] **Step 1: 写失败测试**

```java
package cn.p4u.dth.gui;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class GuiRunnerTest {

    @Test
    void deriveOutputPath_changesExtensionToHtml() {
        Path input = Paths.get("C:\\Docs\\report.docx");
        Path output = GuiRunner.deriveOutputPath(input);
        assertEquals(Paths.get("C:\\Docs\\report.html"), output);
    }

    @Test
    void deriveOutputPath_handlesUppercaseExtension() {
        Path input = Paths.get("C:\\Docs\\REPORT.DOCX");
        Path output = GuiRunner.deriveOutputPath(input);
        assertEquals(Paths.get("C:\\Docs\\REPORT.html"), output);
    }

    @Test
    void deriveOutputPath_handlesMultipleDots() {
        Path input = Paths.get("C:\\Docs\\my.file.v2.docx");
        Path output = GuiRunner.deriveOutputPath(input);
        assertEquals(Paths.get("C:\\Docs\\my.file.v2.html"), output);
    }

    @Test
    void deriveOutputPath_preservesParentDirectory() {
        Path input = Paths.get("report.docx");
        Path output = GuiRunner.deriveOutputPath(input);
        assertEquals(Paths.get("report.html"), output);
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd C:\DevCode\agile-hub\docxToHtml4j && set JAVA_HOME=C:\DevRepo\jdk\jdk-21 && mvn test -Dtest=GuiRunnerTest -q`
Expected: FAIL — `GuiRunner` 类不存在

- [ ] **Step 3: 写最小实现**

```java
package cn.p4u.dth.gui;

import java.nio.file.Path;

/**
 * Swing GUI 入口类，提供 .docx → HTML 转换的图形界面。
 * <p>
 * 核心职责：展示文件选择、转换进度、成功/失败反馈；
 * 通过 DocxConverter.convert() 执行实际转换。
 * <p>
 * 主要使用场景：双击 exe 或 jar 启动的桌面应用入口。
 */
public class GuiRunner {

    private GuiRunner() {}

    /**
     * 根据输入 .docx 文件路径推导输出 .html 文件路径。
     * 替换文件扩展名为 .html（不区分大小写），保留同目录。
     *
     * @param docxPath 输入的 .docx 文件路径，Path 类型
     * @return 对应的 .html 输出路径，Path 类型
     */
    public static Path deriveOutputPath(Path docxPath) {
        String fileName = docxPath.getFileName().toString();
        String baseName;
        int dotIndex = fileName.toLowerCase().lastIndexOf(".docx");
        if (dotIndex > 0) {
            baseName = fileName.substring(0, dotIndex);
        } else {
            baseName = fileName;
        }
        String htmlName = baseName + ".html";
        Path parent = docxPath.getParent();
        return parent != null ? parent.resolve(htmlName) : Paths.get(htmlName);
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd C:\DevCode\agile-hub\docxToHtml4j && set JAVA_HOME=C:\DevRepo\jdk\jdk-21 && mvn test -Dtest=GuiRunnerTest -q`
Expected: PASS — 4 tests

- [ ] **Step 5: 提交**

```bash
cd C:\DevCode\agile-hub\docxToHtml4j
git add src/main/java/cn/p4u/dth/gui/GuiRunner.java src/test/java/cn/p4u/dth/gui/GuiRunnerTest.java
git commit -m "feat(gui): add deriveOutputPath logic with tests"
```

---

### Task 2: GUI 窗口框架与布局

**Files:**
- Modify: `src/main/java/cn/p4u/dth/gui/GuiRunner.java`

在 GuiRunner 中添加 JFrame 子类、窗口属性、组件布局。此步骤只搭建框架，不做事件绑定。

- [ ] **Step 1: 添加 JFrame 子类和窗口属性**

在 `GuiRunner.java` 中，将现有的私有构造函数替换为 JFrame 子类：

```java
package cn.p4u.dth.gui;

import cn.p4u.dth.converter.ConversionConfig;
import cn.p4u.dth.converter.ConversionResult;
import cn.p4u.dth.converter.DocxConverter;
import cn.p4u.dth.util.Jdk8Helpers;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Swing GUI 入口类，提供 .docx → HTML 转换的图形界面。
 * <p>
 * 核心职责：展示文件选择、转换进度、成功/失败反馈；
 * 通过 DocxConverter.convert() 执行实际转换。
 * <p>
 * 主要使用场景：双击 exe 或 jar 启动的桌面应用入口。
 */
public class GuiRunner extends JFrame {

    private static final String TITLE = "docx → HTML 转换器";
    private static final int WIDTH = 480;
    private static final int HEIGHT = 220;

    private final JTextField pathField;
    private final JButton browseButton;
    private final JButton convertButton;
    private final JProgressBar progressBar;
    private final JLabel statusLabel;

    private Path selectedFile;

    public GuiRunner() {
        // 窗口属性
        setTitle(TITLE);
        setSize(WIDTH, HEIGHT);
        setResizable(false);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // 组件初始化
        pathField = new JTextField();
        pathField.setEditable(false);
        pathField.setText("未选择文件");

        browseButton = new JButton("浏览...");
        convertButton = new JButton("转换");
        convertButton.setEnabled(false);

        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);

        statusLabel = new JLabel(" ");
        statusLabel.setForeground(Color.RED);

        // 布局
        layoutComponents();
    }

    private void layoutComponents() {
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        panel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 4, 4, 4);

        // 第一行：路径框 + 浏览按钮
        gbc.gridx = 0; gbc.gridy = 0;
        gbc.weightx = 1.0;
        panel.add(pathField, gbc);

        gbc.gridx = 1; gbc.gridy = 0;
        gbc.weightx = 0.0;
        panel.add(browseButton, gbc);

        // 第二行：进度条
        gbc.gridx = 0; gbc.gridy = 1;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        panel.add(progressBar, gbc);

        // 第三行：状态标签
        gbc.gridx = 0; gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        panel.add(statusLabel, gbc);

        // 第四行：转换按钮（右对齐）
        gbc.gridx = 0; gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(convertButton, gbc);

        setContentPane(panel);
    }

    /**
     * 根据输入 .docx 文件路径推导输出 .html 文件路径。
     * 替换文件扩展名为 .html（不区分大小写），保留同目录。
     *
     * @param docxPath 输入的 .docx 文件路径，Path 类型
     * @return 对应的 .html 输出路径，Path 类型
     */
    public static Path deriveOutputPath(Path docxPath) {
        String fileName = docxPath.getFileName().toString();
        String baseName;
        int dotIndex = fileName.toLowerCase().lastIndexOf(".docx");
        if (dotIndex > 0) {
            baseName = fileName.substring(0, dotIndex);
        } else {
            baseName = fileName;
        }
        String htmlName = baseName + ".html";
        Path parent = docxPath.getParent();
        return parent != null ? parent.resolve(htmlName) : Paths.get(htmlName);
    }

    /**
     * 程序入口方法，启动 GUI。
     *
     * @param args 命令行参数（未使用）
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception ignored) {
                    // 回退到默认 L&F
                }
                GuiRunner frame = new GuiRunner();
                frame.setVisible(true);
            }
        });
    }
}
```

- [ ] **Step 2: 编译确认**

Run: `cd C:\DevCode\agile-hub\docxToHtml4j && set JAVA_HOME=C:\DevRepo\jdk\jdk-21 && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: 运行现有测试确认无回归**

Run: `cd C:\DevCode\agile-hub\docxToHtml4j && set JAVA_HOME=C:\DevRepo\jdk\jdk-21 && mvn test -q`
Expected: BUILD SUCCESS — 所有测试通过

- [ ] **Step 4: 提交**

```bash
cd C:\DevCode\agile-hub\docxToHtml4j
git add src/main/java/cn/p4u/dth/gui/GuiRunner.java
git commit -m "feat(gui): add JFrame skeleton with layout components"
```

---

### Task 3: 文件选择事件绑定

**Files:**
- Modify: `src/main/java/cn/p4u/dth/gui/GuiRunner.java`

绑定浏览按钮的 ActionListener，弹出 JFileChooser（仅 .docx 过滤），选择后更新路径框和转换按钮状态。

- [ ] **Step 1: 在 GuiRunner 构造函数末尾添加事件绑定**

在 `GuiRunner()` 构造函数最末尾（`layoutComponents()` 调用之后）添加：

```java
        // 事件绑定
        bindEvents();
```

然后添加 `bindEvents()` 方法：

```java
    private void bindEvents() {
        browseButton.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                chooseFile();
            }
        });

        convertButton.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                startConversion();
            }
        });
    }

    private void chooseFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Word 文档 (*.docx)", "docx"));
        chooser.setAcceptAllFileFilterUsed(false);

        if (selectedFile != null) {
            chooser.setCurrentDirectory(selectedFile.toFile().getParentFile());
        }

        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            Path file = chooser.getSelectedFile().toPath();
            if (!file.toString().toLowerCase().endsWith(".docx")) {
                showError("请选择 .docx 格式的文件");
                return;
            }
            if (!java.nio.file.Files.exists(file)) {
                showError("文件不存在: " + file);
                return;
            }
            selectedFile = file;
            pathField.setText(file.toString());
            convertButton.setEnabled(true);
            clearError();
        }
    }

    private void showError(String message) {
        statusLabel.setText("❌ " + message);
        statusLabel.setForeground(Color.RED);
    }

    private void clearError() {
        statusLabel.setText(" ");
        statusLabel.setForeground(Color.RED);
    }
```

- [ ] **Step 2: 编译确认**

Run: `cd C:\DevCode\agile-hub\docxToHtml4j && set JAVA_HOME=C:\DevRepo\jdk\jdk-21 && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: 运行测试确认无回归**

Run: `cd C:\DevCode\agile-hub\docxToHtml4j && set JAVA_HOME=C:\DevRepo\jdk\jdk-21 && mvn test -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
cd C:\DevCode\agile-hub\docxToHtml4j
git add src/main/java/cn/p4u/dth/gui/GuiRunner.java
git commit -m "feat(gui): bind file chooser and validation logic"
```

---

### Task 4: SwingWorker 转换逻辑

**Files:**
- Modify: `src/main/java/cn/p4u/dth/gui/GuiRunner.java`

实现 `startConversion()` 方法，包含内部 SwingWorker 类。点击转换后禁用按钮、显示进度条，转换完成后处理成功/失败。

- [ ] **Step 1: 添加 startConversion 方法和 ConversionWorker 内部类**

```java
    private void startConversion() {
        if (selectedFile == null) {
            return;
        }

        convertButton.setEnabled(false);
        browseButton.setEnabled(false);
        progressBar.setVisible(true);
        clearError();
        statusLabel.setText("转换中...");
        statusLabel.setForeground(UIManager.getColor("Label.foreground"));

        SwingWorker<ConversionResult, Object> worker = new SwingWorker<ConversionResult, Object>() {
            @Override
            protected ConversionResult doInBackground() throws Exception {
                return DocxConverter.convert(selectedFile, ConversionConfig.base64Defaults());
            }

            @Override
            protected void done() {
                progressBar.setVisible(false);
                browseButton.setEnabled(true);

                try {
                    ConversionResult result = get();
                    Path outputPath = deriveOutputPath(selectedFile);
                    Jdk8Helpers.writeString(outputPath, result.html());
                    showSuccessDialog(outputPath);
                    convertButton.setEnabled(false);
                    statusLabel.setText(" ");
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    String msg = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getName();
                    showError("转换失败: " + msg);
                    convertButton.setEnabled(true);
                }
            }
        };

        worker.execute();
    }
```

注意：需要确认 `GuiRunner` 文件顶部已有所有 import：
- `javax.swing.SwingWorker` — 需手动添加
- `cn.p4u.dth.converter.ConversionConfig` — 已在上一步添加
- `cn.p4u.dth.converter.ConversionResult` — 已在上一步添加
- `cn.p4u.dth.converter.DocxConverter` — 已在上一步添加
- `cn.p4u.dth.util.Jdk8Helpers` — 已在上一步添加

添加 `import javax.swing.SwingWorker;` 到 import 区域。

- [ ] **Step 2: 编译确认**

Run: `cd C:\DevCode\agile-hub\docxToHtml4j && set JAVA_HOME=C:\DevRepo\jdk\jdk-21 && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: 手动冒烟测试**

Run: `cd C:\DevCode\agile-hub\docxToHtml4j && set JAVA_HOME=C:\DevRepo\jdk\jdk-21 && mvn exec:java -Dexec.mainClass="cn.p4u.dth.gui.GuiRunner" -q`
操作：选择项目根目录的 `demo.docx` → 点击"转换" → 确认同目录出现 `demo.html`
Expected: 转换成功，进度条显示后消失

- [ ] **Step 4: 提交**

```bash
cd C:\DevCode\agile-hub\docxToHtml4j
git add src/main/java/cn/p4u/dth/gui/GuiRunner.java
git commit -m "feat(gui): add SwingWorker conversion logic"
```

---

### Task 5: 成功弹窗

**Files:**
- Modify: `src/main/java/cn/p4u/dth/gui/GuiRunner.java`

自定义 JDialog 显示转换结果，"打开文件位置"按钮调用 `Desktop.open()`。

- [ ] **Step 1: 添加 showSuccessDialog 方法**

```java
    private void showSuccessDialog(final Path outputPath) {
        final JDialog dialog = new JDialog(this, "转换完成", true);
        dialog.setSize(380, 150);
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(this);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        panel.setLayout(new BorderLayout(8, 8));

        JLabel infoLabel = new JLabel("✔ 输出文件: " + outputPath.toString());
        infoLabel.setFont(infoLabel.getFont().deriveFont(Font.PLAIN, 12));

        JButton openButton = new JButton("打开文件位置");
        JButton okButton = new JButton("确定");

        openButton.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                try {
                    Desktop.getDesktop().open(outputPath.toFile().getParentFile());
                } catch (IOException ignored) {
                    // 无法打开目录时静默忽略
                }
            }
        });

        okButton.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                dialog.dispose();
            }
        });

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttonPanel.add(openButton);
        buttonPanel.add(okButton);

        panel.add(infoLabel, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setContentPane(panel);
        dialog.setVisible(true);
    }
```

确认 import 区域包含 `java.awt.BorderLayout`, `java.awt.FlowLayout`, `java.awt.Font`, `java.awt.Desktop`, `java.io.IOException`。

- [ ] **Step 2: 编译确认**

Run: `cd C:\DevCode\agile-hub\docxToHtml4j && set JAVA_HOME=C:\DevRepo\jdk\jdk-21 && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: 手动冒烟测试**

Run: `cd C:\DevCode\agile-hub\docxToHtml4j && set JAVA_HOME=C:\DevRepo\jdk\jdk-21 && mvn exec:java -Dexec.mainClass="cn.p4u.dth.gui.GuiRunner" -q`
操作：选择 demo.docx → 转换 → 在弹窗中点击"打开文件位置" → 确认资源管理器打开
Expected: 弹窗显示输出路径，点击"打开文件位置"打开目录

- [ ] **Step 4: 提交**

```bash
cd C:\DevCode\agile-hub\docxToHtml4j
git add src/main/java/cn/p4u/dth/gui/GuiRunner.java
git commit -m "feat(gui): add success dialog with open-file-location"
```

---

### Task 6: GraalVM Native Image Maven Profile

**Files:**
- Modify: `pom.xml`
- Create: `src/main/resources/META-INF/native-image/reflect-config.json`
- Create: `src/main/resources/META-INF/native-image/resource-config.json`

添加 Maven profile `native`，使用 `org.graalvm.buildtools:native-maven-plugin`。配置 Swing 反射元数据。

- [ ] **Step 1: 在 pom.xml 的 `</build>` 前添加 profiles 段**

在 `</build>` 标签之前，`</project>` 之前，添加：

```xml
  <profiles>
    <profile>
      <id>native</id>
      <build>
        <plugins>
          <plugin>
            <groupId>org.graalvm.buildtools</groupId>
            <artifactId>native-maven-plugin</artifactId>
            <version>0.10.6</version>
            <extensions>true</extensions>
            <configuration>
              <mainClass>cn.p4u.dth.gui.GuiRunner</mainClass>
              <outputName>docx2html</outputName>
              <buildArgs>
                <buildArg>--no-fallback</buildArg>
                <buildArg>-H:windowsApiType=gui</buildArg>
              </buildArgs>
            </configuration>
            <executions>
              <execution>
                <id>build-native</id>
                <goals>
                  <goal>.Compile</goal>
                </goals>
                <phase>package</phase>
              </execution>
            </executions>
          </plugin>
          <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-shade-plugin</artifactId>
            <version>3.6.0</version>
            <executions>
              <execution>
                <phase>package</phase>
                <goals>
                  <goal>shade</goal>
                </goals>
                <configuration>
                  <shadedArtifactAttached>true</shadedArtifactAttached>
                  <shadedClassifierName>all</shadedClassifierName>
                  <transformers>
                    <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                      <mainClass>cn.p4u.dth.gui.GuiRunner</mainClass>
                    </transformer>
                  </transformers>
                </configuration>
              </execution>
            </executions>
          </plugin>
        </plugins>
      </build>
    </profile>
  </profiles>
```

注意：shade plugin 打 fat jar 是 native-image 编译的前置步骤（需要 classpath 包含所有依赖）。

- [ ] **Step 2: 创建 reflect-config.json**

创建目录 `src/main/resources/META-INF/native-image/`，写入文件：

```json
[
  {
    "name": "javax.swing.JFrame",
    "allDeclaredConstructors": true,
    "allDeclaredMethods": true,
    "allDeclaredFields": true
  },
  {
    "name": "javax.swing.JPanel",
    "allDeclaredConstructors": true,
    "allDeclaredMethods": true,
    "allDeclaredFields": true
  },
  {
    "name": "javax.swing.JButton",
    "allDeclaredConstructors": true,
    "allDeclaredMethods": true,
    "allDeclaredFields": true
  },
  {
    "name": "javax.swing.JTextField",
    "allDeclaredConstructors": true,
    "allDeclaredMethods": true,
    "allDeclaredFields": true
  },
  {
    "name": "javax.swing.JLabel",
    "allDeclaredConstructors": true,
    "allDeclaredMethods": true,
    "allDeclaredFields": true
  },
  {
    "name": "javax.swing.JProgressBar",
    "allDeclaredConstructors": true,
    "allDeclaredMethods": true,
    "allDeclaredFields": true
  },
  {
    "name": "javax.swing.JDialog",
    "allDeclaredConstructors": true,
    "allDeclaredMethods": true,
    "allDeclaredFields": true
  },
  {
    "name": "javax.swing.JFileChooser",
    "allDeclaredConstructors": true,
    "allDeclaredMethods": true,
    "allDeclaredFields": true
  },
  {
    "name": "com.sun.java.swing.plaf.windows.WindowsLookAndFeel",
    "allDeclaredConstructors": true,
    "allDeclaredMethods": true
  }
]
```

- [ ] **Step 3: 创建 resource-config.json**

```json
{
  "resources": {
    "includes": []
  }
}
```

- [ ] **Step 4: 验证常规构建不受影响**

Run: `cd C:\DevCode\agile-hub\docxToHtml4j && set JAVA_HOME=C:\DevRepo\jdk\jdk-21 && mvn compile test -q`
Expected: BUILD SUCCESS — profile 未激活，不受影响

- [ ] **Step 5: 提交**

```bash
cd C:\DevCode\agile-hub\docxToHtml4j
git add pom.xml src/main/resources/META-INF/native-image/reflect-config.json src/main/resources/META-INF/native-image/resource-config.json
git commit -m "feat(build): add GraalVM Native Image profile and Swing reflect config"
```

---

### Task 7: 集成验证与清理

**Files:**
- Modify: `src/main/java/cn/p4u/dth/gui/GuiRunner.java`（如有修复）
- Modify: `src/test/java/cn/p4u/dth/gui/GuiRunnerTest.java`（如有补充）

最终验证：全量测试、手动端到端测试、代码审查。

- [ ] **Step 1: 运行全量测试**

Run: `cd C:\DevCode\agile-hub\docxToHtml4j && set JAVA_HOME=C:\DevRepo\jdk\jdk-21 && mvn test -q`
Expected: BUILD SUCCESS — 所有测试通过

- [ ] **Step 2: 手动端到端测试**

Run: `cd C:\DevCode\agile-hub\docxToHtml4j && set JAVA_HOME=C:\DevRepo\jdk\jdk-21 && mvn exec:java -Dexec.mainClass="cn.p4u.dth.gui.GuiRunner" -q`

验证清单：
- [ ] 窗口标题为"docx → HTML 转换器"
- [ ] 窗口大小约 480×220，不可缩放
- [ ] 窗口居中
- [ ] Windows 原生外观
- [ ] 初始状态：路径框显示"未选择文件"，"转换"按钮灰色禁用
- [ ] 点击"浏览..."弹出文件选择器，只显示 .docx 文件
- [ ] 选择文件后路径框显示完整路径，"转换"按钮启用
- [ ] 点击"转换"→ 进度条显示，按钮禁用 → 弹出成功对话框
- [ ] 成功对话框有"打开文件位置"和"确定"按钮
- [ ] 打开文件位置能打开资源管理器到对应目录
- [ ] 同目录生成 .html 文件，内容正确
- [ ] 关闭窗口即退出程序

- [ ] **Step 3: 如有修复，补充测试并提交**

- [ ] **Step 4: 最终提交（如有修复）**

```bash
cd C:\DevCode\agile-hub\docxToHtml4j
git add -A
git commit -m "fix(gui): integration fixes from smoke test"
```

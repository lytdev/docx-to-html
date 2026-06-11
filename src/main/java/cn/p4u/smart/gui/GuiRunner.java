package cn.p4u.smart.gui;

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
        String htmlName;
        if (fileName.toLowerCase().endsWith(".docx")) {
            htmlName = fileName.substring(0, fileName.length() - 5) + ".html";
        } else {
            htmlName = fileName + ".html";
        }
        Path parent = docxPath.getParent();
        return parent != null ? parent.resolve(htmlName) : Paths.get(htmlName);
    }
}

package cn.p4u.smart.gui;

import cn.p4u.smart.converter.ConversionConfig;
import cn.p4u.smart.converter.ConversionResult;
import cn.p4u.smart.converter.DocxConverter;
import cn.p4u.smart.util.Jdk8Helpers;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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
        setTitle(TITLE);
        setSize(WIDTH, HEIGHT);
        setResizable(false);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

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

        layoutComponents();
        bindEvents();
    }

    private void layoutComponents() {
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        panel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 4, 4, 4);

        // Row 0: path field + browse button
        gbc.gridx = 0; gbc.gridy = 0;
        gbc.weightx = 1.0;
        panel.add(pathField, gbc);

        gbc.gridx = 1; gbc.gridy = 0;
        gbc.weightx = 0.0;
        panel.add(browseButton, gbc);

        // Row 1: progress bar
        gbc.gridx = 0; gbc.gridy = 1;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        panel.add(progressBar, gbc);

        // Row 2: status label
        gbc.gridx = 0; gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        panel.add(statusLabel, gbc);

        // Row 3: convert button (right-aligned)
        gbc.gridx = 0; gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(convertButton, gbc);

        setContentPane(panel);
    }

    private void bindEvents() {
        browseButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                chooseFile();
            }
        });

        convertButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
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

        openButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    Desktop.getDesktop().open(outputPath.toFile().getParentFile());
                } catch (IOException ignored) {
                    // 无法打开目录时静默忽略
                }
            }
        });

        okButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
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

    private void showError(String message) {
        statusLabel.setText("❌ " + message);
        statusLabel.setForeground(Color.RED);
    }

    private void clearError() {
        statusLabel.setText(" ");
        statusLabel.setForeground(Color.RED);
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
        String htmlName;
        if (fileName.toLowerCase().endsWith(".docx")) {
            htmlName = fileName.substring(0, fileName.length() - 5) + ".html";
        } else {
            htmlName = fileName + ".html";
        }
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

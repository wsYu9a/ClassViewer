package com.classviewer.ui;

import com.classviewer.service.BatchDecompilerService;
import com.classviewer.service.DecompilerService;
import com.classviewer.service.JarExtractorService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 主界面控制器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MainViewController {

    private final DecompilerService decompilerService;
    private final JarExtractorService jarExtractorService;
    private final BatchDecompilerService batchDecompilerService;
    
    private Stage primaryStage;
    private TreeView<String> fileTreeView;
    private TextArea codeTextArea;
    private TextArea logTextArea;  // 日志输出区
    private Label statusLabel;
    private ProgressBar progressBar;
    private Label progressLabel;  // 进度文本
    private File selectedDirectory;  // 当前选择的目录
    private volatile boolean isDecompiling = false;  // 是否正在反编译
    private Button clearBtn;  // 清空按钮引用
    private StringBuilder processLog = new StringBuilder();  // 处理日志累积

    public void show(Stage stage) {
        this.primaryStage = stage;
        primaryStage.setTitle("ClassViewer - Java反编译工具");
        
        // 设置关闭事件：退出GUI后项目暂停
        primaryStage.setOnCloseRequest(event -> {
            log.info("用户关闭窗口，程序退出");
            System.exit(0);
        });
        
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #f5f5f5;");

        // 顶部工具栏
        root.setTop(createToolBar());

        // 中间分割面板
        root.setCenter(createSplitPane());

        // 底部状态栏和日志区
        VBox bottomContainer = new VBox();
        bottomContainer.getChildren().addAll(createLogPanel(), createStatusBar());
        root.setBottom(bottomContainer);

        
        Scene scene = new Scene(root, 1200, 750);
        primaryStage.setScene(scene);
        primaryStage.show();
        
        appendLog("✓ ClassViewer 初始化完成");
        appendLog("→ 请选择项目文件夹开始反编译");
    }

    /**
     * 创建工具栏
     */
    private ToolBar createToolBar() {
        ToolBar toolBar = new ToolBar();
        toolBar.setStyle("-fx-background-color: #ffffff; " +
                        "-fx-padding: 10; -fx-spacing: 8; -fx-border-color: #e0e0e0; -fx-border-width: 0 0 1 0;");

        // 选择文件夹按钮
        Button selectFolderBtn = new Button("📁 选择文件夹");
        selectFolderBtn.getStyleClass().add("modern-button");
        selectFolderBtn.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; " +
                                "-fx-font-size: 14px; -fx-padding: 10 24; -fx-background-radius: 6; " +
                                "-fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(33,150,243,0.3), 4, 0, 0, 2);");
        selectFolderBtn.setOnAction(e -> selectProjectFolder());
        selectFolderBtn.setOnMouseEntered(e -> 
            selectFolderBtn.setStyle(selectFolderBtn.getStyle() + "-fx-background-color: #1976D2;"));

        // 开始反编译按钮
        Button startDecompileBtn = new Button("🚀 开始反编译");
        startDecompileBtn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; " +
                                   "-fx-font-size: 14px; -fx-padding: 10 24; -fx-background-radius: 6; " +
                                   "-fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(76,175,80,0.3), 4, 0, 0, 2);");
        startDecompileBtn.setOnAction(e -> startAutoDecompile());

        Region spacer1 = new Region();
        spacer1.setPrefWidth(20);

        Separator separator = new Separator();
        separator.setOrientation(javafx.geometry.Orientation.VERTICAL);

        Region spacer2 = new Region();
        spacer2.setPrefWidth(20);

        // 白名单设置按钮
        Button whitelistBtn = new Button("⚙ 白名单设置");
        whitelistBtn.setStyle("-fx-background-color: #FF9800; -fx-text-fill: white; " +
                             "-fx-font-size: 13px; -fx-padding: 8 20; -fx-background-radius: 6; -fx-cursor: hand;");
        whitelistBtn.setOnAction(e -> showWhitelistDialog());

        // 清空按钮
        clearBtn = new Button("✕ 清空");
        clearBtn.setStyle("-fx-background-color: #f44336; -fx-text-fill: white; " +
                         "-fx-font-size: 13px; -fx-padding: 8 20; -fx-background-radius: 6; -fx-cursor: hand;");
        clearBtn.setOnAction(e -> clearAll());

        toolBar.getItems().addAll(
                selectFolderBtn, startDecompileBtn,
                spacer1, separator, spacer2,
                whitelistBtn, clearBtn
        );

        return toolBar;
    }

    /**
     * 创建分割面板
     */
    private SplitPane createSplitPane() {
        SplitPane splitPane = new SplitPane();
        splitPane.setStyle("-fx-background-color: transparent;");

        // 左侧：文件树
        VBox leftPane = new VBox(8);
        leftPane.setPadding(new Insets(10));
        leftPane.setStyle("-fx-background-color: white; -fx-background-radius: 8; " +
                         "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 10, 0, 0, 2);");
        
        Label treeLabel = new Label("📂 文件列表");
        treeLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: #333;");
        
        fileTreeView = new TreeView<>();
        fileTreeView.setStyle("-fx-background-color: white; -fx-border-color: #e0e0e0; " +
                             "-fx-border-radius: 4; -fx-background-radius: 4;");
        TreeItem<String> rootItem = new TreeItem<>("📦 未加载文件");
        rootItem.setExpanded(true);
        fileTreeView.setRoot(rootItem);
        fileTreeView.setShowRoot(true);
        
        // 文件树选择事件
        fileTreeView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.isLeaf()) {
                onFileSelected(newVal.getValue());
            }
        });
        
        VBox.setVgrow(fileTreeView, Priority.ALWAYS);
        leftPane.getChildren().addAll(treeLabel, fileTreeView);

        // 右侧：代码显示区
        VBox rightPane = new VBox(8);
        rightPane.setPadding(new Insets(10));
        rightPane.setStyle("-fx-background-color: white; -fx-background-radius: 8; " +
                          "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 10, 0, 0, 2);");
        
        Label codeLabel = new Label("📄 反编译结果");
        codeLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: #333;");
        
        codeTextArea = new TextArea();
        codeTextArea.setEditable(false);
        codeTextArea.setWrapText(false);
        codeTextArea.setStyle("-fx-font-family: 'Consolas', 'Monaco', 'Courier New', monospace; " +
                             "-fx-font-size: 13px; -fx-background-color: #fafafa; " +
                             "-fx-border-color: #e0e0e0; -fx-border-radius: 4; " +
                             "-fx-background-radius: 4; -fx-text-fill: #333;");
        codeTextArea.setText("🎯 欢迎使用 ClassViewer\n\n" +
                            "使用指南：\n" +
                            "1. 点击 '📁 选择文件夹' 选择项目目录\n" +
                            "2. 点击 '🚀 开始反编译' 自动完成解压和反编译\n" +
                            "3. 在左侧文件树中选择文件查看反编译结果\n\n" +
                            "提示：可以在 '白名单设置' 中配置需要排除的依赖包");
        
        VBox.setVgrow(codeTextArea, Priority.ALWAYS);
        rightPane.getChildren().addAll(codeLabel, codeTextArea);

        splitPane.getItems().addAll(leftPane, rightPane);
        splitPane.setDividerPositions(0.30);

        return splitPane;
    }

    /**
     * 创建日志面板
     */
    private VBox createLogPanel() {
        VBox logPanel = new VBox(5);
        logPanel.setPadding(new Insets(10));
        logPanel.setPrefHeight(120);
        logPanel.setStyle("-fx-background-color: #263238;");
        
        Label logLabel = new Label("📋 实时日志");
        logLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #B0BEC5;");
        
        logTextArea = new TextArea();
        logTextArea.setEditable(false);
        logTextArea.setWrapText(true);
        logTextArea.setStyle("-fx-control-inner-background: #263238; " +
                            "-fx-text-fill: #B0BEC5; " +
                            "-fx-font-family: 'Consolas', 'Monaco', monospace; " +
                            "-fx-font-size: 12px; " +
                            "-fx-highlight-fill: #37474F; " +
                            "-fx-highlight-text-fill: #ECEFF1;");
        
        VBox.setVgrow(logTextArea, Priority.ALWAYS);
        logPanel.getChildren().addAll(logLabel, logTextArea);
        
        return logPanel;
    }

    /**
     * 创建状态栏
     */
    private HBox createStatusBar() {
        HBox statusBar = new HBox(15);
        statusBar.setPadding(new Insets(8, 15, 8, 15));
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setStyle("-fx-background-color: white; -fx-border-color: #e0e0e0; -fx-border-width: 1 0 0 0;");

        // 状态图标和文本
        Label statusIcon = new Label("●");
        statusIcon.setStyle("-fx-text-fill: #4CAF50; -fx-font-size: 16px;");
        
        statusLabel = new Label("就绪");
        statusLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #333;");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        // 进度标签
        progressLabel = new Label("");
        progressLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #666;");
        progressLabel.setVisible(false);
        
        // 进度条
        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(250);
        progressBar.setPrefHeight(8);
        progressBar.setStyle("-fx-accent: #4CAF50;");
        progressBar.setVisible(false);
        
        statusBar.getChildren().addAll(statusIcon, statusLabel, spacer, progressLabel, progressBar);

        return statusBar;
    }

    /**
     * 选择项目文件夹
     */
    private void selectProjectFolder() {
        javafx.stage.DirectoryChooser directoryChooser = new javafx.stage.DirectoryChooser();
        directoryChooser.setTitle("选择项目所在文件夹");
        
        if (selectedDirectory != null && selectedDirectory.exists()) {
            directoryChooser.setInitialDirectory(selectedDirectory.getParentFile());
        }

        File directory = directoryChooser.showDialog(primaryStage);
        if (directory != null) {
            selectedDirectory = directory;
            updateStatus("已选择目录: " + directory.getName());
            appendLog("✓ 已选择目录: " + directory.getAbsolutePath());
            codeTextArea.setText("📁 当前选择目录: " + directory.getAbsolutePath() + "\n\n" +
                                "💡 点击 '🚀 开始反编译' 按钮开始自动处理...");
            
            // 显示目录中的文件
            appendLog("→ 开始扫描目录结构...");
            try {
                loadDirectoryStructure(directory);
            } catch (Exception e) {
                appendLog("✗ 加载目录失败: " + e.getMessage());
                showError("加载目录失败", e.getMessage());
            }
        }
    }

    /**
     * 加载目录结构到文件树
     */
    private void loadDirectoryStructure(File directory) {
        updateStatus("正在扫描目录...");
        
        new Thread(() -> {
            try {
                List<String> allFiles = new ArrayList<>();
                scanDirectory(directory, directory.toPath(), allFiles);
                
                javafx.application.Platform.runLater(() -> {
                    updateFileTree(directory.getName(), allFiles);
                    updateStatus("目录扫描完成");
                    appendLog(String.format("✓ 目录扫描完成，共发现 %d 个文件", allFiles.size()));
                });
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> {
                    appendLog("✗ 扫描目录失败: " + e.getMessage());
                    showError("扫描目录失败", e.getMessage());
                    updateStatus("扫描失败");
                });
            }
        }).start();
    }

    /**
     * 添加日志
     */
    private void appendLog(String message) {
        javafx.application.Platform.runLater(() -> {
            String timestamp = java.time.LocalTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
            logTextArea.appendText(String.format("[%s] %s\n", timestamp, message));
            logTextArea.setScrollTop(Double.MAX_VALUE);
        });
    }

    /**
     * 更新进度
     */
    private void updateProgress(double progress, String text) {
        javafx.application.Platform.runLater(() -> {
            if (progress < 0) {
                progressBar.setVisible(false);
                progressLabel.setVisible(false);
            } else {
                progressBar.setVisible(true);
                progressLabel.setVisible(true);
                progressBar.setProgress(progress);
                progressLabel.setText(text);
            }
        });
    }
    private void scanDirectory(File dir, Path basePath, List<String> files) {
        File[] fileList = dir.listFiles();
        if (fileList != null) {
            for (File file : fileList) {
                if (file.isDirectory()) {
                    scanDirectory(file, basePath, files);
                } else {
                    String relativePath = basePath.relativize(file.toPath()).toString().replace("\\", "/");
                    files.add(relativePath);
                }
            }
        }
    }

    /**
     * 开始自动反编译（递归解压JAR + 批量反编译）
     */
    private void startAutoDecompile() {
        if (selectedDirectory == null) {
            showError("错误", "请先选择项目文件夹！");
            appendLog("✗ 错误: 未选择项目文件夹");
            return;
        }
        
        if (isDecompiling) {
            showError("错误", "正在反编译中，请稍候...");
            return;
        }

        appendLog("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        appendLog("🚀 开始自动反编译流程...");
        updateStatus("正在处理...");
        updateProgress(-1, "准备中...");
        
        // 禁用清空按钮
        isDecompiling = true;
        clearBtn.setDisable(true);
        
        // 清空之前的处理日志
        processLog.setLength(0);
        processLog.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        processLog.append("🚀 开始自动反编译流程\n");
        processLog.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");
        
        // 在反编译结果区显示开始信息
        javafx.application.Platform.runLater(() -> {
            codeTextArea.setText(processLog.toString());
        });

        new Thread(() -> {
            try {
                // 第一步：递归解压所有JAR包
                appendProcessLog("=== 第1步：递归解压JAR包 ===");
                appendProcessLog("正在扫描并解压JAR文件...\n");
                appendLog("=== 第1步：递归解压JAR包 ===");
                appendLog("正在扫描并解压JAR文件...");
                
                JarExtractorService.ExtractionResult extractResult = 
                    extractAllJarsInDirectory(selectedDirectory);
                
                appendProcessLog("✓ 解压完成！");
                appendProcessLog("  • 总JAR数: " + extractResult.getTotalJars());
                appendProcessLog("  • 已解压: " + extractResult.getExtractedJars());
                appendProcessLog("  • 已跳过: " + extractResult.getSkippedJars());
                appendProcessLog("  • CLASS文件: " + extractResult.getClassFiles());
                appendProcessLog("");
                
                appendLog("解压完成！");
                appendLog("  总JAR数: " + extractResult.getTotalJars());
                appendLog("  已解压: " + extractResult.getExtractedJars());
                appendLog("  已跳过: " + extractResult.getSkippedJars());
                appendLog("  CLASS文件: " + extractResult.getClassFiles());
                appendLog("");

                // 第二步：批量反编译所有CLASS文件
                appendProcessLog("=== 第2步：批量反编译CLASS文件 ===");
                appendProcessLog("正在反编译，请稍候...\n");
                appendLog("=== 第2步：批量反编译CLASS文件 ===");
                appendLog("正在反编译，请稍候...");
                
                // 使用进度回调实时显示进度
                BatchDecompilerService.BatchResult batchResult = 
                    batchDecompilerService.batchDecompile(selectedDirectory.toPath(), true, 
                        (current, total, fileName) -> {
                            // 只在日志区显示进度，每处理10个文件或处理到最后一个文件时输出
                            if (current % 10 == 0 || current == total) {
                                String progressMsg = String.format("  → 进度: %d/%d (%.1f%%)", 
                                    current, total, (current * 100.0 / total));
                                appendLog(progressMsg + " - " + fileName);
                            }
                        });
                
                appendProcessLog("");
                appendProcessLog("✓ 反编译完成！");
                appendProcessLog("  • 总文件数: " + batchResult.getTotalFiles());
                appendProcessLog("  • 成功: " + batchResult.getSuccessCount());
                appendProcessLog("  • 失败: " + batchResult.getFailCount());
                appendProcessLog("  • 耗时: " + batchResult.getDuration() + "ms");
                appendProcessLog("");
                appendProcessLog("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                appendProcessLog("✓ 所有操作完成！");
                appendProcessLog("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                appendProcessLog("");
                appendProcessLog("📊 处理统计：");
                appendProcessLog("  JAR包处理：");
                appendProcessLog("    - 解压: " + extractResult.getExtractedJars() + " 个");
                appendProcessLog("    - 跳过: " + extractResult.getSkippedJars() + " 个");
                appendProcessLog("");
                appendProcessLog("  CLASS文件处理：");
                appendProcessLog("    - 发现: " + batchResult.getTotalFiles() + " 个");
                appendProcessLog("    - 成功: " + batchResult.getSuccessCount() + " 个");
                appendProcessLog("    - 失败: " + batchResult.getFailCount() + " 个");
                appendProcessLog("");
                appendProcessLog("💡 说明：");
                appendProcessLog("  反编译结果已保存在原目录中");
                appendProcessLog("  每个 .class 文件旁边都有对应的 .java 文件");
                appendProcessLog("  您可以在左侧文件树中选择文件查看反编译结果");
                
                appendLog("反编译完成！");
                appendLog("  总文件数: " + batchResult.getTotalFiles());
                appendLog("  成功: " + batchResult.getSuccessCount());
                appendLog("  失败: " + batchResult.getFailCount());
                appendLog("  耗时: " + batchResult.getDuration() + "ms");
                appendLog("");
                appendLog("=== 所有操作完成！===");
                appendLog("✓ 反编译结果已保存在原目录中，每个.class文件旁边都有对应的.java文件");
                
                javafx.application.Platform.runLater(() -> {
                    showProgress(false);
                    updateStatus("自动反编译完成");
                    
                    // 显示完成对话框
                    showCompletionDialog(extractResult, batchResult);
                    
                    // 刷新文件树
                    try {
                        loadDirectoryStructure(selectedDirectory);
                    } catch (Exception e) {
                        appendLog("✗ 刷新文件树失败: " + e.getMessage());
                    }
                    
                    // 恢复清空按钮
                    isDecompiling = false;
                    clearBtn.setDisable(false);
                });
                
            } catch (Exception e) {
                appendLog("✗ 错误: " + e.getMessage());
                javafx.application.Platform.runLater(() -> {
                    showProgress(false);
                    showError("自动反编译失败", e.getMessage());
                    updateStatus("处理失败");
                    
                    // 恢复清空按钮
                    isDecompiling = false;
                    clearBtn.setDisable(false);
                });
            }
        }).start();
    }

    /**
     * 查找JAR文件
     */
    private List<File> findJarFiles(File directory) {
        List<File> jarFiles = new ArrayList<>();
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().toLowerCase().endsWith(".jar")) {
                    jarFiles.add(file);
                }
            }
        }
        return jarFiles;
    }
    

    private JarExtractorService.ExtractionResult extractAllJarsInDirectory(File directory) {
        JarExtractorService.ExtractionResult totalResult = new JarExtractorService.ExtractionResult();
        
        // 查找所有JAR文件
        List<File> jarFiles = findAllJarFiles(directory);
        
        for (File jarFile : jarFiles) {
            // 检查是否在白名单中
            if (isJarInWhitelist(jarFile.getName())) {
                totalResult.addSkippedJar(jarFile.getName());
                continue;
            }
            
            // 解压到同级目录
            Path outputPath = jarFile.getParentFile().toPath();
            
            try {
                JarExtractorService.ExtractionResult result = 
                    jarExtractorService.extractJarRecursively(jarFile, outputPath);
                
                // 合并结果
                mergeExtractionResults(totalResult, result);
                
                // 删除原JAR文件（可选）
                // jarFile.delete();
                
            } catch (Exception e) {
                totalResult.addError(jarFile.getName(), e.getMessage());
            }
        }
        
        return totalResult;
    }

    /**
     * 查找目录中的所有JAR文件
     */
    private List<File> findAllJarFiles(File directory) {
        List<File> jarFiles = new ArrayList<>();
        findJarFilesRecursive(directory, jarFiles);
        return jarFiles;
    }

    /**
     * 递归查找JAR文件
     */
    private void findJarFilesRecursive(File dir, List<File> jarFiles) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    findJarFilesRecursive(file, jarFiles);
                } else if (file.getName().toLowerCase().endsWith(".jar")) {
                    jarFiles.add(file);
                }
            }
        }
    }

    /**
     * 检查JAR是否在白名单中
     */
    private boolean isJarInWhitelist(String jarName) {
        String lowerName = jarName.toLowerCase();
        for (String pattern : jarExtractorService.getWhitelist()) {
            if (lowerName.contains(pattern.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 合并解压结果
     */
    private void mergeExtractionResults(JarExtractorService.ExtractionResult total, 
                                       JarExtractorService.ExtractionResult current) {
        current.getExtractedJarNames().forEach(total::addExtractedJar);
        current.getSkippedJarNames().forEach(total::addSkippedJar);
        current.getErrors().forEach(total::addError);
    }

    /**
     * 添加处理日志（同时显示在日志区和反编译结果区）
     */
    private void appendProcessLog(String message) {
        processLog.append(message).append("\n");
        
        // 实时更新反编译结果区（仅最终摘要，不包含实时进度）
        javafx.application.Platform.runLater(() -> {
            codeTextArea.setText(processLog.toString());
            // 自动滚动到底部
            codeTextArea.setScrollTop(Double.MAX_VALUE);
        });
    }



    /**
     * 显示完成对话框
     */
    private void showCompletionDialog(JarExtractorService.ExtractionResult extractResult,
                                     BatchDecompilerService.BatchResult batchResult) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("处理完成");
        alert.setHeaderText("自动反编译流程已完成");
        
        StringBuilder content = new StringBuilder();
        content.append("JAR解压统计：\n");
        content.append("  总JAR数: ").append(extractResult.getTotalJars()).append("\n");
        content.append("  已解压: ").append(extractResult.getExtractedJars()).append("\n");
        content.append("  已跳过: ").append(extractResult.getSkippedJars()).append("\n");
        content.append("  CLASS文件: ").append(extractResult.getClassFiles()).append("\n\n");
        
        content.append("反编译统计：\n");
        content.append("  总文件数: ").append(batchResult.getTotalFiles()).append("\n");
        content.append("  成功: ").append(batchResult.getSuccessCount()).append("\n");
        content.append("  失败: ").append(batchResult.getFailCount()).append("\n");
        content.append("  耗时: ").append(batchResult.getDuration()).append("ms\n\n");
        
        content.append("所有.java文件已生成在对应.class文件旁边");
        
        alert.setContentText(content.toString());
        alert.showAndWait();
    }

    /**
     * 文件选择事件（在文件树中选择文件时显示内容）
     */
    private void onFileSelected(String fileName) {
        if (fileName.endsWith(".class")) {
            updateStatus("正在加载: " + fileName);
            appendLog("→ 正在反编译: " + fileName);
            
            new Thread(() -> {
                try {
                    // 根据当前选择的目录和文件名构造完整路径
                    File classFile = null;
                    if (selectedDirectory != null) {
                        classFile = new File(selectedDirectory, fileName);
                    }
                    
                    if (classFile == null || !classFile.exists()) {
                        javafx.application.Platform.runLater(() -> {
                            codeTextArea.setText("// 文件不存在: " + fileName);
                            updateStatus("文件不存在");
                        });
                        return;
                    }
                    
                    // 检查是否已经有对应的.java文件
                    String javaFilePath = classFile.getAbsolutePath().replace(".class", ".java");
                    File javaFile = new File(javaFilePath);
                    
                    String sourceCode;
                    if (javaFile.exists()) {
                        // 如果已经反编译过，直接读取.java文件
                        sourceCode = new String(Files.readAllBytes(javaFile.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                        javafx.application.Platform.runLater(() -> {
                            codeTextArea.setText(sourceCode);
                            updateStatus("✓ 已加载: " + fileName);
                            appendLog("  ✓ 加载成功");
                        });
                    } else {
                        // 如果还没有反编译，现在反编译
                        sourceCode = decompilerService.decompileClass(classFile);
                        javafx.application.Platform.runLater(() -> {
                            codeTextArea.setText(sourceCode);
                            updateStatus("✓ 反编译完成: " + fileName);
                            appendLog("  ✓ 反编译完成");
                        });
                    }
                } catch (Exception e) {
                    javafx.application.Platform.runLater(() -> {
                        String errorMsg = "反编译失败: " + e.getMessage();
                        codeTextArea.setText("// " + errorMsg + "\n// 文件: " + fileName);
                        updateStatus("✗ " + errorMsg);
                        appendLog("  ✗ 失败: " + e.getMessage());
                        log.error("反编译失败: {}", fileName, e);
                    });
                }
            }).start();
        } else if (fileName.endsWith(".java")) {
            // 如果选择的是.java文件，直接显示
            new Thread(() -> {
                try {
                    File javaFile = new File(selectedDirectory, fileName);
                    if (javaFile.exists()) {
                        String content = new String(Files.readAllBytes(javaFile.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                        javafx.application.Platform.runLater(() -> {
                            codeTextArea.setText(content);
                            updateStatus("✓ 已加载: " + fileName);
                            appendLog("  ✓ 加载成功");
                        });
                    }
                } catch (Exception e) {
                    javafx.application.Platform.runLater(() -> {
                        codeTextArea.setText("// 读取文件失败: " + e.getMessage());
                        updateStatus("读取失败");
                    });
                }
            }).start();
        }
    }

    /**
     * 更新文件树
     */
    private void updateFileTree(String rootName, java.util.List<String> entries) {
        TreeItem<String> rootItem = new TreeItem<>(rootName);
        rootItem.setExpanded(true);

        java.util.Map<String, TreeItem<String>> pathMap = new java.util.HashMap<>();
        pathMap.put("", rootItem);

        for (String entry : entries) {
            String[] parts = entry.split("/");
            String currentPath = "";

            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                String parentPath = currentPath;
                currentPath = currentPath.isEmpty() ? part : currentPath + "/" + part;

                if (!pathMap.containsKey(currentPath)) {
                    TreeItem<String> item = new TreeItem<>(part);
                    TreeItem<String> parent = pathMap.get(parentPath);
                    parent.getChildren().add(item);
                    pathMap.put(currentPath, item);

                    if (i == parts.length - 1) {
                        item.setExpanded(false);
                    } else {
                        item.setExpanded(true);
                    }
                }
            }
        }

        fileTreeView.setRoot(rootItem);
    }

    /**
     * 显示白名单设置对话框
     */
    private void showWhitelistDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("白名单管理");
        dialog.setHeaderText("JAR过滤白名单配置\n包含以下关键词的JAR包将被跳过");

        // 创建主面板
        VBox mainContent = new VBox(10);
        mainContent.setPadding(new Insets(10));
        
        // 文件路径提示
        Label pathLabel = new Label("配置文件: " + jarExtractorService.getWhitelistFilePath());
        pathLabel.setStyle("-fx-text-fill: #666666; -fx-font-size: 11px;");
        
        // 白名单列表
        ListView<String> listView = new ListView<>();
        listView.setPrefHeight(300);
        listView.getItems().addAll(jarExtractorService.getWhitelist());
        listView.getItems().sort(String::compareTo);
        listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        
        // 按钮面板
        HBox buttonPanel = new HBox(10);
        buttonPanel.setAlignment(Pos.CENTER_LEFT);
        
        Button addBtn = new Button("添加");
        addBtn.setOnAction(e -> {
            TextInputDialog inputDialog = new TextInputDialog();
            inputDialog.setTitle("添加白名单规则");
            inputDialog.setHeaderText("请输入要添加的关键词");
            inputDialog.setContentText("关键词:");
            
            inputDialog.showAndWait().ifPresent(pattern -> {
                if (!pattern.trim().isEmpty()) {
                    jarExtractorService.addWhitelistPattern(pattern.trim());
                    listView.getItems().add(pattern.trim());
                    listView.getItems().sort(String::compareTo);
                    showInformation("成功", "已添加白名单规则: " + pattern.trim());
                }
            });
        });
        
        Button removeBtn = new Button("删除选中");
        removeBtn.setOnAction(e -> {
            List<String> selected = new ArrayList<>(listView.getSelectionModel().getSelectedItems());
            if (!selected.isEmpty()) {
                Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
                confirmAlert.setTitle("确认删除");
                confirmAlert.setHeaderText("确认删除选中的 " + selected.size() + " 条规则？");
                confirmAlert.showAndWait().ifPresent(response -> {
                    if (response == ButtonType.OK) {
                        for (String pattern : selected) {
                            jarExtractorService.removeWhitelistPattern(pattern);
                            listView.getItems().remove(pattern);
                        }
                        showInformation("成功", "已删除 " + selected.size() + " 条规则");
                    }
                });
            } else {
                showInformation("提示", "请先选择要删除的规则");
            }
        });
        
        Button batchAddBtn = new Button("批量添加");
        batchAddBtn.setOnAction(e -> {
            Dialog<String> batchDialog = new Dialog<>();
            batchDialog.setTitle("批量添加白名单");
            batchDialog.setHeaderText("请输入要添加的关键词\n每行一个，用换行分隔");
            
            TextArea textArea = new TextArea();
            textArea.setPrefRowCount(10);
            textArea.setPrefColumnCount(40);
            textArea.setPromptText("例如：\naliyun-\ntencent-\nhuawei-");
            
            batchDialog.getDialogPane().setContent(textArea);
            ButtonType okBtn = new ButtonType("确定", ButtonBar.ButtonData.OK_DONE);
            ButtonType cancelBtn = new ButtonType("取消", ButtonBar.ButtonData.CANCEL_CLOSE);
            batchDialog.getDialogPane().getButtonTypes().addAll(okBtn, cancelBtn);
            
            batchDialog.setResultConverter(dialogButton -> {
                if (dialogButton == okBtn) {
                    return textArea.getText();
                }
                return null;
            });
            
            batchDialog.showAndWait().ifPresent(input -> {
                if (input != null && !input.trim().isEmpty()) {
                    String[] lines = input.split("\\n");
                    List<String> patterns = Arrays.stream(lines)
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());
                    
                    if (!patterns.isEmpty()) {
                        jarExtractorService.addWhitelistPatterns(patterns);
                        listView.getItems().addAll(patterns);
                        listView.getItems().sort(String::compareTo);
                        showInformation("成功", "已添加 " + patterns.size() + " 条规则");
                    }
                }
            });
        });
        
        Button resetBtn = new Button("重置默认");
        resetBtn.setOnAction(e -> {
            Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
            confirmAlert.setTitle("确认重置");
            confirmAlert.setHeaderText("确认重置为默认白名单？");
            confirmAlert.setContentText("将清空所有自定义规则，恢复为默认配置。");
            confirmAlert.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    jarExtractorService.resetToDefault();
                    listView.getItems().clear();
                    listView.getItems().addAll(jarExtractorService.getWhitelist());
                    listView.getItems().sort(String::compareTo);
                    showInformation("成功", "已重置为默认白名单");
                }
            });
        });
        
        buttonPanel.getChildren().addAll(addBtn, removeBtn, batchAddBtn, resetBtn);
        
        // 说明文本
        Label tipLabel = new Label(
            "提示：\n" +
            "1. 关键词支持部分匹配，例如 'spring-' 会匹配所有包含 'spring-' 的JAR\n" +
            "2. 修改会立即生效并持久化到本地文件\n" +
            "3. 配置文件位于用户目录下的 .classviewer 文件夹"
        );
        tipLabel.setStyle("-fx-text-fill: #666666; -fx-font-size: 11px;");
        tipLabel.setWrapText(true);
        
        mainContent.getChildren().addAll(pathLabel, listView, buttonPanel, tipLabel);
        dialog.getDialogPane().setContent(mainContent);
        dialog.getDialogPane().setPrefWidth(500);

        ButtonType closeBtn = new ButtonType("关闭", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().add(closeBtn);

        dialog.showAndWait();
    }

    /**
     * 显示/隐藏进度条
     */
    private void showProgress(boolean show) {
        progressBar.setVisible(show);
        if (!show) {
            progressBar.setProgress(0);
        }
    }

    /**
     * 清空所有内容
     */
    private void clearAll() {
        if (isDecompiling) {
            showError("错误", "正在反编译中，无法清空！");
            return;
        }
        
        fileTreeView.setRoot(new TreeItem<>("📦 未加载文件"));
        codeTextArea.clear();
        codeTextArea.setText("🎯 欢迎使用 ClassViewer\n\n" +
                            "使用指南：\n" +
                            "1. 点击 '📁 选择文件夹' 选择项目目录\n" +
                            "2. 点击 '🚀 开始反编译' 自动完成解压和反编译\n" +
                            "3. 在左侧文件树中选择文件查看反编译结果\n\n" +
                            "提示：可以在 '白名单设置' 中配置需要排除的依赖包");
        logTextArea.clear();
        selectedDirectory = null;
        updateStatus("已清空");
        appendLog("✓ 已清空所有内容");
    }

    /**
     * 更新状态栏
     */
    private void updateStatus(String message) {
        javafx.application.Platform.runLater(() -> {
            statusLabel.setText(message);
            log.info("状态更新: {}", message);
        });
    }

    /**
     * 显示错误对话框
     */
    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        // 美化对话框
        alert.getDialogPane().setStyle("-fx-font-family: 'Microsoft YaHei', 'SimHei'; -fx-font-size: 13px;");
        alert.showAndWait();
    }

    /**
     * 显示信息对话框
     */
    private void showInformation(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        // 美化对话框
        alert.getDialogPane().setStyle("-fx-font-family: 'Microsoft YaHei', 'SimHei'; -fx-font-size: 13px;");
        alert.showAndWait();
    }
}

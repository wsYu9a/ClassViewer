package com.classviewer.service;

import lombok.extern.slf4j.Slf4j;
import org.benf.cfr.reader.api.CfrDriver;
import org.benf.cfr.reader.api.OutputSinkFactory;
import org.benf.cfr.reader.api.SinkReturns;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.nio.file.Paths;

/**
 * 反编译服务
 * 支持JAR和CLASS文件的反编译
 */
@Slf4j
@Service
public class DecompilerService {

    private File currentJarFile;
    private File currentDirectory;
    private List<String> jarEntries = new ArrayList<>();
    private List<String> directoryFiles = new ArrayList<>();
    private final Path tempDir;

    public DecompilerService() throws IOException {
        // 创建临时目录用于反编译输出
        tempDir = Files.createTempDirectory("classviewer_");
        log.info("临时目录创建: {}", tempDir);
    }

    /**
     * 加载JAR文件
     */
    public void loadJarFile(File jarFile) throws IOException {
        this.currentJarFile = jarFile;
        this.jarEntries.clear();

        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.isDirectory()) {
                    jarEntries.add(entry.getName());
                }
            }
        }

        // 按路径排序
        jarEntries.sort(String::compareTo);
        log.info("JAR文件加载完成，共 {} 个文件", jarEntries.size());
    }

    /**
     * 加载文件夹
     */
    public void loadDirectory(File directory) throws IOException {
        this.currentDirectory = directory;
        this.directoryFiles.clear();

        Path basePath = directory.toPath();
        try (Stream<Path> paths = Files.walk(basePath)) {
            directoryFiles = paths
                    .filter(Files::isRegularFile)
                    .map(path -> basePath.relativize(path).toString().replace("\\", "/"))
                    .sorted()
                    .collect(Collectors.toList());
        }

        log.info("文件夹加载完成，共 {} 个文件", directoryFiles.size());
    }

    /**
     * 反编译CLASS文件
     */
    public String decompileClass(File classFile) throws Exception {
        if (!classFile.exists()) {
            String error = "文件不存在: " + classFile.getAbsolutePath();
            log.error(error);
            throw new IllegalArgumentException(error);
        }
        
        if (!classFile.getName().endsWith(".class")) {
            String error = "不是CLASS文件: " + classFile.getAbsolutePath();
            log.error(error);
            throw new IllegalArgumentException(error);
        }
        
        if (!classFile.canRead()) {
            String error = "文件不可读: " + classFile.getAbsolutePath();
            log.error(error);
            throw new IllegalArgumentException(error);
        }
        
        if (classFile.length() == 0) {
            String error = "文件为空: " + classFile.getAbsolutePath();
            log.error(error);
            throw new IllegalArgumentException(error);
        }
        
        log.info("准备反编译: {}, 大小: {} bytes", classFile.getName(), classFile.length());

        try {
            String result = decompileWithCFR(classFile.getAbsolutePath());
            if (result.startsWith("// 反编译失败")) {
                log.warn("反编译返回失败标识: {}", classFile.getName());
            }
            return result;
        } catch (Exception e) {
            log.error("反编译失败: {}", classFile.getName(), e);
            throw new Exception("反编译失败: " + e.getMessage(), e);
        }
    }

    /**
     * 反编译JAR中的某个条目
     */
    public String decompileEntry(String entryName) throws Exception {
        if (currentJarFile == null) {
            throw new IllegalStateException("未加载JAR文件");
        }

        if (!entryName.endsWith(".class")) {
            return "// 该文件不是CLASS文件";
        }

        // 创建临时输出目录
        Path outputDir = Files.createTempDirectory(tempDir, "decompile_");
        Path classOutputPath = outputDir.resolve("class");
        Files.createDirectories(classOutputPath);

        try {
            // 从JAR中提取CLASS文件
            File extractedClass = extractClassFromJar(entryName, classOutputPath.toFile());

            // 使用CFR反编译
            String sourceCode = decompileWithCFR(extractedClass.getAbsolutePath());
            log.info("反编译完成: {}", entryName);
            return sourceCode;
        } finally {
            // 清理临时文件
            deleteDirectory(outputDir.toFile());
        }
    }

    /**
     * 使用CFR反编译
     */
    private String decompileWithCFR(String classFilePath) {
        StringBuilder result = new StringBuilder();
        StringBuilder errorLog = new StringBuilder();
        final boolean[] hasOutput = {false};
        
        try {
            log.debug("开始反编译: {}", classFilePath);
            
            // 验证文件存在
            File file = new File(classFilePath);
            if (!file.exists()) {
                return "// 文件不存在: " + classFilePath;
            }
            
            OutputSinkFactory mySink = new OutputSinkFactory() {
                @Override
                public List<SinkClass> getSupportedSinks(SinkType sinkType, Collection<SinkClass> collection) {
                    // 支持所有类型
                    if (collection.isEmpty()) {
                        return Arrays.asList(
                            SinkClass.STRING,
                            SinkClass.DECOMPILED,
                            SinkClass.DECOMPILED_MULTIVER,
                            SinkClass.EXCEPTION_MESSAGE
                        );
                    }
                    return new ArrayList<>(collection);
                }

                @Override
                public <T> Sink<T> getSink(SinkType sinkType, SinkClass sinkClass) {
                    return sinkable -> {
                        try {
                            log.debug("接收到输出 - Type: {}, Class: {}, Sinkable: {}", 
                                    sinkType, sinkClass, sinkable.getClass().getSimpleName());
                            
                            // 尝试多种方式捕获输出
                            if (sinkable instanceof SinkReturns.Decompiled) {
                                SinkReturns.Decompiled decompiled = (SinkReturns.Decompiled) sinkable;
                                String javaCode = decompiled.getJava();
                                if (javaCode != null && !javaCode.trim().isEmpty()) {
                                    result.append(javaCode);
                                    hasOutput[0] = true;
                                    log.info("成功捕获Java代码，长度: {}", javaCode.length());
                                }
                            } else if (sinkable instanceof SinkReturns.ExceptionMessage) {
                                SinkReturns.ExceptionMessage exMsg = (SinkReturns.ExceptionMessage) sinkable;
                                String error = exMsg.getMessage();
                                errorLog.append(error).append("\n");
                                log.warn("捕获到错误: {}", error);
                            } else if (sinkType == SinkType.JAVA) {
                                // 尝试直接转换
                                String content = sinkable.toString();
                                if (content != null && content.length() > 50) {
                                    result.append(content);
                                    hasOutput[0] = true;
                                    log.info("通过toString捕获到内容，长度: {}", content.length());
                                }
                            }
                        } catch (Exception e) {
                            log.error("处理sink输出时出错", e);
                            errorLog.append("Sink处理错误: ").append(e.getMessage()).append("\n");
                        }
                    };
                }
            };

            // CFR选项
            Map<String, String> options = new HashMap<>();
            options.put("showversion", "false");
            options.put("comments", "true");
            options.put("outputdir", "");

            try {
                CfrDriver driver = new CfrDriver.Builder()
                        .withOutputSink(mySink)
                        .withOptions(options)
                        .build();
                
                log.debug("开始执行CFR分析...");
                driver.analyse(Collections.singletonList(classFilePath));
                log.debug("CFR分析完成");
            } catch (Exception e) {
                log.error("CFR执行失败", e);
                return "// CFR执行失败: " + e.getMessage() + "\n// 文件: " + classFilePath;
            }
            
            // 检查结果
            if (result.length() > 0) {
                log.info("反编译成功: {}, 代码长度: {}", classFilePath, result.length());
                return result.toString();
            } else if (errorLog.length() > 0) {
                log.error("反编译失败，错误信息:\n{}", errorLog);
                return "// 反编译失败\n// 错误信息:\n// " + 
                       errorLog.toString().replace("\n", "\n// ") + 
                       "\n// 文件: " + classFilePath;
            } else {
                log.warn("反编译未返回任何结果: {}", classFilePath);
                return "// 反编译失败：CFR未返回任何输出\n" +
                       "// 文件: " + classFilePath + "\n" +
                       "// 文件大小: " + file.length() + " bytes\n" +
                       "// 请检查class文件是否有效";
            }
        } catch (Throwable t) {
            log.error("反编译过程异常: {}", classFilePath, t);
            return "// 反编译异常: " + t.getClass().getSimpleName() + "\n" +
                   "// 错误: " + t.getMessage() + "\n" +
                   "// 文件: " + classFilePath;
        }
    }

    /**
     * 从JAR中提取CLASS文件
     */
    private File extractClassFromJar(String entryName, File outputDir) throws IOException {
        try (JarFile jar = new JarFile(currentJarFile)) {
            JarEntry entry = jar.getJarEntry(entryName);
            if (entry == null) {
                throw new IOException("找不到条目: " + entryName);
            }

            // 创建输出文件
            String fileName = Paths.get(entryName).getFileName().toString();
            File outputFile = new File(outputDir, fileName);

            // 复制文件内容
            Files.copy(jar.getInputStream(entry), outputFile.toPath());
            
            return outputFile;
        }
    }

    /**
     * 递归删除目录
     */
    private void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }

    /**
     * 清空当前状态
     */
    public void clear() {
        this.currentJarFile = null;
        this.currentDirectory = null;
        this.jarEntries.clear();
        this.directoryFiles.clear();
    }

    /**
     * 获取JAR条目列表
     */
    public List<String> getJarEntries() {
        return new ArrayList<>(jarEntries);
    }

    /**
     * 获取文件夹文件列表
     */
    public List<String> getDirectoryFiles() {
        return new ArrayList<>(directoryFiles);
    }
}

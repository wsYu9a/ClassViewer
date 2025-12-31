package com.classviewer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Stream;

/**
 * 批量反编译服务
 * 支持多线程批量反编译目录中的所有CLASS文件
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchDecompilerService {

    private final DecompilerService decompilerService;
    
    // 线程池大小
    private static final int THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors();

    /**
     * 批量反编译目录中的所有CLASS文件
     * @param directory 目录
     * @param useMultiThread 是否使用多线程
     * @return 反编译结果
     */
    public BatchResult batchDecompile(Path directory, boolean useMultiThread) throws IOException {
        return batchDecompile(directory, useMultiThread, null);
    }
    
    /**
     * 批量反编译目录中的所有CLASS文件（带进度回调）
     * @param directory 目录
     * @param useMultiThread 是否使用多线程
     * @param progressCallback 进度回调
     * @return 反编译结果
     */
    public BatchResult batchDecompile(Path directory, boolean useMultiThread, ProgressCallback progressCallback) throws IOException {
        BatchResult result = new BatchResult();
        
        log.info("开始批量反编译目录: {}", directory);
        
        // 查找所有CLASS文件
        List<Path> classFiles = findAllClassFiles(directory);
        result.setTotalFiles(classFiles.size());
        
        log.info("发现 {} 个CLASS文件", classFiles.size());
        
        if (classFiles.isEmpty()) {
            return result;
        }

        long startTime = System.currentTimeMillis();
        
        if (useMultiThread) {
            decompileWithThreadPool(classFiles, result, progressCallback);
        } else {
            decompileSingleThread(classFiles, result, progressCallback);
        }
        
        long duration = System.currentTimeMillis() - startTime;
        result.setDuration(duration);
        
        log.info("批量反编译完成 - 耗时: {}ms, 成功: {}, 失败: {}", 
                duration, result.getSuccessCount(), result.getFailCount());
        
        return result;
    }

    /**
     * 单线程反编译
     */
    private void decompileSingleThread(List<Path> classFiles, BatchResult result, ProgressCallback progressCallback) {
        int current = 0;
        int total = classFiles.size();
        
        for (Path classFile : classFiles) {
            current++;
            try {
                if (progressCallback != null) {
                    progressCallback.onProgress(current, total, classFile.getFileName().toString());
                }
                
                String sourceCode = decompilerService.decompileClass(classFile.toFile());
                
                // 保存反编译结果
                Path javaFile = getJavaFilePath(classFile);
                Files.createDirectories(javaFile.getParent());
                Files.writeString(javaFile, sourceCode);
                
                result.incrementSuccess();
                result.addDecompiledFile(classFile.toString(), javaFile.toString());
                
            } catch (Exception e) {
                log.error("反编译失败: {}", classFile, e);
                result.incrementFail();
                result.addError(classFile.toString(), e.getMessage());
            }
        }
    }

    /**
     * 多线程反编译
     */
    private void decompileWithThreadPool(List<Path> classFiles, BatchResult result, ProgressCallback progressCallback) {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        CountDownLatch latch = new CountDownLatch(classFiles.size());
        
        log.info("使用 {} 个线程进行并发反编译", THREAD_POOL_SIZE);
        
        int total = classFiles.size();
        for (Path classFile : classFiles) {
            executor.submit(() -> {
                try {
                    String sourceCode = decompilerService.decompileClass(classFile.toFile());
                    
                    // 保存反编译结果
                    Path javaFile = getJavaFilePath(classFile);
                    synchronized (result) {
                        Files.createDirectories(javaFile.getParent());
                        Files.writeString(javaFile, sourceCode);
                    }
                    
                    result.incrementSuccess();
                    result.addDecompiledFile(classFile.toString(), javaFile.toString());
                    
                    // 进度回调
                    if (progressCallback != null) {
                        int current = result.getSuccessCount() + result.getFailCount();
                        progressCallback.onProgress(current, total, classFile.getFileName().toString());
                    }
                    
                } catch (Exception e) {
                    log.error("反编译失败: {}", classFile, e);
                    result.incrementFail();
                    result.addError(classFile.toString(), e.getMessage());
                    
                    // 进度回调
                    if (progressCallback != null) {
                        int current = result.getSuccessCount() + result.getFailCount();
                        progressCallback.onProgress(current, total, classFile.getFileName().toString());
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        try {
            latch.await();
        } catch (InterruptedException e) {
            log.error("等待反编译任务完成时被中断", e);
            Thread.currentThread().interrupt();
        } finally {
            executor.shutdown();
        }
    }

    /**
     * 查找所有CLASS文件
     */
    private List<Path> findAllClassFiles(Path directory) throws IOException {
        List<Path> classFiles = new ArrayList<>();
        
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.filter(Files::isRegularFile)
                 .filter(path -> path.toString().endsWith(".class"))
                 .forEach(classFiles::add);
        }
        
        return classFiles;
    }

    /**
     * 获取Java文件路径（将.class替换为.java）
     */
    private Path getJavaFilePath(Path classFile) {
        String classPath = classFile.toString();
        String javaPath = classPath.substring(0, classPath.length() - 6) + ".java";
        return Path.of(javaPath);
    }

    /**
     * 批量反编译结果
     */
    public static class BatchResult {
        private int totalFiles = 0;
        private int successCount = 0;
        private int failCount = 0;
        private long duration = 0;
        private final List<DecompiledFile> decompiledFiles = new ArrayList<>();
        private final List<ErrorFile> errorFiles = new ArrayList<>();

        public synchronized void incrementSuccess() {
            successCount++;
        }

        public synchronized void incrementFail() {
            failCount++;
        }

        public synchronized void addDecompiledFile(String classFile, String javaFile) {
            decompiledFiles.add(new DecompiledFile(classFile, javaFile));
        }

        public synchronized void addError(String classFile, String error) {
            errorFiles.add(new ErrorFile(classFile, error));
        }

        public void setTotalFiles(int total) { this.totalFiles = total; }
        public void setDuration(long duration) { this.duration = duration; }
        
        public int getTotalFiles() { return totalFiles; }
        public int getSuccessCount() { return successCount; }
        public int getFailCount() { return failCount; }
        public long getDuration() { return duration; }
        public List<DecompiledFile> getDecompiledFiles() { return decompiledFiles; }
        public List<ErrorFile> getErrorFiles() { return errorFiles; }

        @Override
        public String toString() {
            return String.format(
                "批量反编译结果:\n总文件数: %d\n成功: %d\n失败: %d\n耗时: %dms\n平均速度: %.2f 文件/秒",
                totalFiles, successCount, failCount, duration, 
                duration > 0 ? (successCount * 1000.0 / duration) : 0
            );
        }
    }

    public static class DecompiledFile {
        private final String classFile;
        private final String javaFile;

        public DecompiledFile(String classFile, String javaFile) {
            this.classFile = classFile;
            this.javaFile = javaFile;
        }

        public String getClassFile() { return classFile; }
        public String getJavaFile() { return javaFile; }
    }

    public static class ErrorFile {
        private final String classFile;
        private final String error;

        public ErrorFile(String classFile, String error) {
            this.classFile = classFile;
            this.error = error;
        }

        public String getClassFile() { return classFile; }
        public String getError() { return error; }
    }
    
    /**
     * 进度回调接口
     */
    @FunctionalInterface
    public interface ProgressCallback {
        void onProgress(int current, int total, String fileName);
    }
}

package com.classviewer.example;

/**
 * ClassViewer使用示例
 * 
 * 这个类演示了如何使用ClassViewer进行代码审计
 */
public class UsageExample {
    
    /**
     * 使用流程：
     * 
     * 1. 递归解压JAR包
     *    - 点击"递归解压JAR"按钮
     *    - 选择要分析的主JAR文件
     *    - 选择输出目录
     *    - 系统会自动：
     *      a. 解压主JAR
     *      b. 扫描解压后的目录，找出所有嵌套的JAR
     *      c. 过滤掉白名单中的依赖（如spring、mybatis等）
     *      d. 递归解压业务相关的JAR包
     *      e. 重复上述过程，直到没有新的JAR包
     * 
     * 2. 批量反编译
     *    - 点击"批量反编译"按钮
     *    - 选择解压后的目录
     *    - 选择是否使用多线程（推荐）
     *    - 系统会：
     *      a. 扫描目录中的所有.class文件
     *      b. 使用CFR引擎批量反编译
     *      c. 在原class文件旁边生成对应的.java文件
     *      d. 显示反编译统计信息
     * 
     * 3. 查看反编译结果
     *    - 打开解压目录
     *    - 每个.class文件旁边都有对应的.java源码
     *    - 可以使用IDE打开查看
     * 
     * 4. 白名单管理
     *    - 点击"白名单设置"查看当前白名单
     *    - 修改 src/main/resources/jar-whitelist.txt 来自定义白名单
     *    - 支持的格式：
     *      # 注释行
     *      spring-        # 匹配所有包含"spring-"的JAR
     *      mybatis        # 精确匹配
     * 
     * 代码审计建议：
     * 
     * 1. 关注业务代码
     *    - 白名单会自动过滤常见框架
     *    - 重点审计业务逻辑代码
     * 
     * 2. 查找安全问题
     *    - SQL注入：搜索字符串拼接SQL的代码
     *    - XSS漏洞：搜索未转义的输出
     *    - 文件操作：检查路径遍历风险
     *    - 反序列化：查找readObject等危险操作
     * 
     * 3. 批量搜索
     *    - 解压后的目录可以用grep等工具批量搜索
     *    - 示例：grep -r "Runtime.getRuntime" .
     *    - 示例：grep -r "ProcessBuilder" .
     * 
     * 性能优化建议：
     * 
     * 1. 大型项目
     *    - 使用SSD硬盘存储解压结果
     *    - 批量反编译时选择多线程
     *    - 根据需要调整白名单，减少不必要的解压
     * 
     * 2. 白名单配置
     *    - 添加项目特定的第三方依赖到白名单
     *    - 例如：aliyun-sdk、tencent-cloud等
     * 
     * 3. 内存管理
     *    - 处理超大JAR时，可能需要增加JVM堆内存
     *    - 运行时添加参数：-Xmx4g
     */
    
    // 示例：程序化使用（非GUI）
    public void programmaticExample() {
        /*
        // 1. 注入服务
        @Autowired
        private JarExtractorService jarExtractorService;
        
        @Autowired
        private BatchDecompilerService batchDecompilerService;
        
        // 2. 解压JAR
        File jarFile = new File("target.jar");
        Path outputDir = Paths.get("output");
        ExtractionResult result = jarExtractorService.extractJarRecursively(jarFile, outputDir);
        System.out.println(result);
        
        // 3. 批量反编译
        BatchResult batchResult = batchDecompilerService.batchDecompile(outputDir, true);
        System.out.println(batchResult);
        
        // 4. 自定义白名单
        jarExtractorService.addWhitelistPattern("custom-lib-");
        */
    }
}

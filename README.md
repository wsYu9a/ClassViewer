# ClassViewer

一个基于 SpringBoot + JavaFX 开发的 Java 反编译工具。

## 功能特性

- ✅ **递归解压嵌套JAR包** - 自动识别并解压JAR中的JAR，适合代码审计
- ✅ **智能白名单过滤** - 自动过滤Spring、MyBatis等常见框架依赖
- ✅ **批量反编译** - 支持多线程批量反编译整个目录的CLASS文件
- ✅ 支持反编译单个JAR文件
- ✅ 支持反编译单个CLASS文件
- ✅ 图形化界面，操作简单直观
- ✅ 使用CFR反编译引擎（高质量反编译输出）

## 技术栈

- **SpringBoot 3.2.1** - 依赖注入框架
- **JavaFX 21** - 图形界面
- **CFR** - 反编译引擎（高质量输出）
- **Lombok** - 简化代码
- **Maven** - 项目管理

## 项目结构

```
ClassViewer/
├── src/main/java/com/classviewer/
│   ├── ClassViewerApplication.java      # 主启动类
│   ├── ui/
│   │   ├── JavaFxApplication.java       # JavaFX应用类
│   │   └── MainViewController.java      # 主界面控制器
│   └── service/
│       └── DecompilerService.java       # 反编译服务
├── src/main/resources/
│   ├── application.properties           # 应用配置
│   └── logback.xml                      # 日志配置
└── pom.xml                              # Maven配置
```

## 快速开始

### 一键审计JAR包

```bash
# 1. 启动应用
mvn clean javafx:run

# 2. 在界面中操作
# - 点击"递归解压JAR" → 选择目标JAR → 选择输出目录
# - 等待解压完成（会自动过滤框架依赖）
# - 点击"批量反编译" → 选择解压目录 → 选择"是"使用多线程
# - 完成！所有业务代码已反编译为.java文件
```

## 运行要求

- JDK 17 或更高版本
- Maven 3.6+

## 如何运行

### 方式一：使用 Maven 运行

```bash
mvn clean javafx:run
```

### 方式二：使用 IDE 运行

1. 在 IntelliJ IDEA 中打开项目
2. 等待 Maven 依赖下载完成
3. 运行 `ClassViewerApplication` 类的 main 方法

### 方式三：打包后运行

```bash
mvn clean package
java -jar target/ClassViewer-1.0.0.jar
```

## 使用说明

### 代码审计工作流程（推荐）

**场景：** 你拿到一个待审计的JAR包，想快速获取所有业务代码的源码

1. **递归解压JAR包**
   - 点击 "递归解压JAR" 按钮
   - 选择待审计的JAR文件
   - 选择输出目录（建议使用SSD）
   - 系统会自动：
     * 解压主JAR包
     * 扫描并识别所有嵌套的JAR包
     * 过滤白名单中的框架依赖（Spring、MyBatis等）
     * 递归解压业务相关的JAR
     * 持续处理直到没有新的JAR包
   - 查看解压统计信息

2. **批量反编译**
   - 点击 "批量反编译" 按钮
   - 选择刚才解压的输出目录
   - 选择 "是" 启用多线程加速
   - 系统会批量反编译所有CLASS文件
   - 每个.class文件旁边生成对应的.java源码
   - 查看反编译统计

3. **代码审计**
   - 使用IDE打开解压后的目录
   - 所有源码已经准备就绪
   - 使用grep等工具批量搜索敏感代码：
     ```bash
     # 搜索SQL注入风险
     grep -r "+ \"SELECT" .
     
     # 搜索命令执行
     grep -r "Runtime.getRuntime" .
     grep -r "ProcessBuilder" .
     
     # 搜索文件操作
     grep -r "new File(" .
     ```

### 基础功能

1. **打开JAR文件** - 浏览JAR内容，单个文件反编译
2. **打开CLASS文件** - 快速反编译单个CLASS
3. **打开文件夹** - 浏览文件夹中的CLASS文件
4. **白名单设置** - 查看和自定义过滤规则
5. **清空** - 清除当前加载的内容

### 白名单配置

白名单用于过滤常见的框架依赖，只保留业务代码。

- 配置文件：`src/main/resources/jar-whitelist.txt`
- 格式：每行一个关键词，支持部分匹配
- 示例：
  ```
  # Spring框架
  spring-
  springframework
  
  # 数据库
  mybatis
  druid-
  ```
- 内置白名单包括：Spring、MyBatis、Jackson、Logback、Tomcat、Netty等

## 开发计划

- [x] 递归解压嵌套JAR包
- [x] 白名单过滤机制
- [x] 批量反编译（多线程）
- [ ] 代码语法高亮
- [ ] 搜索功能（跨文件搜索）
- [ ] 导出反编译结果为独立项目
- [ ] 反编译进度实时显示
- [ ] 支持更多反编译引擎切换

## 许可证

MIT License

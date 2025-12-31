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

- **SpringBoot 2.7.18** - 依赖注入框架
- **JavaFX 11.0.2** - 图形界面
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

## 运行要求

- JDK 1.8 或更高版本
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

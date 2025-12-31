package com.classviewer.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * JAR包解压服务
 * 支持递归解压嵌套的JAR包
 */
@Slf4j
@Service
public class JarExtractorService {

    // 白名单：这些依赖包会被排除（不解压）
    private static final Set<String> JAR_WHITELIST = new HashSet<>();
    private static final String WHITELIST_FILE = "jar-whitelist.txt";
    private static Path whitelistFilePath;
    
    static {
        // 从配置文件加载白名单
        loadWhitelistFromFile();
    }
    
    /**
     * 从配置文件加载白名单
     */
    private static void loadWhitelistFromFile() {
        try {
            // 先尝试加载resources中的文件
            ClassPathResource resource = new ClassPathResource(WHITELIST_FILE);
            if (resource.exists()) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(resource.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        // 跳过空行和注释
                        if (!line.isEmpty() && !line.startsWith("#")) {
                            JAR_WHITELIST.add(line);
                        }
                    }
                    log.info("从配置文件加载白名单，共 {} 条规则", JAR_WHITELIST.size());
                }
            } else {
                // 如果配置文件不存在，使用默认白名单
                loadDefaultWhitelist();
            }
            
            // 设置用户目录下的持久化文件路径
            String userHome = System.getProperty("user.home");
            whitelistFilePath = Paths.get(userHome, ".classviewer", WHITELIST_FILE);
            
            // 如果用户目录下有自定义白名单，合并加载
            if (Files.exists(whitelistFilePath)) {
                loadUserWhitelist();
            } else {
                // 创建默认的用户白名单文件
                saveWhitelistToFile();
            }
        } catch (Exception e) {
            log.warn("加载白名单配置文件失败，使用默认配置", e);
            loadDefaultWhitelist();
        }
    }
    
    /**
     * 加载用户自定义白名单
     */
    private static void loadUserWhitelist() {
        try {
            List<String> lines = Files.readAllLines(whitelistFilePath);
            int addedCount = 0;
            for (String line : lines) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    if (JAR_WHITELIST.add(line)) {
                        addedCount++;
                    }
                }
            }
            log.info("从用户配置加载 {} 条新规则，总计 {} 条", addedCount, JAR_WHITELIST.size());
        } catch (IOException e) {
            log.error("加载用户白名单失败", e);
        }
    }
    
    /**
     * 保存白名单到文件
     */
    private static void saveWhitelistToFile() {
        try {
            // 创建目录
            Files.createDirectories(whitelistFilePath.getParent());
            
            // 写入文件
            List<String> lines = new ArrayList<>();
            lines.add("# ClassViewer JAR过滤白名单");
            lines.add("# 包含以下关键词的JAR包将被跳过（不解压、不反编译）");
            lines.add("# 每行一个关键词，支持部分匹配");
            lines.add("# 修改后重启应用生效，或使用界面动态添加");
            lines.add("");
            
            // 按字母顺序排序
            List<String> sortedRules = new ArrayList<>(JAR_WHITELIST);
            Collections.sort(sortedRules);
            lines.addAll(sortedRules);
            
            Files.write(whitelistFilePath, lines, StandardOpenOption.CREATE, 
                       StandardOpenOption.TRUNCATE_EXISTING);
            log.info("白名单已保存到: {}", whitelistFilePath);
        } catch (IOException e) {
            log.error("保存白名单失败", e);
        }
    }
    
    /**
     * 加载默认白名单
     */
//    private static void loadDefaultWhitelist() {
//        JAR_WHITELIST.addAll(Arrays.asList(
//                "spring-", "springframework",
//                "mybatis", "mybatis-spring",
//                "jackson-", "fastjson",
//                "slf4j-", "logback-", "log4j-",
//                "junit", "mockito",
//                "tomcat-", "jetty-",
//                "netty-", "httpclient",
//                "commons-", "guava-",
//                "lombok-", "aspectj",
//                "hibernate-", "druid-",
//                "jedis-", "redisson-",
//                "kafka-", "rabbitmq-",
//                "mysql-connector", "postgresql-",
//                "servlet-api", "validation-api"
//        ));
//    }
    private static void loadDefaultWhitelist() {
        JAR_WHITELIST.addAll(Arrays.asList(
                // ==== Java/JDK 运行时 ====
                "rt-", "tools-", "dt-", "javaws-", "deploy-", "jfxrt-", "jfr-", "jaccess-",
                "jconsole-", "jce-", "jsse-", "sunrsasign-", "localedata-", "dnsns-", "zipfs-",
                "sunec-", "sunjce_provider-", "sunpkcs11-", "sunmscapi-", "charsets-", "cldrdata-",
                "nashorn-", "junit-", "hamcrest-", "asm-", "cglib-", "javassist-",

                // ==== Web服务器/容器 ====
                "tomcat-", "catalina-", "jasper-", "el-api-", "jsp-api-", "servlet-api-",
                "jetty-", "undertow-", "jboss-", "wildfly-", "weblogic-", "websphere-",

                // ==== Spring全家桶 ====
                "spring-", "springframework", "spring-boot-", "spring-cloud-", "spring-security-",
                "spring-data-", "spring-session-", "spring-amqp-", "spring-batch-", "spring-integration-",

                // ==== ORM框架 ====
                "mybatis-", "mybatis-plus-", "hibernate-", "hibernate-validator-", "hibernate-search-",
                "eclipselink-", "openjpa-", "querydsl-", "jpa-", "ejb-", "jta-",

                // ==== 数据库相关 ====
                "mysql-connector-", "mariadb-", "ojdbc", "orai18n-", "xdb-", "xmlparserv2-",
                "postgresql-", "mssql-", "sqljdbc", "h2-", "derby-", "hsqldb-", "sqlite-",
                "hikaricp-", "druid-", "bonecp-", "c3p0-", "proxool-", "dbcp-", "dbcp2-",
                "dbutils-", "jdbctemplate-", "jdbc-", "jdo-", "jpox-",

                // ==== 连接池/数据源 ====
                "commons-dbcp", "tomcat-jdbc-", "dbcp-", "dbcp2-", "bonecp-", "c3p0-",
                "proxool-", "druid-", "hikaricp-",

                // ==== 日志框架 ====
                "slf4j-", "logback-", "log4j-", "log4j2-", "commons-logging-", "jcl-",
                "jul-", "tinylog-", "log4j-api-", "log4j-core-", "log4j-web-", "log4j-slf4j-",
                "log4j-1.2-api-", "log4j-to-slf4j-", "jcl-over-slf4j-", "jul-to-slf4j-",

                // ==== JSON处理 ====
                "jackson-", "fastjson-", "gson-", "json-lib-", "json-smart-", "jsonpath-",
                "json-simple-", "json-io-", "moshi-", "json-", "jsonp-", "javax.json-",
                "johnzon-", "yasson-", "jsonb-api-", "jsonb-runtime-",

                // ==== 序列化框架 ====
                "protobuf-", "protostuff-", "kryo-", "fst-", "hessian-", "java-serialization-",
                "avro-", "thrift-", "msgpack-", "cbor-", "smile-", "xml-", "yaml-",
                "xstream-", "jaxb-", "jaxb-api-", "jaxb-impl-", "jaxb-runtime-",

                // ==== 工具库 ====
                "commons-", "guava-", "guice-", "caffeine-", "ehcache-", "cache-api-", "javax.cache-",
                "reflections-", "javapoet-", "auto-service-", "auto-value-", "auto-common-",
                "jodd-", "vavr-", "jool-", "streamex-", "cyclops-", "functionaljava-",

                // ==== Apache Commons ====
                "commons-beanutils", "commons-codec", "commons-collections", "commons-compress",
                "commons-configuration", "commons-dbcp", "commons-digester", "commons-fileupload",
                "commons-io", "commons-jxpath", "commons-lang", "commons-logging", "commons-math",
                "commons-net", "commons-pool", "commons-text", "commons-validator", "commons-vfs",
                "commons-cli", "commons-csv", "commons-email", "commons-exec", "commons-jci",
                "commons-jexl", "commons-jxpath", "commons-modeler", "commons-weaver",

                // ==== 测试框架 ====
                "junit-", "testng-", "mockito-", "powermock-", "easymock-", "jmock-", "jmockit-",
                "assertj-", "hamcrest-", "rest-assured-", "wiremock-", "mockserver-",
                "awaitility-", "fest-assert-", "truth-", "spock-", "cucumber-", "jbehave-",
                "selenium-", "testcontainers-", "jacoco-", "jmeter-", "jprofiler-",

                // ==== 构建工具 ====
                "maven-", "gradle-", "ant-", "ivy-", "nexus-", "artifactory-", "jenkins-",
                "plexus-", "wagon-", "aether-", "maven-plugin-", "maven-core-", "maven-model-",

                // ==== HTTP客户端 ====
                "httpclient-", "httpcore-", "httpmime-", "fluent-hc-", "okhttp-", "okio-",
                "retrofit-", "feign-", "ribbon-", "resttemplate-", "webclient-", "async-http-",
                "unirest-", "rest-assured-", "jersey-client-", "resteasy-client-",

                // ==== 网络框架 ====
                "netty-", "mina-", "grizzly-", "xnio-", "undertow-", "vertx-", "akka-",
                "grpc-", "thrift-", "protobuf-", "avro-", "zmq-", "zeromq-", "kryonet-",

                // ==== 消息队列 ====
                "kafka-", "kafka-clients-", "rabbitmq-", "amqp-client-", "rocketmq-", "activemq-",
                "artemis-", "qpid-", "zeromq-", "nsq-", "nats-", "pulsar-", "beanstalk-",
                "mqtt-", "stomp-", "amqp-", "jms-", "javax.jms-",

                // ==== 缓存框架 ====
                "ehcache-", "caffeine-", "guava-", "redis-", "jedis-", "lettuce-", "redisson-",
                "memcached-", "xmemcached-", "spymemcached-", "infinispan-", "hazelcast-",
                "ignite-", "geode-", "cache2k-", "cache-api-", "javax.cache-",

                // ==== 安全框架 ====
                "shiro-", "spring-security-", "keycloak-", "oauth-", "openid-", "saml-",
                "jwt-", "jjwt-", "nimbus-jose-jwt-", "pac4j-", "apache-shiro-", "bouncycastle-",
                "bcprov-", "bcmail-", "bcpkix-", "bcpg-", "bcutil-", "tink-", "google-tink-",

                // ==== 模板引擎 ====
                "thymeleaf-", "freemarker-", "velocity-", "groovy-", "jsp-", "jstl-",
                "mustache-", "handlebars-", "pebble-", "jte-", "jtwig-", "rythm-",

                // ==== 验证框架 ====
                "hibernate-validator-", "javax.validation-", "validation-api-", "oval-",
                "apache-bval-", "jsr-", "bean-validation-",

                // ==== 配置框架 ====
                "apollo-", "nacos-", "consul-", "zookeeper-", "etcd-", "archaius-",
                "spring-cloud-config-", "spring-cloud-consul-", "spring-cloud-zookeeper-",

                // ==== 调度框架 ====
                "quartz-", "xxl-job-", "elastic-job-", "saturn-", "spring-scheduler-",
                "spring-batch-", "spring-integration-", "spring-cloud-task-",

                // ==== 搜索引擎 ====
                "elasticsearch-", "lucene-", "solr-", "sphinx-", "hibernate-search-",
                "spring-data-elasticsearch-", "jest-", "rest-high-level-client-",

                // ==== 大数据 ====
                "hadoop-", "hbase-", "hive-", "spark-", "flink-", "storm-", "kylin-",
                "kafka-", "zookeeper-", "flume-", "sqoop-", "oozie-", "azkaban-",

                // ==== 微服务框架 ====
                "spring-cloud-", "dubbo-", "dubbo-spring-boot-", "sofa-", "motan-", "grpc-",
                "thrift-", "brpc-", "tars-", "servicecomb-", "spring-cloud-alibaba-",

                // ==== 监控/追踪 ====
                "micrometer-", "prometheus-", "zipkin-", "sleuth-", "skywalking-", "pinpoint-",
                "cat-", "jaeger-", "opentracing-", "opencensus-", "javamelody-", "metrics-",
                "dropwizard-metrics-", "influxdb-", "grafana-",

                // ==== 部署/容器 ====
                "docker-", "docker-java-", "kubernetes-", "kubernetes-client-", "fabric8-",
                "helm-", "istio-", "linkerd-", "consul-", "etcd-", "zookeeper-",

                // ==== 文档/API ====
                "swagger-", "openapi-", "springfox-", "knife4j-", "smart-doc-", "asciidoctor-",
                "asciidoctorj-", "markdown-", "commonmark-", "flexmark-", "pegdown-",

                // ==== 前端相关 ====
                "thymeleaf-", "freemarker-", "velocity-", "jsp-", "jstl-", "webjars-",
                "bootstrap-", "jquery-", "vue-", "react-", "angular-", "webpack-",

                // ==== 工具/插件 ====
                "lombok-", "mapstruct-", "querydsl-", "immutables-", "auto-", "bytebuddy-",
                "asm-", "cglib-", "javassist-", "btrace-", "arthas-", "greys-", "jol-",
                "jmh-", "junit-", "testng-", "mockito-", "powermock-", "easymock-",

                // ==== 邮件 ====
                "javax.mail-", "javax.mail-api-", "javax.activation-", "activation-",
                "spring-mail-", "commons-email-", "simplejavamail-", "greenmail-",

                // ==== 文件处理 ====
                "poi-", "poi-ooxml-", "easypoi-", "alibaba-easyexcel-", "jexcelapi-",
                "itext-", "pdfbox-", "fop-", "barcode4j-", "zxing-", "tess4j-", "im4java-",

                // ==== 图片处理 ====
                "thumbnailator-", "imgscalr-", "imagej-", "javax.imageio-", "batik-",
                "jmagick-", "im4java-", "metadata-extractor-", "xmpcore-",

                // ==== 音视频处理 ====
                "ffmpeg-", "jcodec-", "xuggle-", "vlcj-", "jave-", "javacv-", "opencv-",

                // ==== 地理位置 ====
                "geotools-", "jts-", "spatial4j-", "geolatte-", "proj4j-", "osmosis-",

                // ==== 科学计算 ====
                "ejml-", "ujmp-", "nd4j-", "deeplearning4j-", "tablesaw-", "smile-",
                "tensorflow-", "pytorch-", "dl4j-", "weka-", "rapidminer-", "knime-",

                // ==== 游戏开发 ====
                "libgdx-", "jmonkeyengine-", "slick2d-", "lwjgl-", "jinput-", "jorbis-",

                // ==== 区块链 ====
                "web3j-", "bitcoinj-", "ethereumj-", "hyperledger-", "fabric-", "corda-",

                // ==== AI/机器学习 ====
                "tensorflow-", "pytorch-", "deeplearning4j-", "smile-", "weka-", "rapidminer-",
                "knime-", "h2o-", "mahout-", "spark-mllib-", "mxnet-", "caffe-",

                // ==== 特殊协议 ====
                "ftp-", "sftp-", "ssh-", "smtp-", "pop3-", "imap-", "ldap-", "kerberos-",
                "ntlm-", "oauth-", "saml-", "openid-", "jwt-", "jwe-", "jws-",

                // ==== 硬件/物联网 ====
                "pi4j-", "dio-", "jserialcomm-", "rxtx-", "jssc-", "usb4java-", "bluecove-",

                // ==== 企业应用 ====
                "activiti-", "camunda-", "flowable-", "jbpm-", "drools-", "ruleengine-",
                "jreport-", "ireport-", "jasperreports-", "birt-", "pentaho-", "spoon-",

                // ==== 云原生 ====
                "spring-cloud-", "spring-cloud-alibaba-", "dubbo-", "servicecomb-",
                "kubernetes-", "istio-", "linkerd-", "consul-", "etcd-", "zookeeper-",

                // ==== 代码生成 ====
                "freemarker-", "velocity-", "beetl-", "enjoy-", "jfinal-template-",
                "thymeleaf-", "groovy-", "jsp-", "jstl-", "stringtemplate-",

                // ==== 其他知名框架 ====
                "jfinal-", "nutz-", "solon-", "jboot-", "blade-", "actframework-",
                "jooby-", "ratpack-", "vertx-", "play-", "grails-", "micronaut-",
                "quarkus-", "helidon-", "javalin-", "sparkjava-", "ninja-"

        ));
    }

    /**
     * 递归解压JAR包
     * @param jarFile 要解压的JAR文件
     * @param outputDir 输出目录
     * @return 解压统计信息
     */
    public ExtractionResult extractJarRecursively(File jarFile, Path outputDir) throws IOException {
        ExtractionResult result = new ExtractionResult();
        
        if (!jarFile.exists()) {
            throw new FileNotFoundException("JAR文件不存在: " + jarFile.getAbsolutePath());
        }

        log.info("开始递归解压JAR: {}", jarFile.getName());
        
        // 创建输出目录
        Files.createDirectories(outputDir);
        
        // 第一层解压
        Path extractPath = outputDir.resolve(removeJarExtension(jarFile.getName()));
        extractJar(jarFile, extractPath, result);
        
        // 递归处理嵌套的JAR
        processNestedJars(extractPath, result, 1);
        
        log.info("JAR解压完成 - 总JAR数: {}, 已解压: {}, 已跳过: {}, CLASS文件: {}", 
                result.getTotalJars(), result.getExtractedJars(), result.getSkippedJars(), result.getClassFiles());
        
        return result;
    }

    /**
     * 递归处理嵌套的JAR包
     */
    private void processNestedJars(Path directory, ExtractionResult result, int depth) throws IOException {
        if (depth > 10) {
            log.warn("递归深度超过10层，停止解压: {}", directory);
            return;
        }

        List<Path> jarFiles = findJarFiles(directory);
        
        if (jarFiles.isEmpty()) {
            log.debug("目录 {} 中没有发现JAR文件", directory);
            return;
        }

        log.info("在第{}层发现 {} 个JAR文件", depth, jarFiles.size());
        
        for (Path jarPath : jarFiles) {
            String jarName = jarPath.getFileName().toString();
            
            // 检查是否在白名单中（需要跳过）
            if (isInWhitelist(jarName)) {
                log.debug("跳过白名单JAR: {}", jarName);
                result.addSkippedJar(jarName);
                continue;
            }

            try {
                // 解压嵌套的JAR
                Path nestedExtractPath = jarPath.getParent().resolve(removeJarExtension(jarName));
                
                // 避免重复解压
                if (Files.exists(nestedExtractPath)) {
                    log.debug("目录已存在，跳过: {}", nestedExtractPath);
                    continue;
                }
                
                extractJar(jarPath.toFile(), nestedExtractPath, result);
                
                // 删除原JAR文件（可选）
                Files.delete(jarPath);
                
                // 继续递归处理
                processNestedJars(nestedExtractPath, result, depth + 1);
                
            } catch (Exception e) {
                log.error("解压嵌套JAR失败: {}", jarName, e);
                result.addError(jarName, e.getMessage());
            }
        }
    }

    /**
     * 解压单个JAR文件
     */
    private void extractJar(File jarFile, Path outputPath, ExtractionResult result) throws IOException {
        Files.createDirectories(outputPath);
        result.incrementTotalJars();

        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                Path entryPath = outputPath.resolve(entry.getName());

                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    // 创建父目录
                    Files.createDirectories(entryPath.getParent());
                    
                    // 复制文件
                    try (InputStream is = jar.getInputStream(entry)) {
                        Files.copy(is, entryPath, StandardCopyOption.REPLACE_EXISTING);
                        
                        // 统计CLASS文件
                        if (entry.getName().endsWith(".class")) {
                            result.incrementClassFiles();
                        }
                    }
                }
            }
            
            result.addExtractedJar(jarFile.getName());
            log.info("已解压: {} -> {}", jarFile.getName(), outputPath.getFileName());
        }
    }

    /**
     * 查找目录中的所有JAR文件
     */
    private List<Path> findJarFiles(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return Collections.emptyList();
        }

        try (Stream<Path> paths = Files.walk(directory)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().toLowerCase().endsWith(".jar"))
                    .collect(Collectors.toList());
        }
    }

    /**
     * 检查JAR名称是否在白名单中
     */
    private boolean isInWhitelist(String jarName) {
        String lowerName = jarName.toLowerCase();
        return JAR_WHITELIST.stream().anyMatch(lowerName::contains);
    }

    /**
     * 移除.jar扩展名
     */
    private String removeJarExtension(String fileName) {
        if (fileName.toLowerCase().endsWith(".jar")) {
            return fileName.substring(0, fileName.length() - 4);
        }
        return fileName;
    }

    /**
     * 添加自定义白名单规则
     */
    public synchronized void addWhitelistPattern(String pattern) {
        if (pattern == null || pattern.trim().isEmpty()) {
            return;
        }
        pattern = pattern.trim();
        if (JAR_WHITELIST.add(pattern)) {
            log.info("已添加白名单规则: {}", pattern);
            saveWhitelistToFile();
        }
    }
    
    /**
     * 批量添加白名单规则
     */
    public synchronized void addWhitelistPatterns(List<String> patterns) {
        int addedCount = 0;
        for (String pattern : patterns) {
            if (pattern != null && !pattern.trim().isEmpty()) {
                pattern = pattern.trim();
                if (JAR_WHITELIST.add(pattern)) {
                    addedCount++;
                }
            }
        }
        if (addedCount > 0) {
            log.info("已添加 {} 条白名单规则", addedCount);
            saveWhitelistToFile();
        }
    }

    /**
     * 移除白名单规则
     */
    public synchronized void removeWhitelistPattern(String pattern) {
        if (JAR_WHITELIST.remove(pattern)) {
            log.info("已移除白名单规则: {}", pattern);
            saveWhitelistToFile();
        }
    }
    
    /**
     * 清空所有白名单规则
     */
    public synchronized void clearWhitelist() {
        JAR_WHITELIST.clear();
        log.info("已清空白名单");
        saveWhitelistToFile();
    }
    
    /**
     * 重置为默认白名单
     */
    public synchronized void resetToDefault() {
        JAR_WHITELIST.clear();
        loadDefaultWhitelist();
        log.info("已重置为默认白名单");
        saveWhitelistToFile();
    }
    
    /**
     * 获取白名单文件路径
     */
    public String getWhitelistFilePath() {
        return whitelistFilePath != null ? whitelistFilePath.toString() : "未知";
    }

    /**
     * 获取当前白名单
     */
    public Set<String> getWhitelist() {
        return new HashSet<>(JAR_WHITELIST);
    }

    /**
     * 解压结果统计
     */
    public static class ExtractionResult {
        private int totalJars = 0;
        private int extractedJars = 0;
        private int skippedJars = 0;
        private int classFiles = 0;
        private final List<String> extractedJarNames = new ArrayList<>();
        private final List<String> skippedJarNames = new ArrayList<>();
        private final Map<String, String> errors = new HashMap<>();

        public void incrementTotalJars() {
            totalJars++;
        }

        public void incrementClassFiles() {
            classFiles++;
        }

        public void addExtractedJar(String name) {
            extractedJars++;
            extractedJarNames.add(name);
        }

        public void addSkippedJar(String name) {
            skippedJars++;
            skippedJarNames.add(name);
        }

        public void addError(String jar, String error) {
            errors.put(jar, error);
        }

        public int getTotalJars() { return totalJars; }
        public int getExtractedJars() { return extractedJars; }
        public int getSkippedJars() { return skippedJars; }
        public int getClassFiles() { return classFiles; }
        public List<String> getExtractedJarNames() { return extractedJarNames; }
        public List<String> getSkippedJarNames() { return skippedJarNames; }
        public Map<String, String> getErrors() { return errors; }

        @Override
        public String toString() {
            return String.format(
                "解压统计:\n总JAR数: %d\n已解压: %d\n已跳过: %d\nCLASS文件: %d\n错误: %d",
                totalJars, extractedJars, skippedJars, classFiles, errors.size()
            );
        }
    }
}

package com.classviewer.ui;

import com.classviewer.ClassViewerApplication;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * JavaFX应用启动类
 * 负责初始化SpringBoot上下文和JavaFX舞台
 */
public class JavaFxApplication extends Application {

    private ConfigurableApplicationContext springContext;

    @Override
    public void init() {
        // 初始化Spring上下文
        springContext = new SpringApplicationBuilder(ClassViewerApplication.class)
                .run(getParameters().getRaw().toArray(new String[0]));
    }

    @Override
    public void start(Stage primaryStage) {
        // 从Spring容器中获取主界面控制器
        MainViewController mainViewController = springContext.getBean(MainViewController.class);
        mainViewController.show(primaryStage);
    }

    @Override
    public void stop() {
        // 关闭Spring上下文
        springContext.close();
        Platform.exit();
    }
}

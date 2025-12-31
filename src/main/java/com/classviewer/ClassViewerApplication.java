package com.classviewer;

import com.classviewer.ui.JavaFxApplication;
import javafx.application.Application;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ClassViewer主启动类
 * 结合SpringBoot和JavaFX
 */
@SpringBootApplication
public class ClassViewerApplication {

    public static void main(String[] args) {
        // 启动JavaFX应用，SpringBoot会在JavaFX应用中初始化
        Application.launch(JavaFxApplication.class, args);
    }
}

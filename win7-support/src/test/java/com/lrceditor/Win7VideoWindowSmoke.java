package com.lrceditor;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;

public class Win7VideoWindowSmoke extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage owner) {
        try {
            LrcDocument doc = new LrcDocument();
            doc.load(
                    "[00:00.50]镜头推开清晨\n"
                            + "[00:03.20]风把城市唤醒\n"
                            + "[00:06.40]让每句歌词都有回音\n",
                    new File("win7-video-window.lrc"));
            VideoExportWindow window = new VideoExportWindow(
                    owner, doc, null, 9.0, Color.WHITE);
            window.show();

            PauseTransition pause = new PauseTransition(Duration.millis(1000));
            pause.setOnFinished(event -> {
                System.out.println("videoWindowSmoke=passed");
                Platform.exit();
            });
            pause.play();
        } catch (Throwable failure) {
            failure.printStackTrace();
            Platform.exit();
            System.exit(2);
        }
    }
}

package com.lrceditor;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;

public class Win7MediaSmoke extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        try {
            File source = new File(getParameters().getRaw().get(0));
            Media media = new Media(source.toURI().toString());
            MediaPlayer player = new MediaPlayer(media);
            player.setMute(true);
            player.setOnError(() -> {
                player.getError().printStackTrace();
                System.exit(2);
            });
            player.setOnReady(() -> {
                player.play();
                PauseTransition pause = new PauseTransition(Duration.millis(900));
                pause.setOnFinished(event -> {
                    double position = player.getCurrentTime().toSeconds();
                    System.out.println("mediaPosition=" + position
                            + " duration=" + media.getDuration().toSeconds());
                    player.dispose();
                    Platform.exit();
                    if (position <= 0.05) {
                        System.exit(3);
                    }
                });
                pause.play();
            });
        } catch (Throwable failure) {
            failure.printStackTrace();
            Platform.exit();
            System.exit(4);
        }
    }
}

package com.lrceditor;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Method;

public class Win7UiSnapshot extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        stage.setOpacity(0);
        try {
            LrcEditorApp app = new LrcEditorApp();
            app.start(stage);

            java.lang.reflect.Field docField = LrcEditorApp.class.getDeclaredField("doc");
            docField.setAccessible(true);
            LrcDocument doc = (LrcDocument) docField.get(app);
            doc.load(
                    "[00:03.20]夜色沿着城市慢慢落下\n"
                            + "[00:07.45]我把旧日旋律重新点亮\n"
                            + "[00:11.80]每一句等待都有了方向\n"
                            + "[00:16.35]让时间停在最温柔的篇章\n",
                    new File("win7-ui-snapshot.lrc"));

            Method refreshAll = LrcEditorApp.class.getDeclaredMethod("refreshAll");
            refreshAll.setAccessible(true);
            refreshAll.invoke(app);
            java.lang.reflect.Field viewField =
                    LrcEditorApp.class.getDeclaredField("lyricView");
            viewField.setAccessible(true);
            LyricEditorView view = (LyricEditorView) viewField.get(app);
            view.getSelectionModel().clearSelection();
            view.getSelectionModel().select(1);

            PauseTransition pause = new PauseTransition(Duration.millis(800));
            pause.setOnFinished(event -> {
                try {
                    Scene scene = stage.getScene();
                    scene.getRoot().applyCss();
                    scene.getRoot().layout();

                    if (!scene.getRoot().getStyleClass().contains("theme-dark")) {
                        throw new IllegalStateException("default dark theme was not applied");
                    }

                    WritableImage image = scene.snapshot(null);
                    int width = (int) image.getWidth();
                    int height = (int) image.getHeight();
                    int[] pixels = new int[width * height];
                    image.getPixelReader().getPixels(
                            0, 0, width, height,
                            PixelFormat.getIntArgbInstance(),
                            pixels, 0, width);
                    BufferedImage output = new BufferedImage(
                            width, height, BufferedImage.TYPE_INT_ARGB);
                    output.setRGB(0, 0, width, height, pixels, 0, width);
                    File target = new File(getParameters().getRaw().get(0));
                    ImageIO.write(output, "png", target);
                    System.out.println("uiSnapshot=" + target.getAbsolutePath());
                } catch (Throwable failure) {
                    failure.printStackTrace();
                    System.exit(2);
                } finally {
                    stage.close();
                    Platform.exit();
                }
            });
            pause.play();
        } catch (Throwable failure) {
            failure.printStackTrace();
            stage.close();
            Platform.exit();
            System.exit(3);
        }
    }
}

package com.lrceditor;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ListCell;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.prefs.Preferences;

public class InsertRowsVisualCheck extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        stage.setOpacity(0);
        try {
            if (getParameters().getRaw().size() > 3) {
                String requestedTheme = getParameters().getRaw().get(3);
                if ("light".equals(requestedTheme) || "dark".equals(requestedTheme)) {
                    Preferences.userNodeForPackage(LrcEditorApp.class)
                            .put("appearanceTheme", requestedTheme);
                }
            }
            LrcEditorApp app = new LrcEditorApp();
            app.start(stage);
            Field docField = LrcEditorApp.class.getDeclaredField("doc");
            docField.setAccessible(true);
            LrcDocument document = (LrcDocument) docField.get(app);
            document.clear();
            Field viewField = LrcEditorApp.class.getDeclaredField("lyricView");
            viewField.setAccessible(true);
            LyricEditorView view = (LyricEditorView) viewField.get(app);
            Method refresh = LrcEditorApp.class.getDeclaredMethod("refreshAll");
            refresh.setAccessible(true);
            refresh.invoke(app);

            PauseTransition initial = new PauseTransition(Duration.millis(400));
            initial.setOnFinished(event -> {
                try {
                    snapshot(stage, new File(getParameters().getRaw().get(0)));
                    Method insert = LrcEditorApp.class.getDeclaredMethod(
                            "insertNewLines", int.class);
                    insert.setAccessible(true);
                    insert.invoke(app, 1);
                    PauseTransition one = new PauseTransition(Duration.millis(350));
                    one.setOnFinished(e2 -> {
                        try {
                            snapshot(stage,
                                    new File(getParameters().getRaw().get(1)));
                            audit(view);
                            insert.invoke(app, 12);
                            PauseTransition many =
                                    new PauseTransition(Duration.millis(500));
                            many.setOnFinished(e3 -> {
                                try {
                                    snapshot(stage,
                                            new File(getParameters().getRaw().get(2)));
                                    audit(view);
                                    System.out.println("insertRowsVisual=passed");
                                } catch (Throwable failure) {
                                    failure.printStackTrace();
                                    System.exit(2);
                                } finally {
                                    stage.close();
                                    Platform.exit();
                                }
                            });
                            many.play();
                        } catch (Throwable failure) {
                            failure.printStackTrace();
                            System.exit(3);
                        }
                    });
                    one.play();
                } catch (Throwable failure) {
                    failure.printStackTrace();
                    System.exit(4);
                }
            });
            initial.play();
        } catch (Throwable failure) {
            failure.printStackTrace();
            Platform.exit();
            System.exit(5);
        }
    }

    private static void snapshot(Stage stage, File file) throws Exception {
        Scene scene = stage.getScene();
        scene.getRoot().applyCss();
        scene.getRoot().layout();
        WritableImage image = scene.snapshot(null);
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        int[] pixels = new int[width * height];
        image.getPixelReader().getPixels(0, 0, width, height,
                PixelFormat.getIntArgbInstance(), pixels, 0, width);
        BufferedImage output = new BufferedImage(
                width, height, BufferedImage.TYPE_INT_ARGB);
        output.setRGB(0, 0, width, height, pixels, 0, width);
        ImageIO.write(output, "png", file);
    }

    private static void audit(LyricEditorView view) {
        String listBackground = background(view);
        System.out.println("listBackground=" + listBackground);
        for (Node node : view.lookupAll(".list-cell")) {
            if (!(node instanceof ListCell)) continue;
            ListCell<?> cell = (ListCell<?>) node;
            if (cell.getIndex() < 0 || cell.getIndex() > 4) continue;
            Bounds bounds = cell.localToScene(cell.getBoundsInLocal());
            System.out.printf("cell=%d w=%.1f h=%.1f bg=%s graphic=%s%n",
                    cell.getIndex(), bounds.getWidth(), bounds.getHeight(),
                    background(cell), cell.getGraphic());
            if (cell.getItem() == null && isLight(cell)) {
                throw new IllegalStateException(
                        "empty filler cell index " + cell.getIndex()
                                + " still has a light background");
            }
            if (cell.getItem() != null && !cell.isSelected()
                    && !listBackground.equals(background(cell))) {
                throw new IllegalStateException(
                        "normal row " + cell.getIndex()
                                + " does not match list background: "
                                + background(cell) + " vs " + listBackground);
            }
        }
        for (Node node : view.lookupAll("*")) {
            if (!(node instanceof Region)) continue;
            Region region = (Region) node;
            Background background = region.getBackground();
            if (background == null) continue;
            boolean light = false;
            for (BackgroundFill fill : background.getFills()) {
                if (fill.getFill() instanceof javafx.scene.paint.Color) {
                    javafx.scene.paint.Color color =
                            (javafx.scene.paint.Color) fill.getFill();
                    if (color.getBrightness() > 0.9 && color.getOpacity() > 0.2) {
                        light = true;
                    }
                }
            }
            if (light) {
                Bounds bounds = region.localToScene(region.getBoundsInLocal());
                System.out.printf("LIGHT %s classes=%s x=%.1f y=%.1f w=%.1f h=%.1f%n",
                        region.getClass().getSimpleName(), region.getStyleClass(),
                        bounds.getMinX(), bounds.getMinY(),
                        bounds.getWidth(), bounds.getHeight());
            }
        }
    }

    private static String background(Region region) {
        Background background = region.getBackground();
        if (background == null || background.getFills().isEmpty()) return "<none>";
        StringBuilder text = new StringBuilder();
        for (BackgroundFill fill : background.getFills()) {
            if (text.length() > 0) text.append('|');
            text.append(fill.getFill());
        }
        return text.toString();
    }

    private static boolean isLight(Region region) {
        Background background = region.getBackground();
        if (background == null) return false;
        for (BackgroundFill fill : background.getFills()) {
            if (fill.getFill() instanceof javafx.scene.paint.Color) {
                javafx.scene.paint.Color color =
                        (javafx.scene.paint.Color) fill.getFill();
                if (color.getBrightness() > 0.9 && color.getOpacity() > 0.2) {
                    return true;
                }
            }
        }
        return false;
    }
}

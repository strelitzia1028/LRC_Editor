package com.lrceditor;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ListCell;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.lang.reflect.Field;

public class Win7ThemeAudit extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        stage.setOpacity(0);
        try {
            LrcEditorApp app = new LrcEditorApp();
            app.start(stage);
            Field docField = LrcEditorApp.class.getDeclaredField("doc");
            docField.setAccessible(true);
            LrcDocument doc = (LrcDocument) docField.get(app);
            doc.load(
                    "[00:01.00]第一行歌词\n"
                            + "[00:02.00]第二行歌词\n"
                            + "[00:03.00]第三行歌词\n"
                            + "[00:04.00]第四行歌词\n",
                    new File("win7-theme-audit.lrc"));
            java.lang.reflect.Method refreshAll =
                    LrcEditorApp.class.getDeclaredMethod("refreshAll");
            refreshAll.setAccessible(true);
            refreshAll.invoke(app);

            Field viewField = LrcEditorApp.class.getDeclaredField("lyricView");
            viewField.setAccessible(true);
            LyricEditorView view = (LyricEditorView) viewField.get(app);
            view.getSelectionModel().clearSelection();
            view.getSelectionModel().select(1);

            PauseTransition pause = new PauseTransition(Duration.millis(600));
            pause.setOnFinished(event -> {
                try {
                    Scene scene = stage.getScene();
                    scene.getRoot().applyCss();
                    scene.getRoot().layout();
                    dump("root", scene.getRoot());
                    for (String selector : new String[]{
                            ".app-menu-bar", ".app-header", ".sidebar-scroll",
                            ".sidebar", ".workspace", ".lyric-list",
                            ".bottom-panel", ".wave-panel", ".status-bar"}) {
                        Node node = scene.lookup(selector);
                        dump(selector, node);
                    }
                    dumpText(".app-menu-bar .label", scene.lookup(".app-menu-bar .label"));
                    dumpText(".sidebar-button", scene.lookup(".sidebar-button"));
                    Node viewport = scene.lookup(".sidebar-scroll > .viewport");
                    if (viewport == null) viewport = scene.lookup(".viewport");
                    dump("viewport", viewport);

                    scene.getRoot().applyCss();
                    scene.getRoot().layout();
                    for (Node node : view.lookupAll(".list-cell")) {
                        if (node instanceof ListCell && ((ListCell<?>) node).getIndex() == 1) {
                            ListCell<?> cell = (ListCell<?>) node;
                            Bounds sceneBounds = cell.localToScene(cell.getBoundsInLocal());
                            System.out.printf(
                                    "selectedCell index=%d x=%.1f y=%.1f w=%.1f h=%.1f "
                                            + "selected=%s style=%s%n",
                                    cell.getIndex(), sceneBounds.getMinX(),
                                    sceneBounds.getMinY(), sceneBounds.getWidth(),
                                    sceneBounds.getHeight(), cell.isSelected(),
                                    cell.getStyle());
                            dump("selectedCell", cell);
                            for (Node child : cell.lookupAll(".text-field")) {
                                dumpText("selectedTextField", child);
                            }
                            Region graphic = (Region) cell.getGraphic();
                            if (graphic != null) {
                                Bounds graphicBounds =
                                        graphic.localToScene(graphic.getBoundsInLocal());
                                System.out.printf(
                                        "selectedGraphic x=%.1f y=%.1f w=%.1f h=%.1f%n",
                                        graphicBounds.getMinX(), graphicBounds.getMinY(),
                                        graphicBounds.getWidth(), graphicBounds.getHeight());
                                dump("selectedGraphic", graphic);
                            }
                        }
                    }
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
            Platform.exit();
            System.exit(3);
        }
    }

    private static void dump(String label, Node node) {
        if (node == null) {
            System.out.println(label + "=<missing>");
            return;
        }
        String background = "<non-region>";
        if (node instanceof Region) {
            Background bg = ((Region) node).getBackground();
            if (bg == null || bg.getFills().isEmpty()) {
                background = "<none>";
            } else {
                StringBuilder fills = new StringBuilder();
                for (BackgroundFill fill : bg.getFills()) {
                    if (fills.length() > 0) fills.append('|');
                    fills.append(fill.getFill());
                }
                background = fills.toString();
            }
        }
        Parent parent = node.getParent();
        System.out.printf("%s class=%s id=%s parent=%s bg=%s%n",
                label, node.getStyleClass(),
                node.getId(), parent == null ? "<root>" : parent.getStyleClass(),
                background);
    }

    private static void dumpText(String label, Node node) {
        if (!(node instanceof javafx.scene.control.Labeled)) {
            System.out.println(label + "=<missing-or-not-labeled>");
            return;
        }
        javafx.scene.control.Labeled labeled = (javafx.scene.control.Labeled) node;
        System.out.println(label + " class=" + labeled.getStyleClass()
                + " textFill=" + labeled.getTextFill()
                + " text=" + labeled.getText());
    }
}

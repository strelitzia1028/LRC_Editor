package com.lrceditor;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.ListCell;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class Win7RowScrollSmoke extends Application {

    private LrcEditorApp app;
    private LyricEditorView view;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        stage.setOpacity(0);
        try {
            app = new LrcEditorApp();
            app.start(stage);

            Field docField = LrcEditorApp.class.getDeclaredField("doc");
            docField.setAccessible(true);
            LrcDocument doc = (LrcDocument) docField.get(app);
            StringBuilder lrc = new StringBuilder();
            for (int i = 0; i < 100; i++) {
                lrc.append(String.format("[%02d:%02d.00]第 %d 行歌词%n",
                        i / 60, i % 60, i + 1));
            }
            doc.load(lrc.toString(), new File("win7-row-scroll.lrc"));

            Method refreshAll = LrcEditorApp.class.getDeclaredMethod("refreshAll");
            refreshAll.setAccessible(true);
            refreshAll.invoke(app);

            Field viewField = LrcEditorApp.class.getDeclaredField("lyricView");
            viewField.setAccessible(true);
            view = (LyricEditorView) viewField.get(app);

            PauseTransition initial = new PauseTransition(Duration.millis(500));
            initial.setOnFinished(event -> {
                view.scrollTo(50);
                PauseTransition positioned = new PauseTransition(Duration.millis(300));
                positioned.setOnFinished(e -> {
                    view.getSelectionModel().clearSelection();
                    view.getSelectionModel().select(60);
                    int beforeInsert = topVisibleIndex();
                    try {
                        Method insert = LrcEditorApp.class.getDeclaredMethod("insertNewLine");
                        insert.setAccessible(true);
                        insert.invoke(app);
                    } catch (Exception ex) {
                        fail(ex);
                        return;
                    }
                    PauseTransition afterInsert = new PauseTransition(Duration.millis(300));
                    afterInsert.setOnFinished(e2 -> finishInsert(beforeInsert));
                    afterInsert.play();
                });
                positioned.play();
            });
            initial.play();
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    private void finishInsert(int beforeInsert) {
        int afterInsert = topVisibleIndex();
        int insertedSelection = view.getSelectionModel().getSelectedIndex();
        System.out.println("insert beforeTop=" + beforeInsert
                + " afterTop=" + afterInsert + " selected=" + insertedSelection);
        if (afterInsert > beforeInsert + 1) {
            fail(new IllegalStateException("insert moved the viewport to the selected row"));
            return;
        }
        if (insertedSelection != 61) {
            fail(new IllegalStateException("insert did not select the new row"));
            return;
        }

        view.scrollTo(50);
        PauseTransition reposition = new PauseTransition(Duration.millis(300));
        reposition.setOnFinished(event -> {
            view.getSelectionModel().clearSelection();
            view.getSelectionModel().select(60);
            int beforeDelete = topVisibleIndex();
            try {
                Method delete = LrcEditorApp.class.getDeclaredMethod("deleteSelectedLines");
                delete.setAccessible(true);
                delete.invoke(app);
            } catch (Exception ex) {
                fail(ex);
                return;
            }
            PauseTransition afterDelete = new PauseTransition(Duration.millis(300));
            afterDelete.setOnFinished(e -> finishDelete(beforeDelete));
            afterDelete.play();
        });
        reposition.play();
    }

    private void finishDelete(int beforeDelete) {
        int afterDelete = topVisibleIndex();
        System.out.println("delete beforeTop=" + beforeDelete + " afterTop=" + afterDelete);
        if (afterDelete > beforeDelete + 1) {
            fail(new IllegalStateException("delete moved the viewport to the selected row"));
            return;
        }
        System.out.println("rowScrollSmoke=passed");
        Platform.exit();
    }

    private int topVisibleIndex() {
        Bounds listBounds = view.localToScene(view.getBoundsInLocal());
        int top = Integer.MAX_VALUE;
        for (Node node : view.lookupAll(".list-cell")) {
            if (!(node instanceof ListCell)) continue;
            ListCell<?> cell = (ListCell<?>) node;
            int index = cell.getIndex();
            if (index < 0) continue;
            Bounds bounds = cell.localToScene(cell.getBoundsInLocal());
            if (bounds.getMaxY() <= listBounds.getMinY()
                    || bounds.getMinY() >= listBounds.getMaxY()) {
                continue;
            }
            top = Math.min(top, index);
        }
        return top == Integer.MAX_VALUE ? -1 : top;
    }

    private void fail(Throwable failure) {
        failure.printStackTrace();
        Platform.exit();
        System.exit(2);
    }
}

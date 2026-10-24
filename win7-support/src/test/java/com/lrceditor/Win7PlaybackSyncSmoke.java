package com.lrceditor;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class Win7PlaybackSyncSmoke extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        try {
            LrcDocument document = new LrcDocument();
            document.load(
                    "[00:01.000]第一行\n"
                            + "[00:01.000]第二行\n"
                            + "[00:02.000]第三行\n"
                            + "[00:03.000]第四行\n",
                    new File("playback-sync.lrc"));
            LyricEditorView view = new LyricEditorView(document);
            stage.setScene(new Scene(view, 700, 500));
            stage.show();

            view.setAutoSyncSelection(false);
            view.setPlayingIndices(new HashSet<>(Arrays.asList(0, 1)));
            assertEquals(new HashSet<Integer>(), selected(view), "selection with auto sync off");
            assertEquals(new HashSet<>(Arrays.asList(0, 1)), playing(view), "playing group");

            view.getSelectionModel().select(2);
            assertEquals(new HashSet<>(Arrays.asList(2)), selected(view), "manual selection");
            assertEquals(new HashSet<>(Arrays.asList(0, 1)), playing(view), "playing must stay current");

            view.setAutoSyncSelection(true);
            assertEquals(new HashSet<>(Arrays.asList(0, 1)), selected(view),
                    "auto sync must select playing group");

            view.getSelectionModel().clearSelection();
            view.getSelectionModel().select(3);
            PauseTransition wait = new PauseTransition(Duration.millis(250));
            wait.setOnFinished(event -> {
                try {
                    assertEquals(new HashSet<>(Arrays.asList(3)), selected(view),
                            "manual selection must remain editable in the same playing group");
                    view.setPlayingIndices(new HashSet<>(Arrays.asList(0, 1)));
                    assertEquals(new HashSet<>(Arrays.asList(3)), selected(view),
                            "refreshing the same playing group must not override selection");

                    view.setAutoSyncSelection(false);
                    view.setPlayingIndices(new HashSet<>(Arrays.asList(2)));
                    view.getSelectionModel().clearSelection();
                    view.getSelectionModel().select(0);
                    assertEquals(new HashSet<>(Arrays.asList(0)), selected(view),
                            "selection must stay independent when auto sync is off");
                    assertEquals(new HashSet<>(Arrays.asList(2)), playing(view),
                            "yellow highlight must follow playback when auto sync is off");

                    view.setAutoSyncSelection(true);
                    assertEquals(new HashSet<>(Arrays.asList(2)), selected(view),
                            "enabling auto sync must snap once to current playing group");
                    view.getSelectionModel().clearSelection();
                    view.getSelectionModel().select(0);
                    view.setPlayingIndices(new HashSet<>(Arrays.asList(2)));
                    assertEquals(new HashSet<>(Arrays.asList(0)), selected(view),
                            "same group refresh must preserve manual selection");
                    view.setPlayingIndices(new HashSet<>(Arrays.asList(3)));
                    assertEquals(new HashSet<>(Arrays.asList(3)), selected(view),
                            "new playing group must auto-select once");
                    System.out.println("playbackSyncSmoke=passed");
                } catch (Throwable failure) {
                    failure.printStackTrace();
                    System.exit(2);
                } finally {
                    stage.close();
                    Platform.exit();
                }
            });
            wait.play();
        } catch (Throwable failure) {
            failure.printStackTrace();
            Platform.exit();
            System.exit(3);
        }
    }

    private static Set<Integer> selected(LyricEditorView view) {
        return new HashSet<>(view.getSelectionModel().getSelectedIndices());
    }

    private static Set<Integer> playing(LyricEditorView view) throws Exception {
        Field field = LyricEditorView.class.getDeclaredField("playingIndices");
        field.setAccessible(true);
        return new HashSet<>((Set<Integer>) field.get(view));
    }

    private static void assertEquals(Set<Integer> expected, Set<Integer> actual,
                                     String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }
}

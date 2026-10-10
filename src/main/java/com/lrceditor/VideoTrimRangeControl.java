package com.lrceditor;

import javafx.scene.Cursor;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class VideoTrimRangeControl extends Pane {

    private final Line startLine = new Line();
    private final Line endLine = new Line();
    private final Polygon startArrow = new Polygon();
    private final Polygon endArrow = new Polygon();

    private double durationSeconds = 1;
    private double startSeconds;
    private double endSeconds = 1;
    private boolean draggingStart;
    private boolean draggingEnd;
    private BiConsumer<Double, Double> onRangeChanged;
    private Consumer<Boolean> onInteractionChanged;

    public VideoTrimRangeControl(String theme) {
        boolean dark = "dark".equals(theme);
        Color startColor = Color.web(dark ? "#39ff88" : "#2fae6b");
        Color endColor = Color.web(dark ? "#ff4d5e" : "#df5a5a");
        Color lineColor = Color.web(dark ? "#f8fafc" : "#334155");

        startArrow.getPoints().addAll(0.0, 10.0, 7.0, 2.0, 14.0, 10.0);
        endArrow.getPoints().addAll(0.0, 10.0, 7.0, 2.0, 14.0, 10.0);
        startArrow.setFill(startColor);
        endArrow.setFill(endColor);
        startLine.setStroke(lineColor);
        endLine.setStroke(lineColor);
        startLine.setStrokeWidth(1.0);
        endLine.setStrokeWidth(1.0);

        getChildren().addAll(startLine, endLine, startArrow, endArrow);
        getStyleClass().add("video-trim-range");
        setPrefHeight(22);
        setMinHeight(22);
        setCursor(Cursor.H_RESIZE);
        setOnMousePressed(this::handlePressed);
        setOnMouseDragged(this::handleDragged);
        setOnMouseReleased(e -> {
            draggingStart = false;
            draggingEnd = false;
            notifyInteraction(false);
        });
    }

    public void setOnRangeChanged(BiConsumer<Double, Double> callback) {
        onRangeChanged = callback;
    }

    public void setOnInteractionChanged(Consumer<Boolean> callback) {
        onInteractionChanged = callback;
    }

    public double getStartSeconds() {
        return startSeconds;
    }

    public double getEndSeconds() {
        return endSeconds;
    }

    public void setDuration(double durationSeconds) {
        this.durationSeconds = Math.max(0.1, durationSeconds);
        if (endSeconds <= 0 || endSeconds > this.durationSeconds) {
            endSeconds = this.durationSeconds;
        }
        startSeconds = Math.max(0, Math.min(startSeconds, endSeconds));
        requestLayout();
        notifyChanged();
    }

    public void resetRange() {
        startSeconds = 0;
        endSeconds = durationSeconds;
        requestLayout();
        notifyChanged();
    }

    @Override
    protected void layoutChildren() {
        double width = Math.max(1, getWidth());
        double startX = secondsToX(startSeconds, width);
        double endX = secondsToX(endSeconds, width);
        startLine.setStartX(startX);
        startLine.setEndX(startX);
        startLine.setStartY(0);
        startLine.setEndY(12);
        endLine.setStartX(endX);
        endLine.setEndX(endX);
        endLine.setStartY(0);
        endLine.setEndY(12);
        startArrow.setLayoutX(startX - 7);
        startArrow.setLayoutY(9);
        endArrow.setLayoutX(endX - 7);
        endArrow.setLayoutY(9);
    }

    private void handlePressed(MouseEvent event) {
        double width = Math.max(1, getWidth());
        double startDistance = Math.abs(event.getX() - secondsToX(startSeconds, width));
        double endDistance = Math.abs(event.getX() - secondsToX(endSeconds, width));
        draggingStart = startDistance <= endDistance;
        draggingEnd = !draggingStart;
        notifyInteraction(true);
        updateFromMouse(event.getX());
    }

    private void handleDragged(MouseEvent event) {
        updateFromMouse(event.getX());
    }

    private void updateFromMouse(double x) {
        double width = Math.max(1, getWidth());
        double seconds = Math.max(0, Math.min(durationSeconds, x / width * durationSeconds));
        double minimumGap = Math.min(0.2, durationSeconds * 0.01);
        if (draggingStart) {
            startSeconds = Math.min(seconds, endSeconds - minimumGap);
            startSeconds = Math.max(0, startSeconds);
        } else if (draggingEnd) {
            endSeconds = Math.max(seconds, startSeconds + minimumGap);
            endSeconds = Math.min(durationSeconds, endSeconds);
        }
        requestLayout();
        notifyChanged();
    }

    private double secondsToX(double seconds, double width) {
        if (durationSeconds <= 0) return 0;
        return Math.max(0, Math.min(width, seconds / durationSeconds * width));
    }

    private void notifyChanged() {
        if (onRangeChanged != null) {
            onRangeChanged.accept(startSeconds, endSeconds);
        }
    }

    private void notifyInteraction(boolean dragging) {
        if (onInteractionChanged != null) {
            onInteractionChanged.accept(dragging);
        }
    }
}

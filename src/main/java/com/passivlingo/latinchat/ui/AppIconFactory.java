package com.passivlingo.latinchat.ui;

import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;

public final class AppIconFactory {
    private AppIconFactory() {
    }

    public static Image createSpqrIcon(int size) {
        Canvas canvas = new Canvas(size, size);
        GraphicsContext g = canvas.getGraphicsContext2D();

        double arc = size * 0.22;
        g.setFill(Color.rgb(15, 28, 46));
        g.fillRoundRect(0, 0, size, size, arc, arc);

        double inset = size * 0.08;
        double collageSize = size - inset * 2;
        double tileGap = Math.max(1, size * 0.02);
        int cols = 4;
        int rows = 4;
        double tileW = (collageSize - tileGap * (cols - 1)) / cols;
        double tileH = (collageSize - tileGap * (rows - 1)) / rows;

        int flagIndex = 0;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                double x = inset + col * (tileW + tileGap);
                double y = inset + row * (tileH + tileGap);
                drawFlagTile(g, flagIndex++, x, y, tileW, tileH, Math.max(2, size * 0.03));
            }
        }

        // Subtle globe frame to make the collage read as a world motif.
        double cx = size * 0.5;
        double cy = size * 0.5;
        double r = size * 0.46;
        g.setStroke(Color.rgb(212, 232, 255, 0.9));
        g.setLineWidth(Math.max(1.0, size * 0.028));
        g.strokeOval(cx - r, cy - r, r * 2, r * 2);

        g.setStroke(Color.rgb(212, 232, 255, 0.45));
        g.setLineWidth(Math.max(0.7, size * 0.013));
        g.strokeOval(cx - r * 0.68, cy - r, r * 1.36, r * 2);
        g.strokeLine(cx - r, cy, cx + r, cy);

        return canvas.snapshot(new SnapshotParameters(), null);
    }

    private static void drawFlagTile(GraphicsContext g,
                                     int index,
                                     double x,
                                     double y,
                                     double w,
                                     double h,
                                     double arc) {
        int pattern = index % 10;
        switch (pattern) {
            case 0 -> {
                // Blue / white / red horizontal.
                stripeHorizontal(g, x, y, w, h, Color.rgb(0, 57, 166), Color.WHITE, Color.rgb(213, 43, 30));
            }
            case 1 -> {
                // Black / red / yellow horizontal.
                stripeHorizontal(g, x, y, w, h, Color.rgb(0, 0, 0), Color.rgb(221, 0, 0), Color.rgb(255, 206, 0));
            }
            case 2 -> {
                // Green / white / red vertical.
                stripeVertical(g, x, y, w, h, Color.rgb(0, 146, 70), Color.WHITE, Color.rgb(206, 43, 55));
            }
            case 3 -> {
                // Red / white with blue canton style.
                g.setFill(Color.WHITE);
                g.fillRoundRect(x, y, w, h, arc, arc);
                g.setFill(Color.rgb(191, 10, 48));
                g.fillRect(x, y, w, h * 0.33);
                g.fillRect(x, y + h * 0.67, w, h * 0.33);
                g.setFill(Color.rgb(0, 40, 104));
                g.fillRect(x, y, w * 0.42, h * 0.55);
            }
            case 4 -> {
                // Red with yellow disk.
                g.setFill(Color.rgb(198, 12, 48));
                g.fillRoundRect(x, y, w, h, arc, arc);
                g.setFill(Color.rgb(255, 205, 0));
                g.fillOval(x + w * 0.34, y + h * 0.31, w * 0.32, h * 0.38);
            }
            case 5 -> {
                // White with nordic cross.
                g.setFill(Color.WHITE);
                g.fillRoundRect(x, y, w, h, arc, arc);
                g.setFill(Color.rgb(0, 47, 108));
                g.fillRect(x + w * 0.28, y, w * 0.16, h);
                g.fillRect(x, y + h * 0.42, w, h * 0.16);
            }
            case 6 -> {
                // Blue with yellow cross.
                g.setFill(Color.rgb(0, 82, 165));
                g.fillRoundRect(x, y, w, h, arc, arc);
                g.setFill(Color.rgb(254, 204, 0));
                g.fillRect(x + w * 0.30, y, w * 0.14, h);
                g.fillRect(x, y + h * 0.42, w, h * 0.16);
            }
            case 7 -> {
                // Green / yellow / red vertical.
                stripeVertical(g, x, y, w, h, Color.rgb(0, 122, 61), Color.rgb(255, 209, 0), Color.rgb(206, 17, 38));
            }
            case 8 -> {
                // Red / white / red vertical.
                stripeVertical(g, x, y, w, h, Color.rgb(213, 43, 30), Color.WHITE, Color.rgb(213, 43, 30));
            }
            default -> {
                // Green / white / orange vertical.
                stripeVertical(g, x, y, w, h, Color.rgb(22, 155, 98), Color.WHITE, Color.rgb(255, 130, 0));
            }
        }

        g.setStroke(Color.rgb(14, 20, 34, 0.65));
        g.setLineWidth(Math.max(0.6, w * 0.04));
        g.strokeRoundRect(x, y, w, h, arc, arc);
    }

    private static void stripeHorizontal(GraphicsContext g,
                                         double x,
                                         double y,
                                         double w,
                                         double h,
                                         Color top,
                                         Color mid,
                                         Color bottom) {
        g.setFill(top);
        g.fillRoundRect(x, y, w, h, 2, 2);
        g.setFill(mid);
        g.fillRect(x, y + h / 3.0, w, h / 3.0);
        g.setFill(bottom);
        g.fillRect(x, y + h * 2.0 / 3.0, w, h / 3.0);
    }

    private static void stripeVertical(GraphicsContext g,
                                       double x,
                                       double y,
                                       double w,
                                       double h,
                                       Color left,
                                       Color mid,
                                       Color right) {
        g.setFill(left);
        g.fillRoundRect(x, y, w, h, 2, 2);
        g.setFill(mid);
        g.fillRect(x + w / 3.0, y, w / 3.0, h);
        g.setFill(right);
        g.fillRect(x + w * 2.0 / 3.0, y, w / 3.0, h);
    }
}

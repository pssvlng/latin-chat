package com.passivlingo.latinchat.ui;

import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

public final class AppIconFactory {
    private AppIconFactory() {
    }

    public static Image createSpqrIcon(int size) {
        Canvas canvas = new Canvas(size, size);
        GraphicsContext g = canvas.getGraphicsContext2D();

        g.setFill(Color.rgb(178, 25, 25));
        g.fillRoundRect(0, 0, size, size, size * 0.2, size * 0.2);

        g.setFill(Color.rgb(255, 210, 45));
        Font font = Font.font("Georgia", FontWeight.EXTRA_BOLD, Math.max(10, size * 0.27));
        g.setFont(font);

        String text = "SPQR";
        Text measure = new Text(text);
        measure.setFont(font);
        double textWidth = measure.getLayoutBounds().getWidth();
        double x = (size - textWidth) / 2.0 - measure.getLayoutBounds().getMinX();
        double y = size * 0.61;
        g.fillText(text, x, y);

        return canvas.snapshot(new SnapshotParameters(), null);
    }
}

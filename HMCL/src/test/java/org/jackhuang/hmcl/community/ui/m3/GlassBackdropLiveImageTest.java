/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.community.ui.m3;

import javafx.geometry.Side;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BackgroundImage;
import javafx.scene.layout.BackgroundPosition;
import javafx.scene.layout.BackgroundRepeat;
import javafx.scene.layout.BackgroundSize;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/// [GlassBackdrop#liveImage] 的壁纸选择规则测试（无场景、无 toolkit 依赖）。
public final class GlassBackdropLiveImageTest {

    private static final BackgroundSize COVER =
            new BackgroundSize(BackgroundSize.AUTO, BackgroundSize.AUTO, false, false, false, true);
    private static final BackgroundSize CONTAIN =
            new BackgroundSize(BackgroundSize.AUTO, BackgroundSize.AUTO, false, false, true, false);

    @Test
    public void acceptsCoverCenteredNoRepeatImage() {
        Image image = new WritableImage(100, 100);
        BackgroundImage backgroundImage = image(image, BackgroundRepeat.NO_REPEAT,
                BackgroundRepeat.NO_REPEAT, BackgroundPosition.DEFAULT, COVER);

        assertSame(backgroundImage, GlassBackdrop.liveImage(new Background(backgroundImage)));
    }

    @Test
    public void picksFirstSamplableImage() {
        Image image = new WritableImage(100, 100);
        BackgroundImage contain = image(image, BackgroundRepeat.NO_REPEAT,
                BackgroundRepeat.NO_REPEAT, BackgroundPosition.DEFAULT, CONTAIN);
        BackgroundImage cover = image(image, BackgroundRepeat.NO_REPEAT,
                BackgroundRepeat.NO_REPEAT, BackgroundPosition.DEFAULT, COVER);

        assertSame(cover, GlassBackdrop.liveImage(new Background(contain, cover)));
    }

    @Test
    public void rejectsContain() {
        BackgroundImage backgroundImage = image(new WritableImage(100, 100),
                BackgroundRepeat.NO_REPEAT, BackgroundRepeat.NO_REPEAT, BackgroundPosition.DEFAULT, CONTAIN);

        assertNull(GlassBackdrop.liveImage(new Background(backgroundImage)));
    }

    @Test
    public void rejectsRepeatedImage() {
        BackgroundImage repeated = image(new WritableImage(100, 100),
                BackgroundRepeat.REPEAT, BackgroundRepeat.NO_REPEAT, BackgroundPosition.DEFAULT, COVER);
        BackgroundImage repeatedY = image(new WritableImage(100, 100),
                BackgroundRepeat.NO_REPEAT, BackgroundRepeat.REPEAT, BackgroundPosition.DEFAULT, COVER);

        assertNull(GlassBackdrop.liveImage(new Background(repeated)));
        assertNull(GlassBackdrop.liveImage(new Background(repeatedY)));
    }

    @Test
    public void rejectsNonCenteredPosition() {
        BackgroundPosition topLeft = new BackgroundPosition(Side.LEFT, 0.0, false, Side.TOP, 0.0, false);
        BackgroundImage backgroundImage = image(new WritableImage(100, 100),
                BackgroundRepeat.NO_REPEAT, BackgroundRepeat.NO_REPEAT, topLeft, COVER);

        assertNull(GlassBackdrop.liveImage(new Background(backgroundImage)));
    }

    @Test
    public void rejectsPaintOnlyBackground() {
        assertNull(GlassBackdrop.liveImage(Background.EMPTY));
        assertNull(GlassBackdrop.liveImage(new Background(
                new BackgroundFill(Color.RED, null, null))));
    }

    private static BackgroundImage image(Image image,
                                         BackgroundRepeat repeatX,
                                         BackgroundRepeat repeatY,
                                         BackgroundPosition position,
                                         BackgroundSize size) {
        return new BackgroundImage(image, repeatX, repeatY, position, size);
    }
}

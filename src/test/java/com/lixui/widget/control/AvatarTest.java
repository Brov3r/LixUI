package com.lixui.widget.control;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.input.InputState;
import com.lixui.render.Viewport;
import com.lixui.render.command.TextCommand;
import com.lixui.text.Font;
import com.lixui.text.FontData;
import com.lixui.text.SystemFonts;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestTexture;
import com.lixui.widget.testsupport.TestViewports;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AvatarTest {
    private static final Viewport VIEWPORT = TestViewports.LOGICAL;

    @Test
    void derivesUpToTwoInitials() {
        Avatar avatar = new Avatar();
        avatar.setName("Aria Snow");
        assertEquals("AS", avatar.initials());

        avatar.setName("Bran");
        assertEquals("B", avatar.initials());

        avatar.setName("   ");
        assertEquals("", avatar.initials());
    }

    @Test
    void anImageSourceHidesTheInitials() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            manager.setDefaultFont(font(manager));
            Avatar avatar = new Avatar();
            avatar.setName("Aria Snow");
            avatar.setDiameter(48);
            avatar.setBounds(new Rect(0, 0, 48, 48));
            manager.add(avatar);

            manager.render();
            assertTrue(backend.lastDrawList().commands().stream()
                    .anyMatch(command -> command instanceof TextCommand));

            avatar.setTexture(new TestTexture(64, 64));
            manager.render();
            assertFalse(backend.lastDrawList().commands().stream()
                    .anyMatch(command -> command instanceof TextCommand));
        }
    }

    @Test
    void placesBadgesInDifferentCorners() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            manager.setDefaultFont(font(manager));
            Avatar avatar = new Avatar();
            avatar.setName("Aria");
            avatar.setDiameter(48);
            avatar.setBounds(new Rect(0, 0, 48, 48));
            Badge count = avatar.addBadge(Avatar.Corner.BOTTOM_RIGHT, "3");
            Badge status = avatar.addBadge(Avatar.Corner.TOP_RIGHT);
            status.setDotSize(10);
            manager.add(avatar);

            manager.render();

            assertEquals(2, avatar.badges().size());
            assertEquals(Avatar.Corner.TOP_RIGHT, avatar.badgeCorner(status));
            Rect countBounds = count.bounds();
            Rect statusBounds = status.bounds();
            assertTrue(countBounds.right() <= 48.01f);
            assertTrue(countBounds.bottom() <= 48.01f);
            assertTrue(countBounds.bottom() > 36);
            assertTrue(statusBounds.y() >= 0 && statusBounds.y() < 12);
            assertTrue(statusBounds.right() <= 48.01f);
        }
    }

    @Test
    void clearsEveryBadge() {
        Avatar avatar = new Avatar();
        avatar.addBadge("1");
        avatar.addBadge("2");

        avatar.clearBadges();

        assertTrue(avatar.badges().isEmpty());
    }

    @Test
    void measuresTheConfiguredDiameter() {
        Avatar avatar = new Avatar();
        avatar.setDiameter(64);

        Size measured = avatar.measure(LayoutConstraints.loose(200, 200));

        assertEquals(64, measured.width(), 0.001f);
        assertEquals(64, measured.height(), 0.001f);
    }

    @Test
    void rejectsInvalidArguments() {
        Avatar avatar = new Avatar();
        assertThrows(NullPointerException.class, () -> avatar.setName(null));
        assertThrows(IllegalArgumentException.class, () -> avatar.setDiameter(0));
        assertThrows(IllegalArgumentException.class, () -> avatar.setDiameter(Float.NaN));
    }

    private static Font font(WidgetManager manager) {
        Optional<FontData> data = SystemFonts.findDefault();
        Assumptions.assumeTrue(data.isPresent(), "No readable system font is installed.");
        return manager.context().createFont(data.get(), 16);
    }

    private static WidgetManager manager(TestBackend backend) {
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(VIEWPORT);
        return manager;
    }
}

package com.lixui.widget.control;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.input.InputState;
import com.lixui.render.Viewport;
import com.lixui.text.Font;
import com.lixui.text.FontData;
import com.lixui.text.SystemFonts;
import com.lixui.theme.WidgetStyle;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestViewports;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordInputTest {
    private static final Viewport VIEWPORT = TestViewports.LOGICAL;

    @Test
    void startsSecretAndTogglesWithTheEyeButton() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            manager.setDefaultFont(font(manager));
            PasswordInput input = new PasswordInput("secret");
            input.setBounds(new Rect(0, 0, 240, 40));
            manager.add(input);
            manager.render();

            Button reveal = (Button) input.children().getFirst();
            assertTrue(input.isSecret());

            reveal.click();
            assertFalse(input.isSecret());

            reveal.click();
            assertTrue(input.isSecret());
        }
    }

    @Test
    void reservesTrailingContentSpaceForTheEye() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            manager.setDefaultFont(font(manager));
            PasswordInput input = new PasswordInput("secret");
            Input plain = new Input("secret");
            input.setBounds(new Rect(0, 0, 240, 40));
            plain.setBounds(new Rect(0, 0, 240, 40));
            manager.add(input).add(plain);
            manager.render();

            WidgetStyle style = manager.context().theme().controlStyle();
            assertTrue(input.contentRect(style).width() < plain.contentRect(style).width());

            input.setRevealButtonVisible(false);
            assertEquals(plain.contentRect(style).width(), input.contentRect(style).width(), 0.001f);
        }
    }

    @Test
    void hidingTheEyeShrinksTheNaturalWidth() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            manager.setDefaultFont(font(manager));
            PasswordInput input = new PasswordInput("secret");
            manager.add(input);

            Size withButton = input.measure(LayoutConstraints.loose(1000, 1000));
            input.setRevealButtonVisible(false);
            Size withoutButton = input.measure(LayoutConstraints.loose(1000, 1000));

            assertTrue(withButton.width() > withoutButton.width());
        }
    }

    @Test
    void rejectsNullText() {
        assertThrows(NullPointerException.class, () -> new PasswordInput(null));
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

package com.lixui.demo;

import com.lixui.geometry.Rect;
import com.lixui.input.InputState;
import com.lixui.input.Key;
import com.lixui.input.Modifiers;
import com.lixui.input.event.CharTyped;
import com.lixui.input.event.KeyPressed;
import com.lixui.input.event.KeyReleased;
import com.lixui.text.FontData;
import com.lixui.text.SystemFonts;
import com.lixui.widget.Widget;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.control.Input;
import com.lixui.widget.control.Label;
import com.lixui.widget.control.TagInput;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestViewports;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoFormWidgetsTest {
    @Test
    void tagInputDemoStartsBelowItsLimitAndExplainsRejections() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = new WidgetManager(new InputState(), backend)) {
            manager.resize(TestViewports.LOGICAL);
            Optional<FontData> data = SystemFonts.findDefault();
            Assumptions.assumeTrue(data.isPresent(), "No readable system font is installed.");
            manager.setDefaultFont(manager.context().createFont(data.get(), 16));

            Widget page = DemoFormWidgets.tagInputs();
            page.setBounds(new Rect(0, 0, 520, 360));
            manager.add(page);
            manager.render();

            TagInput input = findTagInput(page);
            Label status = findStatus(page);
            assertNotNull(input, "the tag input section must contain a limited tag editor");
            assertNotNull(status, "the tag input section must contain a status label");
            assertEquals(input.maxTags() - 1, input.tags().size(),
                    "the demo must leave room for another tag");

            Input field = (Input) input.children().get(1);
            manager.focus(field);

            field.setText("design");
            pressEnter(manager);
            assertTrue(status.text().contains("already exists"),
                    "a duplicate must report the duplicate rule");

            field.setText("x");
            pressEnter(manager);
            assertTrue(status.text().contains("at least two characters"),
                    "an invalid tag must report the validator rule");

            input.addTag("testing");
            field.setText("extra");
            pressEnter(manager);
            assertTrue(status.text().contains("limit of " + input.maxTags()),
                    "a full editor must report the tag limit");
        }
    }

    private static TagInput findTagInput(Widget widget) {
        if (widget instanceof TagInput input && input.maxTags() > 0) {
            return input;
        }
        for (Widget child : widget.children()) {
            TagInput found = findTagInput(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static Label findStatus(Widget widget) {
        if (widget instanceof Label label && label.text().startsWith("Tags:")) {
            return label;
        }
        for (Widget child : widget.children()) {
            Label found = findStatus(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static void pressEnter(WidgetManager manager) {
        manager.handle(new KeyPressed(Key.ENTER, Modifiers.NONE, false));
        manager.handle(new CharTyped('\r'));
        manager.handle(new KeyReleased(Key.ENTER, Modifiers.NONE));
    }
}

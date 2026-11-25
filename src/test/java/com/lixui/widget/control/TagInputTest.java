package com.lixui.widget.control;

import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;
import com.lixui.input.InputState;
import com.lixui.input.Key;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.input.event.CharTyped;
import com.lixui.input.event.KeyPressed;
import com.lixui.input.event.KeyReleased;
import com.lixui.input.event.PointerPressed;
import com.lixui.input.event.PointerReleased;
import com.lixui.render.Viewport;
import com.lixui.text.Font;
import com.lixui.text.FontData;
import com.lixui.text.SystemFonts;
import com.lixui.widget.Widget;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.layout.Flow;
import com.lixui.widget.layout.VBox;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestViewports;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TagInputTest {
    private static final Viewport VIEWPORT = TestViewports.LOGICAL;

    @Test
    void enterAddsATagAndClearsTheField() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            manager.setDefaultFont(font(manager));
            TagInput input = new TagInput();
            input.setBounds(new Rect(0, 0, 320, 120));
            List<List<String>> changes = new ArrayList<>();
            input.setOnTagsChanged(changes::add);
            manager.add(input);
            manager.render();
            Input field = (Input) input.children().get(1);
            manager.focus(field);

            field.setText("alpha");
            manager.handle(new KeyPressed(Key.ENTER, Modifiers.NONE, false));

            assertEquals(List.of("alpha"), input.tags());
            assertTrue(field.text().isEmpty());
            assertEquals(List.of(List.of("alpha")), changes);
        }
    }

    @Test
    void enterAddsExactlyOneTagPerSubmissionAndKeepsTypingFocus() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            manager.setDefaultFont(font(manager));
            TagInput input = new TagInput();
            VBox group = new VBox();
            group.setGap(6);
            group.add(input);
            group.setFillViewport(true);
            manager.add(group);
            manager.update(0.016f);
            manager.render();
            Input field = (Input) input.children().get(1);
            Point origin = field.absolutePosition();
            manager.handle(new PointerPressed(origin.x() + 4, origin.y() + 4,
                    MouseButton.LEFT, Modifiers.NONE));
            manager.handle(new PointerReleased(origin.x() + 4, origin.y() + 4,
                    MouseButton.LEFT, Modifiers.NONE));
            manager.update(0.016f);
            assertTrue(field.isFocused());

            type(manager, "alpha");
            pressEnter(manager);
            manager.update(0.016f);
            manager.render();
            assertEquals(List.of("alpha"), input.tags());
            assertTrue(field.isFocused());

            pressEnter(manager);
            manager.update(0.016f);
            assertEquals(List.of("alpha"), input.tags());

            type(manager, "beta");
            manager.update(0.016f);
            pressEnter(manager);
            manager.update(0.016f);
            manager.render();
            assertEquals(List.of("alpha", "beta"), input.tags());
            assertTrue(field.text().isEmpty());
            assertTrue(field.isFocused());

            type(manager, "gamma");
            pressEnter(manager);
            manager.update(0.016f);
            assertEquals(List.of("alpha", "beta", "gamma"), input.tags());
            assertTrue(field.isFocused());

            manager.handle(new KeyPressed(Key.BACKSPACE, Modifiers.NONE, false));
            assertEquals(List.of("alpha", "beta"), input.tags());

            type(manager, "delta");
            pressEnter(manager);
            manager.update(0.016f);
            assertEquals(List.of("alpha", "beta", "delta"), input.tags());
            assertTrue(field.isFocused());
        }
    }

    @Test
    void enterReportsARejectedTagAndKeepsTheTypedValue() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            manager.setDefaultFont(font(manager));
            TagInput input = new TagInput(List.of("alpha"));
            input.setMaxTags(1);
            input.setBounds(new Rect(0, 0, 320, 120));
            List<List<String>> changes = new ArrayList<>();
            List<String> rejected = new ArrayList<>();
            input.setOnTagsChanged(changes::add);
            input.setOnTagRejected(rejected::add);
            manager.add(input);
            manager.render();
            Input field = (Input) input.children().get(1);
            manager.focus(field);

            type(manager, "beta");
            pressEnter(manager);
            pressEnter(manager);

            assertEquals(List.of("alpha"), input.tags());
            assertEquals("beta", field.text());
            assertTrue(field.isFocused());
            assertEquals(List.of(), changes);
            assertEquals(List.of("beta", "beta"), rejected);

            input.removeTag("alpha");
            pressEnter(manager);

            assertEquals(List.of("beta"), input.tags());
            assertTrue(field.text().isEmpty());
            assertEquals(List.of(List.of(), List.of("beta")), changes);
            assertEquals(2, rejected.size());
        }
    }

    @Test
    void backspaceRemovesTheLastTagOnlyWhenTheFieldIsEmpty() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            manager.setDefaultFont(font(manager));
            TagInput input = new TagInput(List.of("alpha", "beta"));
            input.setBounds(new Rect(0, 0, 320, 120));
            manager.add(input);
            manager.render();
            Input field = (Input) input.children().get(1);
            manager.focus(field);

            field.setText("x");
            manager.handle(new KeyPressed(Key.BACKSPACE, Modifiers.NONE, false));
            assertEquals(List.of("alpha", "beta"), input.tags());

            field.setText("");
            manager.handle(new KeyPressed(Key.BACKSPACE, Modifiers.NONE, false));
            assertEquals(List.of("alpha"), input.tags());
        }
    }

    @Test
    void filtersEmptyDuplicateInvalidAndExcessTags() {
        TagInput input = new TagInput();
        List<List<String>> changes = new ArrayList<>();
        input.setOnTagsChanged(changes::add);
        input.setMaxTags(3);

        assertTrue(input.addTag("  alpha  "));
        assertFalse(input.addTag("alpha"));
        assertFalse(input.addTag("   "));
        assertTrue(input.addTag("beta"));
        input.setValidator(tag -> tag.length() > 3);
        assertFalse(input.addTag("abc"));
        assertTrue(input.addTag("gamma"));

        assertEquals(List.of("alpha", "beta", "gamma"), input.tags());
        assertEquals(3, changes.size());

        input.setMaxTags(1);
        assertEquals(List.of("alpha"), input.tags());
        assertEquals(4, changes.size());
        assertThrows(IllegalArgumentException.class, () -> input.setMaxTags(-1));
    }

    @Test
    void setTagsFiltersAndCopiesTheList() {
        TagInput input = new TagInput();
        List<String> source = new ArrayList<>(List.of(" one ", "one", "", "two"));
        input.setValidator(tag -> !tag.equals("three"));
        input.setTags(source);
        source.clear();

        assertEquals(List.of("one", "two"), input.tags());
        assertThrows(UnsupportedOperationException.class, () -> input.tags().add("x"));
    }

    @Test
    void wrapsTagsAndGrowsWithTheRows() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            manager.setDefaultFont(font(manager));
            TagInput input = new TagInput(List.of("alpha"));
            input.setBounds(new Rect(0, 0, 240, 400));
            manager.add(input);
            manager.render();
            float singleRow = input.measure(LayoutConstraints.loose(240, 1000)).height();

            input.setTags(List.of("alpha-long", "beta-long", "gamma-long", "delta-long",
                    "epsilon-long", "zeta-long", "eta-long", "theta-long"));
            float manyRows = input.measure(LayoutConstraints.loose(240, 1000)).height();

            assertTrue(manyRows > singleRow);
        }
    }

    @Test
    void replacesTagsBeyondTheRowLimitWithAnOverflowChip() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            manager.setDefaultFont(font(manager));
            TagInput input = new TagInput(List.of("alpha-long", "beta-long", "gamma-long",
                    "delta-long", "epsilon-long", "zeta-long"));
            input.setMaxRows(1);
            input.setBounds(new Rect(0, 0, 190, 120));
            manager.add(input);
            manager.render();

            Flow flow = (Flow) input.children().get(0);
            long visibleTags = flow.children().stream()
                    .filter(Widget::isVisible)
                    .filter(child -> !((Button) child).text().startsWith("+"))
                    .count();
            Button overflow = flow.children().stream()
                    .map(Button.class::cast)
                    .filter(Button::isVisible)
                    .filter(button -> button.text().startsWith("+"))
                    .findFirst().orElseThrow();

            assertTrue(visibleTags < input.tags().size());
            assertEquals("+" + (input.tags().size() - visibleTags), overflow.text());
        }
    }

    @Test
    void hidesATagThatIsWiderThanTheChipArea() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            manager.setDefaultFont(font(manager));
            TagInput input = new TagInput(List.of("a-tag-wider-than-the-field"));
            input.setBounds(new Rect(0, 0, 80, 120));
            manager.add(input);
            manager.render();

            Flow flow = (Flow) input.children().get(0);
            Button tag = (Button) flow.children().get(0);
            Button overflow = (Button) flow.children().get(1);

            assertFalse(tag.isVisible());
            assertTrue(overflow.isVisible());
            assertEquals("+1", overflow.text());
            assertTrue(overflow.bounds().x() + overflow.bounds().width()
                    <= flow.localBounds().width() + 0.01f);
        }
    }

    @Test
    void crossPointerClickRemovesTheChipAndKeepsTypingFocus() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            manager.setDefaultFont(font(manager));
            TagInput input = new TagInput(List.of("alpha", "beta"));
            input.setBounds(new Rect(0, 0, 320, 120));
            manager.add(input);
            manager.render();

            Flow flow = (Flow) input.children().get(0);
            Button chip = (Button) flow.children().get(0);
            float x = chip.absolutePosition().x() + chip.localBounds().width() - 2;
            float y = chip.absolutePosition().y() + chip.localBounds().height() / 2;

            manager.handle(new PointerPressed(x, y, MouseButton.LEFT, Modifiers.NONE));
            manager.handle(new PointerReleased(x, y, MouseButton.LEFT, Modifiers.NONE));

            assertEquals(List.of("beta"), input.tags());
            Input field = (Input) input.children().get(1);
            assertTrue(field.isFocused());

            manager.handle(new CharTyped('z'));
            assertEquals("z", field.text());
        }
    }

    private static void pressEnter(WidgetManager manager) {
        manager.handle(new KeyPressed(Key.ENTER, Modifiers.NONE, false));
        manager.handle(new CharTyped('\r'));
        manager.handle(new KeyReleased(Key.ENTER, Modifiers.NONE));
    }

    private static void type(WidgetManager manager, String text) {
        text.codePoints().forEach(codepoint -> manager.handle(new CharTyped(codepoint)));
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

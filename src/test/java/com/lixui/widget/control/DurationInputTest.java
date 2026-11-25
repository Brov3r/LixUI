package com.lixui.widget.control;

import com.lixui.widget.Widget;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DurationInputTest {

    @Test
    void switchingUnitsKeepsTheDuration() {
        DurationInput input = new DurationInput();
        input.setUnit(DurationInput.MINUTES);
        input.setSeconds(90);

        assertEquals("1.5", amount(input).text());
        assertEquals(90, input.seconds(), 0.001);

        input.setUnit(DurationInput.MILLISECONDS);
        assertEquals("90000", amount(input).text());
        assertEquals(90, input.seconds(), 0.001);

        input.setUnit(DurationInput.HOURS);
        assertEquals("0.025", amount(input).text());
        assertEquals(90, input.seconds(), 0.001);
    }

    @Test
    void changingUnitsDoesNotNotifyTheValueCallback() {
        DurationInput input = new DurationInput();
        input.setSeconds(90);
        List<Double> changes = new ArrayList<>();
        input.setOnChange(changes::add);

        input.setUnit(DurationInput.MINUTES);

        assertTrue(changes.isEmpty());
        assertEquals(90, input.seconds(), 0.001);
    }

    @Test
    void startsInSeconds() {
        DurationInput input = new DurationInput();

        assertEquals(DurationInput.SECONDS, input.unit());
        assertEquals("0", amount(input).text());
    }

    private static Input amount(DurationInput input) {
        Widget content = input.children().getFirst();
        return (Input) content.children().get(0);
    }
}

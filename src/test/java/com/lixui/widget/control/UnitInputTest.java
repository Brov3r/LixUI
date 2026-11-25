package com.lixui.widget.control;

import com.lixui.widget.Widget;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UnitInputTest {
    @Test
    void changingUnitsKeepsTheBaseValue() {
        UnitInput input = new UnitInput();
        input.setUnits(List.of(new UnitInput.UnitOption("mm", 1),
                new UnitInput.UnitOption("cm", 10), new UnitInput.UnitOption("m", 1000)));
        input.setUnit(new UnitInput.UnitOption("cm", 10));
        input.setValue(250);

        assertEquals("25", amount(input).text());
        assertEquals(250, input.value(), 0.001);

        input.setUnit(new UnitInput.UnitOption("m", 1000));
        assertEquals("0.25", amount(input).text());
        assertEquals(250, input.value(), 0.001);
    }

    @Test
    void typingConvertsTheAmountToBaseUnits() {
        UnitInput input = new UnitInput();
        input.setUnits(List.of(new UnitInput.UnitOption("mm", 1),
                new UnitInput.UnitOption("cm", 10)));
        input.setUnit(new UnitInput.UnitOption("cm", 10));

        amount(input).setText("2.5");

        assertEquals(25, input.value(), 0.001);
    }

    @Test
    void setDecimalsReformatsTheAmount() {
        UnitInput input = new UnitInput();
        input.setUnits(List.of(new UnitInput.UnitOption("unit", 1)));
        input.setValue(1.234);
        input.setDecimals(1);

        assertEquals("1.2", amount(input).text());
        assertEquals(1.234, input.value(), 0.001);
    }

    @Test
    void rejectsInvalidUnitsAndArguments() {
        UnitInput input = new UnitInput();
        assertThrows(NullPointerException.class, () -> input.setUnits(null));
        assertThrows(IllegalArgumentException.class, () -> input.setUnits(List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new UnitInput.UnitOption("", 1));
        assertThrows(IllegalArgumentException.class,
                () -> new UnitInput.UnitOption("m", 0));
        assertThrows(IllegalArgumentException.class,
                () -> input.setUnit(new UnitInput.UnitOption("unknown", 1)));
        assertThrows(NullPointerException.class,
                () -> input.setUnits(Arrays.asList(new UnitInput.UnitOption("m", 1), null)));
        assertThrows(IllegalArgumentException.class, () -> input.setValue(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> input.setDecimals(10));
    }

    private static Input amount(UnitInput input) {
        Widget content = input.children().getFirst();
        return (Input) content.children().get(0);
    }
}

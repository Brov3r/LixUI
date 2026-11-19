package com.lixui.widget.control;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Coordinates the selection of several check boxes.
 * A group can be exclusive, which turns its members into radio buttons, and can enforce minimum
 * and maximum numbers of selected members. When the maximum is exceeded, the oldest selection is
 * cleared to make room for the newly selected check box.
 */
public final class ToggleGroup {
    private final List<CheckBox> members = new ArrayList<>();
    private final List<CheckBox> selected = new ArrayList<>();
    private boolean exclusive;
    private int minimumSelected;
    private int maximumSelected = Integer.MAX_VALUE;

    /**
     * Returns the group members in insertion order.
     *
     * @return an immutable snapshot of the members
     */
    public List<CheckBox> members() {
        return List.copyOf(members);
    }

    /**
     * Returns the selected members in selection order.
     *
     * @return an immutable snapshot of the selected members
     */
    public List<CheckBox> selected() {
        return List.copyOf(selected);
    }

    /**
     * Reports whether the group allows only one selected member.
     *
     * @return true when the selection is exclusive
     */
    public boolean isExclusive() {
        return exclusive;
    }

    /**
     * Enables or disables exclusive selection.
     * Enabling exclusivity keeps the most recently selected member and clears the others.
     *
     * @param exclusive true to allow only one selected member
     */
    public void setExclusive(boolean exclusive) {
        this.exclusive = exclusive;
        if (!exclusive || selected.size() <= 1) {
            return;
        }
        CheckBox keeper = selected.getLast();
        for (CheckBox box : List.copyOf(selected)) {
            if (box != keeper) {
                box.applyChecked(false);
            }
        }
        selected.clear();
        selected.add(keeper);
    }

    /**
     * Returns the minimum number of selected members.
     *
     * @return the minimum selected count
     */
    public int minimumSelected() {
        return minimumSelected;
    }

    /**
     * Sets the minimum number of selected members.
     * A member cannot be cleared while the minimum is reached.
     *
     * @param minimumSelected non-negative minimum count
     * @throws IllegalArgumentException if the count is negative or above the maximum
     */
    public void setMinimumSelected(int minimumSelected) {
        if (minimumSelected < 0 || minimumSelected > maximumSelected) {
            throw new IllegalArgumentException("Minimum selection is out of range.");
        }
        this.minimumSelected = minimumSelected;
    }

    /**
     * Returns the maximum number of selected members.
     *
     * @return the maximum selected count
     */
    public int maximumSelected() {
        return maximumSelected;
    }

    /**
     * Sets the maximum number of selected members.
     * Selecting beyond the limit clears the oldest selection first.
     *
     * @param maximumSelected positive maximum count
     * @throws IllegalArgumentException if the count is not positive or below the minimum
     */
    public void setMaximumSelected(int maximumSelected) {
        if (maximumSelected <= 0 || maximumSelected < minimumSelected) {
            throw new IllegalArgumentException("Maximum selection is out of range.");
        }
        this.maximumSelected = maximumSelected;
        while (selected.size() > maximumSelected) {
            CheckBox oldest = selected.removeFirst();
            oldest.applyChecked(false);
        }
    }

    /**
     * Adds a check box to the group.
     * A check box that already belongs to another group is moved to this one.
     *
     * @param box check box to add
     * @throws NullPointerException if {@code box} is null
     */
    public void add(CheckBox box) {
        Objects.requireNonNull(box, "box");
        if (members.contains(box)) {
            return;
        }
        if (box.group() != null) {
            box.group().remove(box);
        }
        members.add(box);
        box.bindGroup(this);
        if (box.isChecked()) {
            requestSelection(box, true);
        }
    }

    /**
     * Removes a check box from the group without changing its selection state.
     *
     * @param box check box to remove
     * @throws NullPointerException if {@code box} is null
     */
    public void remove(CheckBox box) {
        Objects.requireNonNull(box, "box");
        if (!members.remove(box)) {
            return;
        }
        selected.remove(box);
        box.bindGroup(null);
    }

    /**
     * Clears every selected member.
     * The minimum selection limit is not applied.
     */
    public void clear() {
        for (CheckBox box : List.copyOf(selected)) {
            box.applyChecked(false);
        }
        selected.clear();
    }

    /**
     * Applies a selection request that respects the group limits.
     *
     * @param box member requesting the change
     * @param checked requested state
     * @return true when the group accepted the change
     */
    boolean requestSelection(CheckBox box, boolean checked) {
        if (!members.contains(box)) {
            return false;
        }
        if (checked) {
            if (selected.contains(box)) {
                return false;
            }
            if (exclusive || maximumSelected == 1) {
                for (CheckBox other : List.copyOf(selected)) {
                    other.applyChecked(false);
                }
                selected.clear();
            } else if (selected.size() >= maximumSelected) {
                CheckBox oldest = selected.removeFirst();
                oldest.applyChecked(false);
            }
            selected.add(box);
            box.applyChecked(true);
            return true;
        }
        if (!selected.contains(box) || selected.size() <= minimumSelected) {
            return false;
        }
        selected.remove(box);
        box.applyChecked(false);
        return true;
    }
}

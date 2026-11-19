package com.lixui.input;

import com.lixui.input.event.CharTyped;

/**
 * A backend-independent keyboard key.
 * Printable characters arrive separately through {@link CharTyped}, so key
 * events are intended for
 * shortcuts and navigation rather than text entry. Key codes are
 * layout-independent; the character
 * produced by the current system layout is reported by {@link CharTyped}.
 */
public enum Key {
  /** An unknown or unsupported key. */
  UNKNOWN,
  /** The A key. */
  A,
  /** The B key. */
  B,
  /** The C key. */
  C,
  /** The D key. */
  D,
  /** The E key. */
  E,
  /** The F key. */
  F,
  /** The G key. */
  G,
  /** The H key. */
  H,
  /** The I key. */
  I,
  /** The J key. */
  J,
  /** The K key. */
  K,
  /** The L key. */
  L,
  /** The M key. */
  M,
  /** The N key. */
  N,
  /** The O key. */
  O,
  /** The P key. */
  P,
  /** The Q key. */
  Q,
  /** The R key. */
  R,
  /** The S key. */
  S,
  /** The T key. */
  T,
  /** The U key. */
  U,
  /** The V key. */
  V,
  /** The W key. */
  W,
  /** The X key. */
  X,
  /** The Y key. */
  Y,
  /** The Z key. */
  Z,
  /** The 0 digit key on the main keyboard. */
  DIGIT_0,
  /** The 1 digit key on the main keyboard. */
  DIGIT_1,
  /** The 2 digit key on the main keyboard. */
  DIGIT_2,
  /** The 3 digit key on the main keyboard. */
  DIGIT_3,
  /** The 4 digit key on the main keyboard. */
  DIGIT_4,
  /** The 5 digit key on the main keyboard. */
  DIGIT_5,
  /** The 6 digit key on the main keyboard. */
  DIGIT_6,
  /** The 7 digit key on the main keyboard. */
  DIGIT_7,
  /** The 8 digit key on the main keyboard. */
  DIGIT_8,
  /** The 9 digit key on the main keyboard. */
  DIGIT_9,
  /** The Escape key. */
  ESCAPE,
  /** The Enter key. */
  ENTER,
  /** The Tab key. */
  TAB,
  /** The Space key. */
  SPACE,
  /** The Backspace key. */
  BACKSPACE,
  /** The Delete key. */
  DELETE,
  /** The Insert key. */
  INSERT,
  /** The left arrow key. */
  LEFT,
  /** The right arrow key. */
  RIGHT,
  /** The up arrow key. */
  UP,
  /** The down arrow key. */
  DOWN,
  /** The Home key. */
  HOME,
  /** The End key. */
  END,
  /** The Page Up key. */
  PAGE_UP,
  /** The Page Down key. */
  PAGE_DOWN,
  /** The left Shift key. */
  SHIFT_LEFT,
  /** The right Shift key. */
  SHIFT_RIGHT,
  /** The left Control key. */
  CONTROL_LEFT,
  /** The right Control key. */
  CONTROL_RIGHT,
  /** The left Alt key. */
  ALT_LEFT,
  /** The right Alt key. */
  ALT_RIGHT,
  /** The left Super key. */
  SUPER_LEFT,
  /** The right Super key. */
  SUPER_RIGHT,
  /** The Caps Lock key. */
  CAPS_LOCK,
  /** The Scroll Lock key. */
  SCROLL_LOCK,
  /** The Num Lock key. */
  NUM_LOCK,
  /** The Print Screen key. */
  PRINT_SCREEN,
  /** The Pause key. */
  PAUSE,
  /** The Menu key. */
  MENU,
  /** The F1 key. */
  F1,
  /** The F2 key. */
  F2,
  /** The F3 key. */
  F3,
  /** The F4 key. */
  F4,
  /** The F5 key. */
  F5,
  /** The F6 key. */
  F6,
  /** The F7 key. */
  F7,
  /** The F8 key. */
  F8,
  /** The F9 key. */
  F9,
  /** The F10 key. */
  F10,
  /** The F11 key. */
  F11,
  /** The F12 key. */
  F12,
  /** The F13 key. */
  F13,
  /** The F14 key. */
  F14,
  /** The F15 key. */
  F15,
  /** The F16 key. */
  F16,
  /** The F17 key. */
  F17,
  /** The F18 key. */
  F18,
  /** The F19 key. */
  F19,
  /** The F20 key. */
  F20,
  /** The F21 key. */
  F21,
  /** The F22 key. */
  F22,
  /** The F23 key. */
  F23,
  /** The F24 key. */
  F24,
  /** The F25 key. */
  F25,
  /** The minus key. */
  MINUS,
  /** The equal key. */
  EQUAL,
  /** The left bracket key. */
  LEFT_BRACKET,
  /** The right bracket key. */
  RIGHT_BRACKET,
  /** The backslash key. */
  BACKSLASH,
  /** The semicolon key. */
  SEMICOLON,
  /** The apostrophe key. */
  APOSTROPHE,
  /** The grave accent key. */
  GRAVE,
  /** The comma key. */
  COMMA,
  /** The period key. */
  PERIOD,
  /** The slash key. */
  SLASH,
  /** The 0 key on the numeric keypad. */
  KEYPAD_0,
  /** The 1 key on the numeric keypad. */
  KEYPAD_1,
  /** The 2 key on the numeric keypad. */
  KEYPAD_2,
  /** The 3 key on the numeric keypad. */
  KEYPAD_3,
  /** The 4 key on the numeric keypad. */
  KEYPAD_4,
  /** The 5 key on the numeric keypad. */
  KEYPAD_5,
  /** The 6 key on the numeric keypad. */
  KEYPAD_6,
  /** The 7 key on the numeric keypad. */
  KEYPAD_7,
  /** The 8 key on the numeric keypad. */
  KEYPAD_8,
  /** The 9 key on the numeric keypad. */
  KEYPAD_9,
  /** The decimal separator key on the numeric keypad. */
  KEYPAD_DECIMAL,
  /** The divide key on the numeric keypad. */
  KEYPAD_DIVIDE,
  /** The multiply key on the numeric keypad. */
  KEYPAD_MULTIPLY,
  /** The subtract key on the numeric keypad. */
  KEYPAD_SUBTRACT,
  /** The add key on the numeric keypad. */
  KEYPAD_ADD,
  /** The Enter key on the numeric keypad. */
  KEYPAD_ENTER,
  /** The equal key on the numeric keypad. */
  KEYPAD_EQUAL,
  /** The first non-printable world key. */
  WORLD_1,
  /** The second non-printable world key. */
  WORLD_2,
}

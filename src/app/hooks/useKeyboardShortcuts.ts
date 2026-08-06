import { useCallback, useEffect } from "react";
import { useUiStore } from "../stores/uiStore";

/**
 * Callbacks the keyboard shortcut system dispatches to the host component.
 *
 * Each is optional because not every host binds every action (the ReviewPage, for example,
 * does not expose criterion navigation). Unbound shortcuts are silently ignored.
 */
export interface ShortcutActions {
  /** Ctrl+S — persist the current grading record. */
  save?: () => void;
  /** Ctrl+Shift+Right — save then advance to the next submission. */
  saveAndNext?: () => void;
  /** Ctrl+Shift+Left — save then go back to the previous submission. */
  saveAndPrevious?: () => void;
  /** Alt+Down — move focus to the next criterion card. */
  nextCriterion?: () => void;
  /** Alt+Up — move focus to the previous criterion card. */
  previousCriterion?: () => void;
  /** 1-9, 0 (for 10th) — select a performance level on the focused criterion. */
  selectLevel?: (index: number) => void;
  /** Ctrl+] — advance to the next passage of the focused criterion. */
  nextPassage?: () => void;
  /** Ctrl+[ — return to the previous passage of the focused criterion. */
  previousPassage?: () => void;
  /** Enter on focused passage — confirm the match. */
  confirmMatch?: () => void;
  /** Backspace/Delete on focused passage — reject the match. */
  rejectMatch?: () => void;
  /** Alt+F — move focus to the feedback textarea. */
  focusFeedback?: () => void;
}

/**
 * Returns true when the event target is a text input or textarea where single-key shortcuts
 * would interfere with typing.
 */
function isTextInputFocused(event: KeyboardEvent): boolean {
  const target = event.target as HTMLElement | null;
  if (!target) return false;
  const tag = target.tagName.toLowerCase();
  if (tag === "textarea") return true;
  if (tag === "input") {
    const type = (target as HTMLInputElement).type.toLowerCase();
    return ["text", "search", "url", "tel", "email", "password", "number"].includes(type);
  }
  if (target.isContentEditable) return true;
  return false;
}

/**
 * Registers keyboard shortcuts for the marking workflow.
 *
 * Call from a top-level component (e.g. MarkingPage) and pass an actions object.
 * All shortcuts are suppressed when a text input is focused, except for Ctrl-combos.
 *
 * Requirements: 17.1-17.14
 */
export function useKeyboardShortcuts(actions: ShortcutActions) {
  const setShowShortcutOverlay = useUiStore((s) => s.setShowShortcutOverlay);

  const handler = useCallback(
    (event: KeyboardEvent) => {
      const inTextInput = isTextInputFocused(event);
      const { key, ctrlKey, shiftKey, altKey, metaKey } = event;

      // Shift+? — open keyboard shortcut reference overlay (works everywhere)
      if (shiftKey && key === "?") {
        event.preventDefault();
        setShowShortcutOverlay(true);
        return;
      }

      // --- Ctrl combos (work even when text input is focused) ---

      if (ctrlKey && !altKey && !metaKey) {
        // Ctrl+Shift+ArrowRight — save and advance
        if (shiftKey && key === "ArrowRight") {
          event.preventDefault();
          actions.saveAndNext?.();
          return;
        }

        // Ctrl+Shift+ArrowLeft — save and go back
        if (shiftKey && key === "ArrowLeft") {
          event.preventDefault();
          actions.saveAndPrevious?.();
          return;
        }

        // Ctrl+S — save current grading record
        if (!shiftKey && key.toLowerCase() === "s") {
          event.preventDefault();
          actions.save?.();
          return;
        }

        // Ctrl+] — next passage
        if (!shiftKey && key === "]") {
          event.preventDefault();
          actions.nextPassage?.();
          return;
        }

        // Ctrl+[ — previous passage
        if (!shiftKey && key === "[") {
          event.preventDefault();
          actions.previousPassage?.();
          return;
        }
      }

      // --- Alt combos (work even when text input is focused) ---

      if (altKey && !ctrlKey && !metaKey && !shiftKey) {
        // Alt+Down — next criterion
        if (key === "ArrowDown") {
          event.preventDefault();
          actions.nextCriterion?.();
          return;
        }

        // Alt+Up — previous criterion
        if (key === "ArrowUp") {
          event.preventDefault();
          actions.previousCriterion?.();
          return;
        }

        // Alt+F — focus feedback field
        if (key.toLowerCase() === "f") {
          event.preventDefault();
          actions.focusFeedback?.();
          return;
        }
      }

      // --- Single-key shortcuts: suppressed when text input is focused ---
      if (inTextInput) return;

      // Number keys 1-9, 0 for 10th — select performance level
      if (!ctrlKey && !altKey && !metaKey && !shiftKey) {
        if (key >= "1" && key <= "9") {
          event.preventDefault();
          actions.selectLevel?.(parseInt(key, 10) - 1);
          return;
        }
        if (key === "0") {
          event.preventDefault();
          actions.selectLevel?.(9);
          return;
        }

        // Enter — confirm match on focused passage
        if (key === "Enter") {
          event.preventDefault();
          actions.confirmMatch?.();
          return;
        }

        // Backspace or Delete — reject match on focused passage
        if (key === "Backspace" || key === "Delete") {
          event.preventDefault();
          actions.rejectMatch?.();
          return;
        }
      }
    },
    [actions, setShowShortcutOverlay]
  );

  useEffect(() => {
    document.addEventListener("keydown", handler);
    return () => document.removeEventListener("keydown", handler);
  }, [handler]);
}

/**
 * All defined keyboard shortcuts, for display in the overlay.
 */
export const SHORTCUT_DEFINITIONS = [
  { keys: "Ctrl+S", description: "Save current grading record" },
  { keys: "Ctrl+Shift+\u2192", description: "Save and advance to next submission" },
  { keys: "Ctrl+Shift+\u2190", description: "Save and return to previous submission" },
  { keys: "Alt+\u2193", description: "Focus next criterion card" },
  { keys: "Alt+\u2191", description: "Focus previous criterion card" },
  { keys: "1\u20139, 0", description: "Select performance level (0 = 10th)" },
  { keys: "Ctrl+]", description: "Next passage of focused criterion" },
  { keys: "Ctrl+[", description: "Previous passage of focused criterion" },
  { keys: "Enter", description: "Confirm match on focused passage" },
  { keys: "Backspace / Delete", description: "Reject match on focused passage" },
  { keys: "Alt+F", description: "Focus feedback field" },
  { keys: "Shift+?", description: "Open this shortcut reference" },
] as const;

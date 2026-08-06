import { create } from "zustand";

interface UiState {
  /** Currently focused criterion ID in the rubric panel */
  focusedCriterionId: string | null;
  /** Whether keyboard shortcut overlay is visible */
  showShortcutOverlay: boolean;
  /** Confidence threshold for match filtering (0-1) */
  confidenceThreshold: number;

  setFocusedCriterionId: (id: string | null) => void;
  setShowShortcutOverlay: (show: boolean) => void;
  setConfidenceThreshold: (threshold: number) => void;
}

export const useUiStore = create<UiState>((set) => ({
  focusedCriterionId: null,
  showShortcutOverlay: false,
  confidenceThreshold: 0,

  setFocusedCriterionId: (id) => set({ focusedCriterionId: id }),
  setShowShortcutOverlay: (show) => set({ showShortcutOverlay: show }),
  setConfidenceThreshold: (threshold) => set({ confidenceThreshold: threshold }),
}));

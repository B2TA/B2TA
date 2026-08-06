import { create } from "zustand"
import type { CriterionScore } from "../types"

interface GradingState {
  /** Whether there are unsaved changes to the current grading record */
  hasUnsavedChanges: boolean
  /** Timestamp of the last successful save */
  lastSavedAt: string | null
  /** In-progress criterion scores (not yet persisted) */
  draftScores: CriterionScore[]
  /** Overall feedback text draft */
  draftFeedback: string

  setHasUnsavedChanges: (value: boolean) => void
  setLastSavedAt: (timestamp: string | null) => void
  setDraftScores: (scores: CriterionScore[]) => void
  setDraftFeedback: (feedback: string) => void
  reset: () => void
}

const initialState = {
  hasUnsavedChanges: false,
  lastSavedAt: null,
  draftScores: [] as CriterionScore[],
  draftFeedback: "",
}

export const useGradingStore = create<GradingState>((set) => ({
  ...initialState,
  setHasUnsavedChanges: (value) => set({ hasUnsavedChanges: value }),
  setLastSavedAt: (timestamp) => set({ lastSavedAt: timestamp }),
  setDraftScores: (scores) =>
    set({ draftScores: scores, hasUnsavedChanges: true }),
  setDraftFeedback: (feedback) =>
    set({ draftFeedback: feedback, hasUnsavedChanges: true }),
  reset: () => set(initialState),
}))

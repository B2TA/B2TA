import { create } from "zustand";
import type { ConfirmedMatch, Criterion, CriterionScore, GradingRecord } from "../types";

interface DraftScore {
  criterionId: string;
  selectedLevelId: string | null;
  overridePoints: number | null;
  criterionFeedback: string;
}

interface GradingState {
  /** Whether there are unsaved changes to the current grading record */
  hasUnsavedChanges: boolean;
  /** Timestamp of the last successful save */
  lastSavedAt: string | null;
  /** In-progress criterion scores (not yet persisted) */
  draftScores: DraftScore[];
  /** In-progress confirmed matches (not yet persisted) */
  draftMatches: ConfirmedMatch[];
  /** Overall feedback text draft */
  draftFeedback: string;
  /** Confidence threshold for filtering suggested matches */
  confidenceThreshold: number;
  /** Match IDs that have been confirmed locally */
  confirmedMatchIds: Set<string>;
  /** Match IDs that have been rejected locally */
  rejectedMatchIds: Set<string>;

  setHasUnsavedChanges: (value: boolean) => void;
  setLastSavedAt: (timestamp: string | null) => void;
  setDraftScores: (scores: DraftScore[]) => void;
  setDraftMatches: (matches: ConfirmedMatch[]) => void;
  setDraftFeedback: (feedback: string) => void;
  setConfidenceThreshold: (value: number) => void;
  confirmMatch: (matchId: string) => void;
  rejectMatch: (matchId: string) => void;
  updateScore: (criterionId: string, patch: Partial<DraftScore>) => void;
  initFromRecord: (record: GradingRecord, criteria: Criterion[]) => void;
  reset: () => void;
}

const initialState = {
  hasUnsavedChanges: false,
  lastSavedAt: null,
  draftScores: [] as DraftScore[],
  draftMatches: [] as ConfirmedMatch[],
  draftFeedback: "",
  confidenceThreshold: 0,
  confirmedMatchIds: new Set<string>(),
  rejectedMatchIds: new Set<string>(),
};

export const useGradingStore = create<GradingState>((set, get) => ({
  ...initialState,

  setHasUnsavedChanges: (value) => set({ hasUnsavedChanges: value }),
  setLastSavedAt: (timestamp) => set({ lastSavedAt: timestamp }),
  setDraftScores: (scores) => set({ draftScores: scores, hasUnsavedChanges: true }),
  setDraftMatches: (matches) => set({ draftMatches: matches, hasUnsavedChanges: true }),
  setDraftFeedback: (feedback) => set({ draftFeedback: feedback, hasUnsavedChanges: true }),
  setConfidenceThreshold: (value) => set({ confidenceThreshold: value }),

  confirmMatch: (matchId) => {
    const confirmed = new Set(get().confirmedMatchIds);
    confirmed.add(matchId);
    const rejected = new Set(get().rejectedMatchIds);
    rejected.delete(matchId);
    set({ confirmedMatchIds: confirmed, rejectedMatchIds: rejected, hasUnsavedChanges: true });
  },

  rejectMatch: (matchId) => {
    const rejected = new Set(get().rejectedMatchIds);
    rejected.add(matchId);
    const confirmed = new Set(get().confirmedMatchIds);
    confirmed.delete(matchId);
    set({ rejectedMatchIds: rejected, confirmedMatchIds: confirmed, hasUnsavedChanges: true });
  },

  updateScore: (criterionId, patch) => {
    const scores = [...get().draftScores];
    const idx = scores.findIndex((s) => s.criterionId === criterionId);
    if (idx >= 0) {
      scores[idx] = { ...scores[idx], ...patch };
    } else {
      scores.push({
        criterionId,
        selectedLevelId: null,
        overridePoints: null,
        criterionFeedback: "",
        ...patch,
      });
    }
    set({ draftScores: scores, hasUnsavedChanges: true });
  },

  initFromRecord: (record, criteria) => {
    const stored = new Map(record.criterionScores.map((s) => [s.criterionId, s]));
    const draftScores: DraftScore[] = criteria
      .filter((c) => c.id)
      .map((c) => {
        const score = stored.get(c.id!);
        return {
          criterionId: c.id!,
          selectedLevelId: score?.selectedLevelId ?? null,
          overridePoints: score?.overridePoints ?? null,
          criterionFeedback: score?.criterionFeedback ?? "",
        };
      });

    set({
      draftScores,
      draftFeedback: record.overallFeedback,
      draftMatches: record.confirmedMatches,
      hasUnsavedChanges: false,
      lastSavedAt: record.savedAt,
      confirmedMatchIds: new Set(),
      rejectedMatchIds: new Set(),
    });
  },

  reset: () => set(initialState),
}));

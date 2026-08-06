import { useEffect, useMemo, useRef, useState } from "react";
import { useGradingRecord, useRubric, useSubmissions } from "../../api/queries";
import { ErrorBanner, LoadingPanel, EmptyState } from "../Feedback";
import RubricPanel from "./RubricPanel";
import DocumentViewer from "./DocumentViewer";
import MatchPopover from "./MatchPopover";
import PassageNavigation from "./PassageNavigation";
import ConfidenceFilter from "./ConfidenceFilter";
import FeedbackEditor from "./FeedbackEditor";
import BatchNavigator from "./BatchNavigator";
import SaveIndicator from "./SaveIndicator";
import { useGradingStore } from "../../stores/gradingStore";
import type { Criterion, GradingRecord, SuggestedMatch, ConfirmedMatch } from "../../types";

/**
 * Main container for the marking view (Task 8.1).
 *
 * Loads rubric, current submission grading record, and exposes
 * loading/error states. Passes data down to child components.
 */
export default function MarkingView({
  sessionId,
  submissionId,
  onSubmissionChange,
}: {
  sessionId: string;
  submissionId: string;
  onSubmissionChange: (id: string) => void;
}) {
  const rubric = useRubric(sessionId);
  const grading = useGradingRecord(sessionId, submissionId || undefined);
  const submissions = useSubmissions(sessionId);

  const store = useGradingStore();
  const [selectedCriterionId, setSelectedCriterionId] = useState<string | null>(null);
  const [activePassageIndex, setActivePassageIndex] = useState(0);

  const criteria = useMemo<Criterion[]>(() => rubric.data?.criteria ?? [], [rubric.data]);

  // Initialize store when grading record arrives
  const loadedKey = useRef<string | null>(null);
  useEffect(() => {
    const record = grading.data;
    if (!record || criteria.length === 0) return;
    const key = `${record.submissionId}:${record.savedAt ?? "unsaved"}`;
    if (loadedKey.current === key) return;
    loadedKey.current = key;

    store.initFromRecord(record, criteria);
    setSelectedCriterionId(criteria[0]?.id ?? null);
    setActivePassageIndex(0);
  }, [grading.data, criteria]);

  // Filter matches by confidence threshold
  const confidenceThreshold = store.confidenceThreshold;
  const filteredSuggested = useMemo(() => {
    const record = grading.data;
    if (!record) return [];
    return record.suggestedMatches.filter((m) => m.confidence >= confidenceThreshold);
  }, [grading.data, confidenceThreshold]);

  const hiddenCount = useMemo(() => {
    const record = grading.data;
    if (!record) return 0;
    return record.suggestedMatches.length - filteredSuggested.length;
  }, [grading.data, filteredSuggested]);

  if (rubric.isLoading || submissions.isLoading) {
    return <LoadingPanel label="Loading rubric and submissions..." />;
  }

  if (rubric.error) {
    return <ErrorBanner error={rubric.error} onRetry={() => void rubric.refetch()} />;
  }

  if (!rubric.data) {
    return <EmptyState title="No rubric configured for this session" />;
  }

  if (!submissions.data || submissions.data.length === 0) {
    return <EmptyState title="No submissions in this session" />;
  }

  const record = grading.data;
  const extractedText = record?.extractedText ?? "";

  return (
    <div className="flex h-full flex-col">
      {/* Top bar: batch navigation + save indicator */}
      <div className="flex items-center justify-between border-b border-slate-200 bg-white px-4 py-2">
        <BatchNavigator
          sessionId={sessionId}
          submissionId={submissionId}
          submissions={submissions.data}
          position={record?.position ?? 1}
          batchSize={record?.batchSize ?? submissions.data.length}
          onNavigate={onSubmissionChange}
        />
        <div className="flex items-center gap-3">
          <ConfidenceFilter
            threshold={confidenceThreshold}
            onThresholdChange={store.setConfidenceThreshold}
            hiddenCount={hiddenCount}
          />
          <SaveIndicator sessionId={sessionId} submissionId={submissionId} />
        </div>
      </div>

      {/* Error states */}
      {grading.error && (
        <ErrorBanner
          error={grading.error}
          onRetry={() => void grading.refetch()}
          className="mx-4 mt-2"
        />
      )}

      {/* Loading state for grading record */}
      {grading.isLoading && <LoadingPanel label="Loading submission grading..." />}

      {/* Main content */}
      {record && (
        <div className="grid flex-1 gap-4 overflow-hidden p-4 lg:grid-cols-[minmax(0,400px)_minmax(0,1fr)]">
          {/* Left: Rubric Panel */}
          <div className="flex max-h-full flex-col overflow-hidden">
            <RubricPanel
              criteria={criteria}
              record={record}
              filteredSuggested={filteredSuggested}
              selectedCriterionId={selectedCriterionId}
              onSelectCriterion={setSelectedCriterionId}
            />
          </div>

          {/* Right: Document + nav + feedback */}
          <div className="flex max-h-full flex-col gap-3 overflow-hidden">
            {extractedText.length > 0 ? (
              <>
                <PassageNavigation
                  criteria={criteria}
                  suggested={filteredSuggested}
                  confirmed={record.confirmedMatches}
                  selectedCriterionId={selectedCriterionId}
                  activePassageIndex={activePassageIndex}
                  onPassageIndexChange={setActivePassageIndex}
                  onSelectCriterion={setSelectedCriterionId}
                />
                <div className="min-h-0 flex-1">
                  <DocumentViewer
                    text={extractedText}
                    suggested={filteredSuggested}
                    confirmed={record.confirmedMatches}
                    criteria={criteria}
                    selectedCriterionId={selectedCriterionId}
                    sessionId={sessionId}
                    submissionId={submissionId}
                  />
                </div>
              </>
            ) : (
              <EmptyState title="No extracted text available for this submission" />
            )}

            <FeedbackEditor sessionId={sessionId} submissionId={submissionId} />
          </div>
        </div>
      )}
    </div>
  );
}

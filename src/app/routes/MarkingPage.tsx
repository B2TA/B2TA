import { useState } from "react";
import { useParams, useSearchParams } from "react-router";
import AppShell from "../components/AppShell";
import { MarkingView } from "../components/marking";
import { useSubmissions } from "../api/queries";

/**
 * Marking route page (updated per Task 8.x).
 *
 * Extracts sessionId from URL params, tracks current submissionId in state,
 * and delegates all rendering to the MarkingView component.
 */
export default function MarkingPage() {
  const { id: sessionId = "" } = useParams<{ id: string }>();
  const [searchParams, setSearchParams] = useSearchParams();
  const submissions = useSubmissions(sessionId);

  // The submission in the URL, defaulting to the first in batch order
  const submissionId = searchParams.get("submission") ?? submissions.data?.[0]?.id ?? "";

  const handleSubmissionChange = (newId: string) => {
    setSearchParams({ submission: newId });
  };

  return (
    <AppShell title="Marking">
      <div className="h-[calc(100vh-64px)]">
        <MarkingView
          sessionId={sessionId}
          submissionId={submissionId}
          onSubmissionChange={handleSubmissionChange}
        />
      </div>
    </AppShell>
  );
}

import { useState } from "react";
import { useSuggestComments } from "../../api/queries";
import { useGradingStore } from "../../stores/gradingStore";
import { Badge } from "../Feedback";

/**
 * Overall feedback textarea and AI comment suggestion (Task 8.7).
 *
 * - Overall feedback textarea (max 10,000 chars)
 * - "Suggest Comments" button -> POST /comments/suggest -> show snippets
 * - Insert snippet at cursor, mark as AI-generated
 * - Handle failure with retry button
 */

export default function FeedbackEditor({
  sessionId,
  submissionId,
}: {
  sessionId: string;
  submissionId: string;
}) {
  const store = useGradingStore();
  const suggestComments = useSuggestComments(sessionId, submissionId);
  const [snippets, setSnippets] = useState<Array<{ text: string; isAiGenerated: boolean }>>([]);

  const handleSuggest = async () => {
    try {
      const result = await suggestComments.mutateAsync({
        currentDraft: store.draftFeedback,
      });
      setSnippets(result.snippets);
    } catch {
      // Error displayed via mutation state
    }
  };

  const insertSnippet = (text: string) => {
    const current = store.draftFeedback;
    const newFeedback = current.length === 0 ? text : `${current}\n\n${text}`;
    store.setDraftFeedback(newFeedback);
  };

  return (
    <div className="rounded border border-slate-200 bg-white p-4">
      <div className="flex items-center justify-between">
        <label htmlFor="overall-feedback" className="text-sm font-semibold text-slate-900">
          Overall Feedback
        </label>
        <span className="text-xs text-slate-400">{store.draftFeedback.length}/10000</span>
      </div>

      <textarea
        id="overall-feedback"
        rows={5}
        maxLength={10000}
        value={store.draftFeedback}
        onChange={(e) => store.setDraftFeedback(e.target.value)}
        placeholder="Write feedback for the student..."
        className="mt-2 w-full rounded border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none focus:ring-1 focus:ring-slate-500"
      />

      {/* Suggest Comments button */}
      <div className="mt-2 flex items-center gap-2">
        <button
          type="button"
          onClick={() => void handleSuggest()}
          disabled={suggestComments.isPending}
          className="rounded border border-slate-300 px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-50"
        >
          {suggestComments.isPending ? "Generating..." : "Suggest Comments"}
        </button>

        {suggestComments.isError && (
          <button
            type="button"
            onClick={() => void handleSuggest()}
            className="rounded border border-red-300 px-2 py-1 text-xs text-red-700"
          >
            Retry
          </button>
        )}
      </div>

      {/* Error display */}
      {suggestComments.error && (
        <p className="mt-2 text-xs text-red-700" role="alert">
          {suggestComments.error.message ?? "Failed to generate suggestions. Try again."}
        </p>
      )}

      {/* Snippets */}
      {snippets.length > 0 && (
        <ul className="mt-3 space-y-2">
          {snippets.map((snippet, i) => (
            <li
              key={i}
              className="rounded border border-sky-200 bg-sky-50 p-3 text-xs text-slate-800"
            >
              <div className="mb-1">
                <Badge tone="info">AI-generated</Badge>
              </div>
              <p className="leading-relaxed">{snippet.text}</p>
              <button
                type="button"
                onClick={() => insertSnippet(snippet.text)}
                className="mt-2 rounded bg-slate-900 px-2 py-1 text-xs font-medium text-white hover:bg-slate-800"
              >
                Insert into feedback
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

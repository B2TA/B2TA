import { useParams } from "react-router";

export default function MarkingPage() {
  const { id } = useParams<{ id: string }>();

  return (
    <div className="p-6">
      <h1 className="text-2xl font-semibold">Marking View</h1>
      <p className="text-sm text-gray-500">Session: {id}</p>
      {/* TODO: RubricPanel, DocumentViewer, FeedbackEditor, BatchNavigator */}
    </div>
  );
}

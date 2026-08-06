import { useParams } from "react-router";

export default function SessionSetupPage() {
  const { id } = useParams<{ id: string }>();

  return (
    <div className="p-6">
      <h1 className="text-2xl font-semibold">Session Setup</h1>
      <p className="text-sm text-gray-500">Session: {id}</p>
      {/* TODO: Rubric upload/editor, submission upload, ingestion report, student confirmation */}
    </div>
  );
}

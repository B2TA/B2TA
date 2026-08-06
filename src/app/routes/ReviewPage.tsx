import { useParams } from "react-router";

export default function ReviewPage() {
  const { id } = useParams<{ id: string }>();

  return (
    <div className="p-6">
      <h1 className="text-2xl font-semibold">Review</h1>
      <p className="text-sm text-gray-500">Session: {id}</p>
      {/* TODO: Grade summary table, flags, export gate */}
    </div>
  );
}

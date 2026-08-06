import { useEffect } from "react";
import { useUiStore } from "../stores/uiStore";
import { SHORTCUT_DEFINITIONS } from "../hooks/useKeyboardShortcuts";

/**
 * Modal overlay showing all keyboard shortcuts in a table.
 *
 * Opened with the Shift+? shortcut and closed with Escape.
 * Requirements: 17.13, 17.14
 */
export default function KeyboardShortcutOverlay() {
  const show = useUiStore((s) => s.showShortcutOverlay);
  const setShow = useUiStore((s) => s.setShowShortcutOverlay);

  useEffect(() => {
    if (!show) return;

    const handleEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        setShow(false);
      }
    };

    document.addEventListener("keydown", handleEscape);
    return () => document.removeEventListener("keydown", handleEscape);
  }, [show, setShow]);

  if (!show) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
      onClick={() => setShow(false)}
      role="dialog"
      aria-modal="true"
      aria-label="Keyboard shortcuts"
    >
      <div
        className="mx-4 max-h-[80vh] w-full max-w-lg overflow-y-auto rounded-lg bg-white shadow-xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between border-b border-slate-200 px-6 py-4">
          <h2 className="text-base font-semibold text-slate-900">Keyboard Shortcuts</h2>
          <button
            type="button"
            onClick={() => setShow(false)}
            className="rounded p-1 text-slate-500 hover:bg-slate-100 hover:text-slate-700 focus:outline-none focus:ring-2 focus:ring-slate-400"
            aria-label="Close"
          >
            <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        <table className="w-full text-left text-sm">
          <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th scope="col" className="px-6 py-2">Shortcut</th>
              <th scope="col" className="px-6 py-2">Action</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {SHORTCUT_DEFINITIONS.map((shortcut) => (
              <tr key={shortcut.keys}>
                <td className="px-6 py-2.5">
                  <kbd className="rounded border border-slate-300 bg-slate-100 px-2 py-0.5 font-mono text-xs text-slate-700">
                    {shortcut.keys}
                  </kbd>
                </td>
                <td className="px-6 py-2.5 text-slate-700">{shortcut.description}</td>
              </tr>
            ))}
          </tbody>
        </table>

        <div className="border-t border-slate-200 px-6 py-3 text-xs text-slate-500">
          Single-key shortcuts are suppressed when a text input is focused. Ctrl and Alt combos
          always work.
        </div>
      </div>
    </div>
  );
}

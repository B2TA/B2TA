import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import App from "./App";

// Production app entry point.
// This is separate from src/main.tsx (the Figma Make prototype entry).
// Wire into Vite via a separate HTML entry or multi-page config when ready to serve.

const root = document.getElementById("root");
if (root) {
  createRoot(root).render(
    <StrictMode>
      <App />
    </StrictMode>
  );
}

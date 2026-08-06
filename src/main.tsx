import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './app/App'
import './index.css'

// Mounts the production SPA in src/app, which talks to the Grading API.
// The original static Figma Make prototype is still in src/App.tsx and is no longer mounted; it was
// the UI reference for the marking view rather than a working screen.
ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
)

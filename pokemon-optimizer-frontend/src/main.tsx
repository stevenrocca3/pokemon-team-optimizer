import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router'
import './index.css'
import App from './App.tsx'
import { AnalysisJobProvider } from './analysis/AnalysisJobProvider'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      {/* the battle analysis is followed from the root, above the routes, so it keeps going whichever page is open */}
      <AnalysisJobProvider>
        <App />
      </AnalysisJobProvider>
    </BrowserRouter>
  </StrictMode>,
)

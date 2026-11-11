import { Link, Route, Routes } from 'react-router'
import { Header } from './components/Header'
import { SpeciesProvider } from './species/SpeciesProvider'
import { MovesProvider } from './moves/MovesProvider'
import { CollectionProvider } from './collection/CollectionProvider'
import { HomePage } from './pages/HomePage'
import { SpeciesPage } from './pages/SpeciesPage'
import { CollectionPage } from './pages/CollectionPage'
import { ImportPage } from './pages/ImportPage'
import { TeamBuilderPage } from './pages/TeamBuilderPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { Feedback } from './components/Feedback'
import { PrivacyPage, TermsPage } from './pages/LegalPages'
import { usePageViews } from './lib/analytics'
import { AnalysisWidget } from './analysis/AnalysisWidget'
import { AnalyzePage } from './pages/AnalyzePage'
import { MatchesPage } from './pages/MatchesPage'

function App() {
  usePageViews()
  return (
    <SpeciesProvider>
      <CollectionProvider>
        <div className="flex min-h-screen flex-col">
          {/* the first Tab stop: keyboard users jump past the header and nav straight to the page */}
          <a
            href="#main"
            className="btn btn-primary btn-sm fixed top-3 left-3 z-50 -translate-y-20 focus:translate-y-0"
          >
            Skip to content
          </a>
          <Header />
          <main id="main" tabIndex={-1} className="flex-1 focus:outline-none">
            <Routes>
              <Route path="/" element={<HomePage />} />
              <Route
                path="/species/:id"
                element={
                  <MovesProvider>
                    <SpeciesPage />
                  </MovesProvider>
                }
              />
              <Route
                path="/collection"
                element={
                  <MovesProvider>
                    <CollectionPage />
                  </MovesProvider>
                }
              />
              <Route
                path="/import"
                element={
                  <MovesProvider>
                    <ImportPage />
                  </MovesProvider>
                }
              />
              <Route
                path="/team-builder"
                element={
                  <MovesProvider>
                    <TeamBuilderPage />
                  </MovesProvider>
                }
              />
              <Route path="/analyze" element={<AnalyzePage />} />
              <Route path="/matches" element={<MatchesPage />} />
              <Route path="/privacy" element={<PrivacyPage />} />
              <Route path="/terms" element={<TermsPage />} />
              <Route path="*" element={<NotFoundPage />} />
            </Routes>
          </main>
          <footer className="border-t border-line px-4 py-6 text-center text-xs leading-6 text-muted">
            <p>
              <Feedback variant="link" /> ·{' '}
              <Link to="/privacy" className="hover:text-ink">
                Privacy
              </Link>{' '}
              ·{' '}
              <Link to="/terms" className="hover:text-ink">
                Terms
              </Link>
            </p>
            <p>
              Unofficial fan site, not affiliated with Niantic, Nintendo or The Pokémon Company. Pokémon is a trademark of
              Nintendo, Creatures and GAME FREAK. Artwork from PokéAPI.
            </p>
          </footer>
          <Feedback variant="corner" />
          <AnalysisWidget />
        </div>
      </CollectionProvider>
    </SpeciesProvider>
  )
}

export default App

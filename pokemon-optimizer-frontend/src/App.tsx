import { Route, Routes } from 'react-router'
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

function App() {
  return (
    <SpeciesProvider>
      <CollectionProvider>
        <div className="flex min-h-screen flex-col">
          <Header />
          <main className="flex-1">
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
              <Route path="*" element={<NotFoundPage />} />
            </Routes>
          </main>
          <footer className="border-t border-line py-6 text-center text-xs text-subtle">
            Artwork from PokéAPI · Not affiliated with Niantic or The Pokémon Company
          </footer>
        </div>
      </CollectionProvider>
    </SpeciesProvider>
  )
}

export default App

import { Route, Routes } from 'react-router'
import { Header } from './components/Header'
import { SpeciesProvider } from './species/SpeciesProvider'
import { MovesProvider } from './moves/MovesProvider'
import { HomePage } from './pages/HomePage'
import { SpeciesPage } from './pages/SpeciesPage'
import { NotFoundPage } from './pages/NotFoundPage'

function App() {
  return (
    <SpeciesProvider>
      <MovesProvider>
        <div className="flex min-h-screen flex-col">
          <Header />
          <main className="flex-1">
            <Routes>
              <Route path="/" element={<HomePage />} />
              <Route path="/species/:id" element={<SpeciesPage />} />
              <Route path="*" element={<NotFoundPage />} />
            </Routes>
          </main>
          <footer className="border-t border-zinc-800 py-6 text-center text-xs text-zinc-500">
            Artwork from PokéAPI · Not affiliated with Niantic or The Pokémon Company
          </footer>
        </div>
      </MovesProvider>
    </SpeciesProvider>
  )
}

export default App

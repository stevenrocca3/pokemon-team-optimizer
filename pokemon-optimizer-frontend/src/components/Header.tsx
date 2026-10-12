import { Link, NavLink, useLocation } from 'react-router'
import { SearchBar } from './SearchBar'

export function Header() {
  const onHome = useLocation().pathname === '/'

  return (
    <header className="sticky top-0 z-30 border-b border-zinc-800 bg-zinc-950/95 backdrop-blur">
      <div className="mx-auto flex h-16 max-w-6xl items-center gap-4 px-4">
        <Link to="/" className="flex shrink-0 items-center text-lg font-black tracking-tight" aria-label="DexPresso home">
          <span className="rounded-l bg-brand px-2 py-1 text-black">Dex</span>
          <span className="rounded-r bg-zinc-800 px-2 py-1 text-zinc-100">Presso</span>
        </Link>
        {/* the home page has its own big search bar */}
        {!onHome && (
          <div className="ml-auto w-full max-w-xl">
            <SearchBar />
          </div>
        )}
        <NavLink
          to="/collection"
          className={({ isActive }) =>
            `shrink-0 text-sm font-semibold transition ${onHome ? 'ml-auto' : ''} ${
              isActive ? 'text-brand' : 'text-zinc-300 hover:text-zinc-100'
            }`
          }
        >
          Collection
        </NavLink>
      </div>
    </header>
  )
}

import { Link, NavLink, useLocation } from 'react-router'
import { SearchBar } from './SearchBar'
import { LogoMark } from './Logo'

export function Header() {
  const onHome = useLocation().pathname === '/'

  return (
    <header className="sticky top-0 z-30 border-b border-line bg-canvas/95 backdrop-blur">
      <div className="mx-auto flex h-16 max-w-6xl items-center gap-4 px-4">
        <Link to="/" className="group flex shrink-0 items-center gap-2 font-display text-xl font-black tracking-tight" aria-label="DexPresso home">
          <LogoMark className="h-8 w-8" />
          {/* on phones the search bar needs the room, so only the cup shows */}
          <span className={onHome ? '' : 'hidden sm:inline'}>
            <span className="text-ink">Dex</span>
            <span className="text-brand">Presso</span>
          </span>
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
              isActive ? 'text-brand' : 'text-ink-soft hover:text-ink'
            }`
          }
        >
          Collection
        </NavLink>
      </div>
    </header>
  )
}

import { Link, useLocation } from 'react-router'
import { SearchBar } from './SearchBar'

export function Header() {
  const onHome = useLocation().pathname === '/'

  return (
    <header className="sticky top-0 z-30 border-b border-zinc-800 bg-zinc-950/95 backdrop-blur">
      <div className="mx-auto flex h-16 max-w-6xl items-center gap-4 px-4">
        <Link to="/" className="flex shrink-0 items-center gap-2" aria-label="PvP IV Optimizer home">
          <span className="rounded bg-brand px-2 py-1 text-lg font-black tracking-tight text-black">PvP</span>
          <span className="hidden font-semibold text-zinc-200 sm:inline">IV Optimizer</span>
        </Link>
        {/* the home page has its own big search bar */}
        {!onHome && (
          <div className="ml-auto w-full max-w-xl">
            <SearchBar />
          </div>
        )}
      </div>
    </header>
  )
}

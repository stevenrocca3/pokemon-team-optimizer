import { Link, NavLink, useLocation } from 'react-router'
import { SearchBar } from './SearchBar'
import { LogoText } from './Creamer'
import { LogoMark } from './Logo'
import { AccountMenu } from './AccountMenu'

function navClass({ isActive }: { isActive: boolean }) {
  return `transition ${isActive ? 'text-brand' : 'text-ink-soft hover:text-ink'}`
}

export function Header() {
  const onHome = useLocation().pathname === '/'

  return (
    <header className="sticky top-0 z-30 border-b border-line bg-canvas/95 backdrop-blur">
      <div className="mx-auto flex h-16 max-w-6xl items-center gap-4 px-4">
        <Link to="/" className="group flex shrink-0 items-center gap-2" aria-label="DexPresso home">
          <LogoMark className="h-8 w-8" />
          {/* on phones the search bar needs the room, so only the cup shows */}
          <span className={onHome ? '' : 'hidden sm:inline'}>
            <LogoText text="DexPresso" className="text-[2.1rem]" />
          </span>
        </Link>
        {/* the home page has its own big search bar */}
        {!onHome && (
          <div className="ml-auto w-full max-w-xl">
            <SearchBar />
          </div>
        )}
        <nav className={`flex shrink-0 items-center gap-3 text-sm font-semibold sm:gap-5 ${onHome ? 'ml-auto' : ''}`}>
          <NavLink to="/team-builder" className={navClass}>
            Teams
          </NavLink>
          {/* phones need the room for search; there the Collection page header links to Import */}
          <NavLink to="/import" className={(state) => `hidden sm:inline ${navClass(state)}`}>
            Import
          </NavLink>
          <NavLink to="/analyze" className={(state) => `hidden sm:inline ${navClass(state)}`}>
            Analyze
          </NavLink>
          <NavLink to="/collection" className={navClass}>
            Collection
          </NavLink>
          <AccountMenu />
        </nav>
      </div>
    </header>
  )
}

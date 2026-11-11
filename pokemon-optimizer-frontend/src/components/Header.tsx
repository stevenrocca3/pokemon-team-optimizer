import { useEffect, useRef, useState } from 'react'
import { Link, NavLink, useLocation } from 'react-router'
import { SearchBar } from './SearchBar'
import { LogoText } from './Creamer'
import { LogoMark } from './Logo'
import { AccountMenu } from './AccountMenu'
import { useBuildCandidates } from '../collection/use-build-candidates'

function navClass({ isActive }: { isActive: boolean }) {
  return `transition ${isActive ? 'text-brand' : 'text-ink-soft hover:text-ink'}`
}

function BuildCount({ count }: { count: number }) {
  if (count === 0) return null
  return (
    <span className="grid h-5 min-w-5 place-items-center rounded-full bg-brand px-1.5 text-[11px] font-black tabular-nums text-on-brand">
      {count}
    </span>
  )
}

export function Header() {
  const { pathname } = useLocation()
  const onHome = pathname === '/'
  const toBuild = useBuildCandidates().length
  const [menuOpen, setMenuOpen] = useState(false)

  return (
    <header className="sticky top-0 z-30 border-b border-line bg-canvas/95 backdrop-blur">
      <div className="mx-auto flex h-16 max-w-6xl items-center gap-3 px-4 sm:gap-4">
        <Link to="/" className="group flex shrink-0 items-center gap-2" aria-label="DexPresso home">
          <LogoMark className="h-8 w-8" />
          {/* on phones only the cup shows: the search bar needs the room, and the home hero spells the name out */}
          <span className="hidden sm:inline">
            <LogoText text="DexPresso" className="text-[2.1rem]" />
          </span>
        </Link>
        {/* the home page has its own big search bar */}
        {!onHome && (
          <div className="ml-auto min-w-0 flex-1 sm:max-w-xl">
            <SearchBar />
          </div>
        )}
        {/* phones and tablets: every page sits behind the Menu button, so the search bar keeps its room */}
        <nav className={`hidden shrink-0 items-center gap-5 text-sm font-semibold lg:flex ${onHome ? 'ml-auto' : ''}`}>
          <NavLink to="/team-builder" className={navClass}>
            Teams
          </NavLink>
          <NavLink to="/import" className={navClass}>
            Import
          </NavLink>
          <NavLink to="/analyze" className={navClass}>
            Analyze
          </NavLink>
          {/* #118: always in the nav; the count is how many species you have two or more copies of */}
          <NavLink
            to="/build"
            aria-label={toBuild > 0 ? `Which One to Build: ${toBuild} to decide` : 'Which One to Build'}
            className={(state) => `inline-flex items-center gap-1.5 ${navClass(state)}`}
          >
            Build
            <BuildCount count={toBuild} />
          </NavLink>
          <NavLink to="/collection" className={navClass}>
            Collection
          </NavLink>
        </nav>
        <div className={`flex shrink-0 items-center gap-2 ${onHome ? 'ml-auto lg:ml-0' : ''}`}>
          <MenuButton open={menuOpen} onToggle={() => setMenuOpen((o) => !o)} badge={toBuild > 0} />
          <AccountMenu />
        </div>
      </div>
      {menuOpen && <PhoneMenu toBuild={toBuild} onClose={() => setMenuOpen(false)} />}
    </header>
  )
}

function MenuButton({ open, onToggle, badge }: { open: boolean; onToggle: () => void; badge: boolean }) {
  return (
    <button
      type="button"
      aria-label="Menu"
      aria-expanded={open}
      aria-controls="phone-menu"
      onClick={onToggle}
      // the phone menu's trigger; marked so a tap on it doesn't count as a tap outside the menu
      data-phone-menu-trigger=""
      className="relative grid h-9 w-9 place-items-center rounded-full bg-raised text-ink ring-1 ring-line-strong hover:bg-raised-strong lg:hidden"
    >
      <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true">
        {open ? <path d="M6 6l12 12M18 6L6 18" /> : <path d="M4 7h16M4 12h16M4 17h16" />}
      </svg>
      {/* a dot when there's a copy to decide on, since the count itself is in the menu */}
      {badge && !open && <span aria-hidden="true" className="absolute top-0 right-0 h-2.5 w-2.5 rounded-full bg-brand ring-2 ring-canvas" />}
    </button>
  )
}

/** The phone nav: a panel under the header, closed by picking a page, tapping outside or Escape. */
function PhoneMenu({ toBuild, onClose }: { toBuild: number; onClose: () => void }) {
  const panel = useRef<HTMLElement>(null)
  const { pathname } = useLocation()
  const opened = useRef(pathname)

  // going to another page closes it
  useEffect(() => {
    if (pathname !== opened.current) onClose()
  }, [pathname, onClose])

  useEffect(() => {
    const onPointer = (e: PointerEvent) => {
      const target = e.target as Element
      if (panel.current?.contains(target) || target.closest?.('[data-phone-menu-trigger]')) return
      onClose()
    }
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose()
    document.addEventListener('pointerdown', onPointer)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('pointerdown', onPointer)
      document.removeEventListener('keydown', onKey)
    }
  }, [onClose])

  const item = ({ isActive }: { isActive: boolean }) =>
    `flex h-12 items-center justify-between rounded-xl px-4 font-semibold ring-1 transition ${
      isActive ? 'bg-brand/12 text-brand ring-brand/60' : 'bg-surface text-ink ring-line hover:bg-raised'
    }`

  return (
    <nav
      ref={panel}
      id="phone-menu"
      aria-label="Menu"
      className="animate-rise absolute inset-x-0 top-full border-b border-line bg-canvas/95 px-4 pt-3 pb-4 shadow-2xl backdrop-blur lg:hidden"
    >
      <div className="mx-auto grid max-w-6xl grid-cols-2 gap-2 sm:grid-cols-5">
        <NavLink to="/team-builder" className={item}>
          Teams
        </NavLink>
        <NavLink to="/collection" className={item}>
          Collection
        </NavLink>
        <NavLink to="/analyze" className={item}>
          Analyze
        </NavLink>
        <NavLink to="/build" className={item}>
          Build
          <BuildCount count={toBuild} />
        </NavLink>
        <NavLink to="/import" className={(state) => `col-span-2 sm:col-span-1 ${item(state)}`}>
          Import
        </NavLink>
      </div>
    </nav>
  )
}

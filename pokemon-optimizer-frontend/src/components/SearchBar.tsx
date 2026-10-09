import { useId, useMemo, useRef, useState, type KeyboardEvent } from 'react'
import { useNavigate } from 'react-router'
import { useSpecies } from '../species/species-context'
import { formatDex, searchSpecies } from '../lib/species'
import { PokemonArt } from './PokemonArt'
import type { SpeciesOption } from '../types'

interface Props {
  size?: 'compact' | 'large'
  autoFocus?: boolean
}

export function SearchBar({ size = 'compact', autoFocus = false }: Props) {
  const species = useSpecies()
  const navigate = useNavigate()
  const listId = useId()
  const inputRef = useRef<HTMLInputElement>(null)

  const [query, setQuery] = useState('')
  const [open, setOpen] = useState(false)
  const [activeIndex, setActiveIndex] = useState(0)

  const results = useMemo(
    () => (species.status === 'ready' ? searchSpecies(species.list, query) : []),
    [species, query],
  )
  const showList = open && query.trim() !== '' && species.status === 'ready'

  function select(option: SpeciesOption) {
    setQuery('')
    setOpen(false)
    inputRef.current?.blur()
    navigate(`/species/${encodeURIComponent(option.id)}`)
  }

  function onKeyDown(e: KeyboardEvent<HTMLInputElement>) {
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      setOpen(true)
      setActiveIndex((i) => Math.min(i + 1, results.length - 1))
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      setActiveIndex((i) => Math.max(i - 1, 0))
    } else if (e.key === 'Enter') {
      const option = results[activeIndex] ?? results[0]
      if (option) select(option)
    } else if (e.key === 'Escape') {
      setOpen(false)
    }
  }

  const placeholder =
    species.status === 'loading'
      ? 'Loading species…'
      : species.status === 'error'
        ? 'Species unavailable — is the backend running?'
        : 'Search 1,100+ Pokémon by name or dex #'

  const large = size === 'large'

  return (
    <div className="relative w-full">
      <div
        className={`flex items-center gap-2 rounded-md bg-white text-zinc-900 ring-brand focus-within:ring-2 ${
          large ? 'h-14 px-4' : 'h-10 px-3'
        }`}
      >
        <svg viewBox="0 0 24 24" className={`shrink-0 text-zinc-500 ${large ? 'h-6 w-6' : 'h-5 w-5'}`} fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
          <circle cx="11" cy="11" r="7" />
          <path d="m20 20-3.5-3.5" />
        </svg>
        <input
          ref={inputRef}
          type="text"
          role="combobox"
          aria-expanded={showList}
          aria-controls={listId}
          aria-autocomplete="list"
          aria-activedescendant={showList && results[activeIndex] ? `${listId}-${activeIndex}` : undefined}
          aria-label="Search Pokémon"
          autoFocus={autoFocus}
          disabled={species.status !== 'ready'}
          placeholder={placeholder}
          value={query}
          onChange={(e) => {
            setQuery(e.target.value)
            setActiveIndex(0)
            setOpen(true)
          }}
          onFocus={() => setOpen(true)}
          onBlur={() => setOpen(false)}
          onKeyDown={onKeyDown}
          className={`w-full min-w-0 bg-transparent outline-none placeholder:text-zinc-500 disabled:cursor-not-allowed ${
            large ? 'text-lg' : 'text-sm'
          }`}
        />
      </div>

      {showList && (
        <ul
          id={listId}
          role="listbox"
          // keep focus in the input so onBlur doesn't close the list before the click lands
          onMouseDown={(e) => e.preventDefault()}
          className="absolute z-20 mt-1 w-full overflow-hidden rounded-md border border-zinc-800 bg-zinc-900 shadow-2xl"
        >
          {results.length === 0 ? (
            <li className="px-4 py-3 text-sm text-zinc-400">No Pokémon match “{query.trim()}”</li>
          ) : (
            results.map((option, index) => (
              <li
                key={option.id}
                id={`${listId}-${index}`}
                role="option"
                aria-selected={index === activeIndex}
                onMouseEnter={() => setActiveIndex(index)}
                onClick={() => select(option)}
                className={`flex cursor-pointer items-center gap-3 px-3 py-2 ${
                  index === activeIndex ? 'bg-zinc-800' : ''
                }`}
              >
                <PokemonArt dex={option.dex} alt="" className="h-10 w-10 shrink-0" />
                <div className="min-w-0">
                  <div className="truncate font-medium text-zinc-100">
                    {option.name}
                  </div>
                  <div className="text-xs text-zinc-500">{formatDex(option.dex)}</div>
                </div>
              </li>
            ))
          )}
        </ul>
      )}
    </div>
  )
}

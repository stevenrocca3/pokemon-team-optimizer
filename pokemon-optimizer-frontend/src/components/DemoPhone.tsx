import { useEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react'
import { PokemonArt } from './PokemonArt'
import { useSpecies } from '../species/species-context'
import { prefersReducedMotion } from '../lib/motion'
import { DEMO_RUN, demoFrame, typed, type DemoFrame } from '../lib/demo-run'

const STEP_CAPTIONS = [
  `Battling each Pokémon 1v1 against all ${DEMO_RUN.metaSize} meta picks…`,
  'Ranking teams by how well they cover the meta…',
  'Trying all 6 lineups in full 3v3s, shields and all…',
  `Measuring the win odds on ${DEMO_RUN.best.battles} fresh battles…`,
]
const STEP_LABELS = ['1v1s vs the meta', 'Rank by coverage', '3v3s, every lineup', '400 fresh battles']
const ROLES = ['Lead', 'Safe swap', 'Closer']
const LEAGUES = ['Great', 'Ultra', 'Master']
// shown with reduced motion: the result, fully typed
const STILL_MS = 8800 + 5000

/** What the dialogue box is saying, and when that line started (so it types out from there). */
function caption(frame: DemoFrame): { text: string; since: number } {
  switch (frame.phase) {
    case 'pick':
      return { text: 'Ticking the Pokémon in your box…', since: frame.phaseMs }
    case 'league':
      return { text: `${DEMO_RUN.league}: everyone powered up to just under 1500 CP.`, since: frame.phaseMs }
    case 'tap':
      return { text: 'Build teams!', since: frame.phaseMs }
    case 'brew':
      return { text: STEP_CAPTIONS[frame.stepsDone], since: frame.phaseMs - frame.stepsDone * 1200 }
    default:
      return {
        text: `Best three found: wins ${Math.round(DEMO_RUN.best.rate * 100)}% of ${DEMO_RUN.best.battles} battles. Lead with Giratina!`,
        since: frame.resultMs,
      }
  }
}

/**
 * The home page's live preview: a pixel phone running the Team Builder on a real saved result (lib/demo-run.ts).
 * Plays only while on screen; with reduced motion it just shows the result.
 */
export function DemoPhone({ onFrame }: { onFrame?: (frame: DemoFrame) => void }) {
  const ref = useRef<HTMLDivElement>(null)
  const ms = useDemoClock(ref)
  const frame = demoFrame(ms)
  const line = caption(frame)
  const species = useSpecies()
  const dexOf = (id: string) => (species.status === 'ready' ? species.byId.get(id)?.dex : undefined)
  const nameOf = (id: string) => (species.status === 'ready' ? species.byId.get(id)?.name : undefined) ?? id

  useEffect(() => onFrame?.(frame), [frame.phase, frame.stepsDone]) // eslint-disable-line react-hooks/exhaustive-deps

  const setup = frame.phase === 'pick' || frame.phase === 'league' || frame.phase === 'tap'
  const leagueIndex = frame.phase === 'pick' ? 2 : 0
  const pressed = frame.phase === 'tap' && frame.phaseMs > 500 && frame.phaseMs < 750

  return (
    <div ref={ref} className="relative mx-auto w-[300px] select-none" aria-label={`A Team Builder run on a phone: ${line.text}`} role="img">
      {/* the case: a creamer outline, then the espresso body, stepped at the corners like pixel art */}
      <div className="bg-creamer/80 p-[4px]" style={pixelCorners(8)}>
        <div className="bg-[#2a1d15] px-4 pt-7 pb-9" style={pixelCorners(7)}>
          <div className="mx-auto mb-3 h-[6px] w-16 bg-black/50" />
          {/* the screen */}
          <div className="relative h-[500px] overflow-hidden bg-canvas" style={pixelCorners(4)}>
            <div className="flex items-center justify-between px-3 pt-2 font-display text-[11px] text-subtle">
              <span>DEXPRESSO</span>
              <span className="flex gap-[2px]" aria-hidden="true">
                {[0, 1, 2].map((i) => (
                  <span key={i} className="h-[8px] w-[4px] bg-brand" />
                ))}
              </span>
            </div>
            <p className="px-3 pt-1 font-display text-lg text-ink">Team Builder</p>

            {/* setup: the box filling up, the league, the button */}
            <View shown={setup}>
              <p className="px-3 text-[11px] font-semibold text-muted">Your Pokémon</p>
              <ul className="grid grid-cols-4 gap-1.5 px-3 pt-1.5">
                {DEMO_RUN.box.map((id, i) => (
                  <li
                    key={id}
                    className={`relative aspect-square bg-surface p-1 transition-[transform,opacity] duration-200 ease-out ${
                      i < frame.picked ? 'scale-100 opacity-100' : 'scale-50 opacity-0'
                    }`}
                    style={pixelCorners(3)}
                  >
                    <PokemonArt dex={dexOf(id)} alt="" className="h-full w-full" />
                    <span className="absolute top-0.5 right-0.5 h-2 w-2 bg-brand" />
                  </li>
                ))}
              </ul>
              <p className="px-3 pt-3 text-[11px] font-semibold text-muted">League</p>
              <div className="relative mx-3 mt-1.5 grid grid-cols-3 bg-raised p-1" style={pixelCorners(3)}>
                <span
                  className="absolute top-1 bottom-1 left-1 w-[calc((100%-0.5rem)/3)] bg-brand inset-shadow-[0_2px_0] inset-shadow-creamer/60 transition-transform duration-300 ease-out"
                  style={{ ...pixelCorners(2), transform: `translateX(${leagueIndex * 100}%)` }}
                />
                {LEAGUES.map((l, i) => (
                  <span
                    key={l}
                    className={`relative py-1.5 text-center text-xs font-bold transition-colors duration-300 ${i === leagueIndex ? 'text-on-brand' : 'text-subtle'}`}
                  >
                    {l}
                  </span>
                ))}
              </div>
              <div
                className={`mx-3 mt-4 bg-brand py-2.5 text-center text-sm font-bold text-on-brand transition-transform duration-100 ${pressed ? 'translate-y-[3px]' : ''}`}
                style={{
                  ...pixelCorners(3),
                  boxShadow: pressed ? 'inset 0 3px 0 rgb(241 220 192 / 0.7)' : 'inset 0 3px 0 rgb(241 220 192 / 0.7), 0 4px 0 #392a1c',
                }}
              >
                Build teams
              </div>
              {/* the pixel hand: glides to the button and taps it */}
              <PixelHand
                className={`absolute transition-[left,top,opacity] duration-500 ease-out ${frame.phase === 'tap' ? 'opacity-100' : 'opacity-0'}`}
                style={frame.phase === 'tap' && frame.phaseMs > 50 ? { left: 168, top: 372 } : { left: 250, top: 460 }}
              />
            </View>

            {/* brewing: the cup fills while the steps tick off */}
            <View shown={frame.phase === 'brew'}>
              <PixelCup level={frame.phase === 'brew' ? Math.min(1, frame.phaseMs / 4800) : 0} />
              <ol className="mt-3 grid gap-1.5 px-4 text-xs">
                {STEP_LABELS.map((label, i) => {
                  const done = i < frame.stepsDone
                  const now = i === frame.stepsDone
                  return (
                    <li key={label} className={`flex items-center gap-2 transition-colors duration-200 ${done ? 'text-ink' : now ? 'text-brand' : 'text-faint'}`}>
                      <span className={`grid h-4 w-4 place-items-center text-[10px] font-bold ${done ? 'bg-brand text-on-brand' : 'bg-raised'}`}>
                        {done ? '✓' : now ? <span className="h-1.5 w-1.5 animate-pulse bg-brand" /> : ''}
                      </span>
                      {label}
                    </li>
                  )
                })}
              </ol>
            </View>

            {/* the result: odds counting up, the lineup sliding in */}
            <View shown={frame.phase === 'result' || frame.phase === 'fade'} fading={frame.phase === 'fade'}>
              <Result resultMs={frame.resultMs} dexOf={dexOf} nameOf={nameOf} />
            </View>

            {/* the game-style dialogue box, typing out what's happening */}
            <div className="absolute inset-x-2 bottom-2 min-h-[64px] border-2 border-creamer/80 bg-[#1d140e] px-2.5 py-2 text-[12px] leading-snug text-ink">
              {typed(line.text, line.since)}
              {typed(line.text, line.since).length < line.text.length ? (
                <span className="ml-px inline-block h-3 w-1.5 translate-y-0.5 animate-pulse bg-creamer" />
              ) : (
                <span className="absolute right-2 bottom-1 animate-bounce text-[10px] text-creamer">▼</span>
              )}
            </div>
            {/* faint scanlines over the whole screen */}
            <div className="pointer-events-none absolute inset-0 bg-[repeating-linear-gradient(to_bottom,transparent_0_2px,rgb(0_0_0/0.12)_2px_3px)]" />
          </div>
          <div className="mx-auto mt-4 h-[12px] w-[12px] bg-black/40" />
        </div>
      </div>
    </div>
  )
}

function Result({ resultMs, dexOf, nameOf }: { resultMs: number; dexOf: (id: string) => number | undefined; nameOf: (id: string) => string }) {
  const { rate, low, high, battles, members } = DEMO_RUN.best
  const t = Math.min(1, resultMs / 1200)
  const shown = rate * (1 - Math.pow(1 - t, 3)) // ease-out cubic
  const R = 30
  const C = 2 * Math.PI * R
  return (
    <div className="px-3 pt-1">
      <div className="flex items-center gap-3">
        <div className="relative h-[76px] w-[76px] shrink-0">
          <svg viewBox="0 0 76 76" className="h-full w-full -rotate-90" aria-hidden="true">
            <circle cx="38" cy="38" r={R} fill="none" strokeWidth="8" className="stroke-raised" />
            <circle
              cx="38"
              cy="38"
              r={R}
              fill="none"
              strokeWidth="8"
              className="stroke-brand"
              style={{ strokeDasharray: `${shown * C} ${C}`, opacity: shown < 0.005 ? 0 : 1 }}
            />
          </svg>
          <span className="absolute inset-0 grid place-items-center font-display text-lg text-ink">{Math.round(shown * 100)}%</span>
        </div>
        <div className="text-xs">
          <p className="font-display text-sm text-brand">Best Team</p>
          <p className="text-subtle">
            Likely <span className="tabular-nums">{Math.round(low * 100)}–{Math.round(high * 100)}%</span>
          </p>
          <p className="text-subtle">
            <span className="tabular-nums">{battles}</span> battles
          </p>
        </div>
      </div>
      <ul className="mt-2 grid gap-1.5">
        {members.map((m, i) => {
          const in_ = resultMs > 500 + i * 250
          return (
            <li
              key={m.speciesId}
              className={`flex items-center gap-2 bg-surface p-1.5 transition-[transform,opacity] duration-300 ease-out ${
                in_ ? 'translate-x-0 opacity-100' : 'translate-x-6 opacity-0'
              } ${i === 0 ? 'ring-1 ring-brand/70' : ''}`}
              style={pixelCorners(3)}
            >
              <PokemonArt dex={dexOf(m.speciesId)} alt="" className="h-9 w-9 shrink-0" />
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-1.5">
                  <span className="truncate text-xs font-semibold">{nameOf(m.speciesId)}</span>
                  <span className={`px-1 text-[9px] font-bold uppercase ${i === 0 ? 'bg-brand text-on-brand' : 'bg-raised-strong text-ink-soft'}`}>
                    {ROLES[i]}
                  </span>
                </div>
                <div className="mt-1 flex gap-1" aria-hidden="true">
                  {m.ivs.map((iv, k) => (
                    <span key={k} className="h-1 w-8 bg-raised">
                      <span
                        className="creamer-fill block h-1 transition-[width] duration-500 ease-out"
                        style={{ width: in_ ? `${(iv / 15) * 100}%` : '0%', transitionDelay: `${k * 80}ms` }}
                      />
                    </span>
                  ))}
                  <span className="ml-1 text-[9px] text-subtle tabular-nums">{m.cp} CP</span>
                </div>
              </div>
            </li>
          )
        })}
      </ul>
    </div>
  )
}

/** One screen of the demo, crossfading in and out. */
function View({ shown, fading = false, children }: { shown: boolean; fading?: boolean; children: ReactNode }) {
  return (
    <div
      className={`absolute inset-x-0 top-[62px] bottom-[76px] transition-[opacity,transform] duration-300 ease-out ${
        shown && !fading ? 'translate-y-0 opacity-100' : 'pointer-events-none translate-y-2 opacity-0'
      }`}
    >
      {children}
    </div>
  )
}

/** A cup drawn in pixels, filling with espresso, with creamer swirling in near the end. */
function PixelCup({ level }: { level: number }) {
  return (
    <svg viewBox="0 0 32 28" className="mx-auto mt-2 h-36 w-40" shapeRendering="crispEdges" aria-hidden="true">
      <clipPath id="demo-cup">
        <rect x="6" y="8" width="16" height="15" />
      </clipPath>
      <g clipPath="url(#demo-cup)">
        <rect x="6" y={23 - 15 * level} width="16" height="15" fill="#5a2f17" className="transition-[y] duration-200" />
        <rect x="6" y={23 - 15 * level} width="16" height="2" fill="#c88a3e" className="transition-[y] duration-200" />
        {level > 0.7 && (
          <g fill="#f1dcc0" opacity={Math.min(1, (level - 0.7) * 4)}>
            <rect x="10" y="15" width="3" height="1" />
            <rect x="13" y="16" width="4" height="1" />
            <rect x="16" y="17" width="2" height="2" />
            <rect x="12" y="19" width="4" height="1" />
          </g>
        )}
      </g>
      {/* cup outline and handle */}
      <g fill="#f3e9dc">
        <rect x="5" y="7" width="1" height="16" />
        <rect x="22" y="7" width="1" height="16" />
        <rect x="6" y="23" width="16" height="1" />
        <rect x="23" y="10" width="3" height="1" />
        <rect x="26" y="11" width="1" height="5" />
        <rect x="23" y="16" width="3" height="1" />
        <rect x="3" y="25" width="22" height="1" opacity="0.3" />
      </g>
      {/* steam */}
      <g fill="#f3e9dc" opacity="0.5" className="animate-pulse">
        <rect x="10" y="2" width="1" height="3" />
        <rect x="11" y="0" width="1" height="2" />
        <rect x="16" y="1" width="1" height="3" />
        <rect x="17" y="0" width="1" height="1" />
      </g>
    </svg>
  )
}

/** A pointing hand in pixels. */
function PixelHand({ className, style }: { className: string; style: CSSProperties }) {
  const rows = ['..X.....', '.XWX....', '.XWX....', '.XWXXX..', '.XWWWWX.', 'XXWWWWX.', 'XWWWWWX.', '.XWWWX..', '..XXXX..']
  return (
    <svg viewBox="0 0 8 9" className={`h-9 w-8 ${className}`} style={style} shapeRendering="crispEdges" aria-hidden="true">
      {rows.flatMap((row, y) =>
        [...row].map((c, x) => (c === '.' ? null : <rect key={`${x}-${y}`} x={x} y={y} width="1" height="1" fill={c === 'X' ? '#1d140e' : '#f3e9dc'} />)),
      )}
    </svg>
  )
}

/** Corners stepped like pixel art, `s` px per step, as a clip-path. */
function pixelCorners(s: number): CSSProperties {
  const a = `${s}px`
  const b = `${2 * s}px`
  const W = (d: string) => `calc(100% - ${d})`
  return {
    clipPath: `polygon(${b} 0, ${W(b)} 0, ${W(b)} ${a}, ${W(a)} ${a}, ${W(a)} ${b}, 100% ${b}, 100% ${W(b)}, ${W(a)} ${W(b)}, ${W(a)} ${W(a)}, ${W(b)} ${W(a)}, ${W(b)} 100%, ${b} 100%, ${b} ${W(a)}, ${a} ${W(a)}, ${a} ${W(b)}, 0 ${W(b)}, 0 ${b}, ${a} ${b}, ${a} ${a}, ${b} ${a})`,
  }
}

/** Milliseconds of demo played: runs only while the phone is on screen; frozen on the result with reduced motion. */
function useDemoClock(ref: React.RefObject<HTMLDivElement | null>): number {
  const [ms, setMs] = useState(() => (prefersReducedMotion() ? STILL_MS : 0))
  useEffect(() => {
    if (prefersReducedMotion() || !ref.current) return
    let timer = 0
    let last = 0
    const tick = () => {
      const now = performance.now()
      setMs((m) => m + (now - last))
      last = now
    }
    const observer = new IntersectionObserver(([entry]) => {
      window.clearInterval(timer)
      if (entry.isIntersecting) {
        last = performance.now()
        timer = window.setInterval(tick, 40)
      }
    })
    observer.observe(ref.current)
    return () => {
      observer.disconnect()
      window.clearInterval(timer)
    }
  }, [ref])
  return ms
}

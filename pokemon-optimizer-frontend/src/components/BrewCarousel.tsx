import { useCallback, useEffect, useRef, useState, type CSSProperties, type ReactNode, type RefObject } from 'react'
import { PokemonArt } from './PokemonArt'
import { useSpecies } from '../species/species-context'
import { prefersReducedMotion } from '../lib/motion'
import { pixelCorners } from '../lib/pixel'
import { DEMO_RUN, typed } from '../lib/demo-run'
import {
  TASTE_BATTLES,
  TASTE_GRID,
  doseFrame,
  grindFrame,
  lineups,
  pullFrame,
  tasteFrame,
  wrapIndex,
} from '../lib/brew-steps'

interface Look {
  dexOf: (id: string) => number | undefined
  nameOf: (id: string) => string
}

// the saved run's toughest meta picks (lib/demo-run.ts)
const META = ['tinkaton', 'jellicent', 'feraligatr']
const ROLES = ['Lead', 'Safe swap', 'Closer']

interface Step {
  title: string
  body: string
  screen: string
  Screen: (props: { ms: number; look: Look }) => ReactNode
  /** with autoplay on, move to the next step after this long */
  advanceMs: number
  /** the frame shown while the slide isn't playing, or with reduced motion */
  stillMs: number
}

// What the Team Builder does, in order (ADR 0005); the same steps it shows while it runs
const STEPS: Step[] = [
  {
    title: 'Grind',
    body: 'Each of your Pokémon battles every meta Pokémon 1v1 at the best level the league allows.',
    screen: '1v1s',
    Screen: GrindScreen,
    advanceMs: 8000,
    stillMs: 1000,
  },
  {
    title: 'Dose',
    body: 'Your ten strongest are combined into teams, ranked by how well they cover the meta together. The top five go on.',
    screen: 'Coverage',
    Screen: DoseScreen,
    advanceMs: 9600,
    stillMs: 3300,
  },
  {
    title: 'Pull',
    body: 'Those five play full 3v3s, with switching and shields, against 100 meta teams, in every lineup of lead, safe swap and closer.',
    screen: '3v3s',
    Screen: PullScreen,
    advanceMs: 12000,
    stillMs: 700,
  },
  {
    title: 'Taste',
    body: 'Win odds come from 400 fresh battles the teams were never picked on, with a 95% range around them.',
    screen: 'Win odds',
    Screen: TasteScreen,
    advanceMs: 6600,
    stillMs: 3600,
  },
]

/**
 * "From Beans to a Cup in Four Steps" as a carousel: swipe, drag, arrow keys, or the step tabs. Each slide's pixel
 * screen loops its step (lib/brew-steps.ts); only the slide in front plays. It moves on by itself until touched.
 */
export function BrewCarousel() {
  const viewport = useRef<HTMLDivElement>(null)
  const [active, setActive] = useState(0)
  const [auto, setAuto] = useState(() => !prefersReducedMotion())
  const [hovering, setHovering] = useState(false)
  const ms = useActiveClock(viewport, active)
  const species = useSpecies()
  const look: Look = {
    dexOf: (id) => (species.status === 'ready' ? species.byId.get(id)?.dex : undefined),
    nameOf: (id) => (species.status === 'ready' ? species.byId.get(id)?.name : undefined) ?? id,
  }

  const goTo = useCallback((i: number) => {
    const v = viewport.current
    const slide = v?.children[i] as HTMLElement | undefined
    if (!v || !slide) return
    v.scrollTo({ left: slide.offsetLeft - (v.clientWidth - slide.offsetWidth) / 2, behavior: prefersReducedMotion() ? 'auto' : 'smooth' })
    setActive(i)
  }, [])

  const userGoTo = (i: number) => {
    setAuto(false)
    goTo(wrapIndex(i, STEPS.length))
  }

  // autoplay: on to the next step once this one has played through
  const step = STEPS[active]
  useEffect(() => {
    if (auto && !hovering && ms !== null && ms >= step.advanceMs) goTo(wrapIndex(active + 1, STEPS.length))
  }, [auto, hovering, ms, step.advanceMs, active, goTo])

  // the slide nearest the middle is the active one, however it got there
  const onScroll = () => {
    const v = viewport.current
    if (!v) return
    const middle = v.scrollLeft + v.clientWidth / 2
    let nearest = 0
    let best = Infinity
    Array.from(v.children).forEach((child, i) => {
      const el = child as HTMLElement
      const d = Math.abs(el.offsetLeft + el.offsetWidth / 2 - middle)
      if (d < best) {
        best = d
        nearest = i
      }
    })
    setActive(nearest)
  }

  // dragging with a mouse (touch already swipes natively)
  const drag = useRef<{ x: number; left: number; from: number } | null>(null)
  const onPointerDown = (e: React.PointerEvent<HTMLDivElement>) => {
    setAuto(false)
    if (e.pointerType !== 'mouse' || !viewport.current) return
    drag.current = { x: e.clientX, left: viewport.current.scrollLeft, from: active }
    viewport.current.setPointerCapture(e.pointerId)
    viewport.current.style.scrollSnapType = 'none'
  }
  const onPointerMove = (e: React.PointerEvent<HTMLDivElement>) => {
    if (drag.current && viewport.current) viewport.current.scrollLeft = drag.current.left - (e.clientX - drag.current.x)
  }
  const onPointerUp = (e: React.PointerEvent<HTMLDivElement>) => {
    const d = drag.current
    if (!d || !viewport.current) return
    drag.current = null
    viewport.current.style.scrollSnapType = ''
    const dx = e.clientX - d.x
    const target = Math.abs(dx) > 40 ? d.from + (dx < 0 ? 1 : -1) : d.from
    goTo(Math.min(STEPS.length - 1, Math.max(0, target)))
  }

  return (
    <div
      role="region"
      aria-roledescription="carousel"
      className="[container-type:inline-size]"
      aria-label="How the Team Builder brews a team, in four steps"
      onPointerEnter={() => setHovering(true)}
      onPointerLeave={() => setHovering(false)}
    >
      <div
        ref={viewport}
        tabIndex={0}
        onScroll={onScroll}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerUp}
        onPointerCancel={onPointerUp}
        onWheel={(e) => Math.abs(e.deltaX) > Math.abs(e.deltaY) && setAuto(false)}
        onKeyDown={(e) => {
          if (e.key === 'ArrowRight') userGoTo(active + 1)
          else if (e.key === 'ArrowLeft') userGoTo(active - 1)
          else return
          e.preventDefault()
        }}
        className="no-scrollbar relative flex cursor-grab snap-x snap-mandatory gap-5 overflow-x-auto px-[6cqw] py-4 outline-none select-none focus-visible:ring-2 focus-visible:ring-brand active:cursor-grabbing md:px-[12cqw]"
      >
        {STEPS.map((s, i) => {
          const front = i === active
          return (
            <article
              key={s.title}
              role="group"
              aria-roledescription="slide"
              aria-label={`${i + 1} of ${STEPS.length}: ${s.title}`}
              aria-current={front ? 'step' : undefined}
              onClick={() => !front && userGoTo(i)}
              className={`card grid w-[88cqw] shrink-0 snap-center items-center gap-6 p-5 transition-[opacity,transform] duration-300 ease-out sm:p-7 md:w-[76cqw] md:grid-cols-[minmax(0,1.15fr)_minmax(0,1fr)] md:gap-8 ${
                front ? 'scale-100 opacity-100' : 'scale-[0.94] cursor-pointer opacity-45'
              }`}
            >
              <PixelScreen label={s.screen} playing={front && ms !== null}>
                <s.Screen ms={front && ms !== null ? ms : s.stillMs} look={look} />
              </PixelScreen>
              <div>
                <span
                  className="inline-grid h-11 w-11 place-items-center bg-raised font-display text-xl text-brand inset-shadow-[0_3px_0] inset-shadow-creamer/30"
                  style={pixelCorners(3)}
                >
                  {i + 1}
                </span>
                <h3 className="mt-4 font-display text-3xl text-ink sm:text-4xl">{s.title}</h3>
                <p className="mt-3 leading-relaxed text-ink-soft">{s.body}</p>
              </div>
            </article>
          )
        })}
      </div>

      {/* controls: back, the four steps (the one in front shows how long until it moves on), next */}
      <div className="mt-6 flex items-center justify-center gap-2 sm:gap-3">
        <PixelButton label="Previous step" onClick={() => userGoTo(active - 1)}>
          ◀
        </PixelButton>
        <ol className="flex gap-1.5 sm:gap-2">
          {STEPS.map((s, i) => {
            const front = i === active
            return (
              <li key={s.title}>
                <button
                  type="button"
                  onClick={() => userGoTo(i)}
                  aria-current={front ? 'step' : undefined}
                  className={`relative overflow-hidden px-2.5 py-2 text-xs font-bold transition-colors sm:px-4 sm:text-sm ${
                    front ? 'bg-brand text-on-brand inset-shadow-[0_3px_0] inset-shadow-creamer/60' : 'bg-raised text-ink-soft hover:bg-raised-strong'
                  }`}
                  style={pixelCorners(3)}
                >
                  <span className="font-mono">{i + 1}</span>
                  <span className="ml-1.5 hidden sm:inline">{s.title}</span>
                  {front && auto && ms !== null && (
                    <span
                      className="absolute bottom-0 left-0 h-[3px] bg-espresso/70"
                      style={{ width: `${Math.min(100, (ms / s.advanceMs) * 100)}%` }}
                    />
                  )}
                </button>
              </li>
            )
          })}
        </ol>
        <PixelButton label="Next step" onClick={() => userGoTo(active + 1)}>
          ▶
        </PixelButton>
      </div>
    </div>
  )
}

function PixelButton({ label, onClick, children }: { label: string; onClick: () => void; children: ReactNode }) {
  return (
    <button
      type="button"
      aria-label={label}
      onClick={onClick}
      className="grid h-10 w-10 place-items-center bg-raised text-sm text-brand transition-[background-color,transform] hover:bg-raised-strong active:translate-y-[2px]"
      style={pixelCorners(3)}
    >
      {children}
    </button>
  )
}

/** A little handheld: creamer outline, espresso bezel, a 4:3 screen with scanlines. */
function PixelScreen({ label, playing, children }: { label: string; playing: boolean; children: ReactNode }) {
  return (
    <div className="mx-auto w-full max-w-[420px] bg-creamer/80 p-[3px]" style={pixelCorners(6)} aria-hidden="true">
      <div className="bg-[#2a1d15] px-3 pt-3 pb-2" style={pixelCorners(5)}>
        <div className="relative aspect-[4/3] overflow-hidden bg-canvas" style={pixelCorners(3)}>
          {children}
          <div className="pointer-events-none absolute inset-0 bg-[repeating-linear-gradient(to_bottom,transparent_0_2px,rgb(0_0_0/0.12)_2px_3px)]" />
        </div>
        <div className="mt-2 flex items-center justify-between font-display text-[10px] tracking-wider text-subtle">
          <span>DEXPRESSO · {label.toUpperCase()}</span>
          <span className={`h-1.5 w-1.5 ${playing ? 'animate-pulse bg-brand' : 'bg-faint'}`} />
        </div>
      </div>
    </div>
  )
}

/** Centred at x%, y% of the screen. */
function at(x: number, y: number, width?: number): CSSProperties {
  return { left: `${x}%`, top: `${y}%`, width: width === undefined ? undefined : `${width}%`, transform: 'translate(-50%, -50%)' }
}

/** Blinks on and off every 50 ms while `on`, like a hit in a handheld game. */
const blink = (on: boolean, ms: number) => on && Math.floor(ms / 50) % 2 === 0

function Bar({ value, className = '' }: { value: number; className?: string }) {
  const colour = value > 50 ? 'bg-[#8fc46a]' : value > 20 ? 'bg-brand' : 'bg-danger'
  return (
    <span className={`block h-[5px] bg-raised ${className}`}>
      <span className={`block h-full transition-[width] duration-300 ease-out ${colour}`} style={{ width: `${value}%` }} />
    </span>
  )
}

// 1. Grind: a quick 1v1, then the next

function GrindScreen({ ms, look }: { ms: number; look: Look }) {
  const f = grindFrame(ms)
  const you = DEMO_RUN.box[f.round % DEMO_RUN.box.length]
  const foe = META[f.round % META.length]
  const youName = look.nameOf(you)
  const foeName = look.nameOf(foe)
  const done = f.round + (f.won === null ? 0 : 1)
  const losses = Math.floor(done / 3)
  const t = f.phaseMs
  const enter = Math.min(1, t / 300)
  const charged = f.attack === 'charged' || f.attack === 'foe-charged'
  const shake = charged ? (Math.floor(t / 40) % 2 ? 2 : -2) : 0

  const line =
    t < 300
      ? { text: `${foeName} steps up from the meta!`, since: t }
      : t < 1900
        ? { text: `${youName} and ${foeName} trade fast moves…`, since: t - 300 }
        : f.won === null
          ? { text: `${f.attack === 'charged' ? youName : foeName} fires a charged move!`, since: t - 1900 }
          : { text: f.won ? `${foeName} fainted. Win recorded!` : `${youName} fainted. Loss recorded.`, since: t - 2300 }

  return (
    <div className="absolute inset-0" style={{ transform: `translateX(${shake}px)` }}>
      <div className={`absolute inset-0 transition-opacity duration-300 ${f.leaving ? 'opacity-0' : 'opacity-100'}`}>
        {/* foe: info top-left, sprite top-right */}
        <div className="absolute top-[5%] left-[4%] w-[44%] bg-surface/90 px-2 py-1" style={pixelCorners(2)}>
          <p className="truncate text-[10px] font-semibold text-ink">{foeName}</p>
          <Bar value={f.foeHp} className="mt-0.5" />
        </div>
        <Platform x={72} y={44} />
        <div
          className="absolute aspect-square transition-[transform,opacity] duration-150 ease-out"
          style={{
            ...at(72, 30, 26),
            transform: `translate(-50%, -50%) translateX(${(1 - enter) * 60}%) ${f.attack === 'foe-fast' ? 'translate(-14%, 10%)' : ''} ${f.won === true ? 'translateY(40%)' : ''}`,
            opacity: f.won === true ? 0 : blink(f.attack === 'fast' || f.attack === 'charged', t) ? 0.2 : 1,
            transitionDuration: f.won === true ? '500ms' : undefined,
          }}
        >
          <PokemonArt dex={look.dexOf(foe)} alt="" className="h-full w-full" />
        </div>

        {/* you: sprite bottom-left (facing the foe), info right */}
        <Platform x={27} y={70} />
        <div
          className="absolute aspect-square transition-[transform,opacity] duration-150 ease-out"
          style={{
            ...at(27, 55, 30),
            transform: `translate(-50%, -50%) translateX(${-(1 - enter) * 60}%) ${f.attack === 'fast' ? 'translate(14%, -10%)' : ''} ${f.won === false ? 'translateY(40%)' : ''}`,
            opacity: f.won === false ? 0 : blink(f.attack === 'foe-fast' || f.attack === 'foe-charged', t) ? 0.2 : 1,
            transitionDuration: f.won === false ? '500ms' : undefined,
          }}
        >
          <Facing>
          <PokemonArt dex={look.dexOf(you)} alt="" className="h-full w-full" />
        </Facing>
        </div>
        <div className="absolute top-[47%] right-[4%] w-[44%] bg-surface/90 px-2 py-1" style={pixelCorners(2)}>
          <p className="truncate text-[10px] font-semibold text-ink">{youName}</p>
          <Bar value={f.youHp} className="mt-0.5" />
          <span className="mt-1 block h-[4px] bg-raised">
            <span className="creamer-fill block h-full transition-[width] duration-300" style={{ width: `${f.energy}%` }} />
          </span>
        </div>

        {/* a charged move: a marching pixel beam */}
        {charged && (
          <svg viewBox="0 0 400 300" className="absolute inset-0 h-full w-full" shapeRendering="crispEdges">
            <line
              x1={f.attack === 'charged' ? 120 : 280}
              y1={f.attack === 'charged' ? 160 : 92}
              x2={f.attack === 'charged' ? 280 : 120}
              y2={f.attack === 'charged' ? 92 : 160}
              strokeWidth="8"
              strokeDasharray="8 8"
              className={`animate-[beam-march_0.25s_linear_infinite] ${f.attack === 'charged' ? 'stroke-creamer' : 'stroke-danger'}`}
            />
          </svg>
        )}

        {f.won !== null && !f.leaving && (
          <span
            className={`absolute animate-[pop-in_0.25s_ease-out] px-3 py-1 font-display text-lg ${f.won ? 'bg-brand text-on-brand' : 'bg-raised-strong text-ink'}`}
            style={{ ...at(50, 40), ...pixelCorners(3) }}
          >
            {f.won ? 'WIN' : 'LOSS'}
          </span>
        )}
      </div>

      <span className="absolute top-[5%] right-[4%] font-mono text-[10px] text-subtle">
        W {done - losses} · L {losses}
      </span>
      <Dialogue text={line.text} since={line.since} />
    </div>
  )
}

/** Your side faces right, at the foe. (Flip a wrapper: the sprite's own fit transform is anchored top-left.) */
function Facing({ children }: { children: ReactNode }) {
  return <span className="block h-full w-full -scale-x-100">{children}</span>
}

function Platform({ x, y }: { x: number; y: number }) {
  return (
    <span className="absolute h-[5%] bg-raised-strong/70" style={{ ...at(x, y, 30), ...pixelCorners(4) }} />
  )
}

function Dialogue({ text, since }: { text: string; since: number }) {
  const shown = typed(text, since)
  return (
    <div className="absolute inset-x-[3%] bottom-[3%] h-[19%] border-2 border-creamer/80 bg-[#1d140e] px-2 py-1 text-[11px] leading-snug text-ink">
      {shown}
      {shown.length < text.length ? (
        <span className="ml-px inline-block h-2.5 w-1.5 translate-y-0.5 animate-pulse bg-creamer" />
      ) : (
        <span className="absolute right-1.5 bottom-0.5 animate-bounce text-[9px] text-creamer">▼</span>
      )}
    </div>
  )
}

// 2. Dose: Pokémon file into team folders, the teams stack into the ranked root

const LEAVES = DEMO_RUN.box.slice(0, 6)
const ROOT = { x: 200, y: 52 }
const TEAMS = [
  { x: 105, y: 150 },
  { x: 295, y: 150 },
]
const leafAt = (i: number) => ({ x: 40 + i * 64, y: 250 })

type Point = { x: number; y: number }
const leafPath = (i: number): Point[] => {
  const leaf = leafAt(i)
  const team = TEAMS[Math.floor(i / 3)]
  return [
    { x: leaf.x, y: leaf.y - 22 },
    { x: leaf.x, y: 205 },
    { x: team.x, y: 205 },
    { x: team.x, y: team.y + 20 },
  ]
}
const teamPath = (j: number): Point[] => [
  { x: TEAMS[j].x, y: TEAMS[j].y - 22 },
  { x: TEAMS[j].x, y: 102 },
  { x: ROOT.x, y: 102 },
  { x: ROOT.x, y: ROOT.y + 24 },
]

/** The point `t` (0 to 1) of the way along a polyline. */
function along(points: Point[], t: number): Point {
  const lengths = points.slice(1).map((p, i) => Math.abs(p.x - points[i].x) + Math.abs(p.y - points[i].y))
  let left = t * lengths.reduce((a, b) => a + b, 0)
  for (let i = 0; i < lengths.length; i++) {
    if (left <= lengths[i] || i === lengths.length - 1) {
      const k = lengths[i] === 0 ? 0 : Math.min(1, left / lengths[i])
      return { x: points[i].x + (points[i + 1].x - points[i].x) * k, y: points[i].y + (points[i + 1].y - points[i].y) * k }
    }
    left -= lengths[i]
  }
  return points[points.length - 1]
}

const pct = (p: Point) => ({ x: (p.x / 400) * 100, y: (p.y / 300) * 100 })

function DoseScreen({ ms, look }: { ms: number; look: Look }) {
  const f = doseFrame(ms)
  // each Pokémon sets off a moment after the last
  const leafP = LEAVES.map((_, i) => (f.leafProgress === 1 ? 1 : Math.min(1, Math.max(0, f.leafProgress * 1.4 - i * 0.08))))
  const filed = [0, 1].map((j) => leafP.slice(j * 3, j * 3 + 3).filter((p) => p === 1).length)
  const polyline = (pts: Point[]) => pts.map((p) => `${p.x},${p.y}`).join(' ')

  return (
    <div className={`absolute inset-0 transition-opacity duration-500 ${f.fading ? 'opacity-0' : 'opacity-100'}`}>
      <svg viewBox="0 0 400 300" className="absolute inset-0 h-full w-full" shapeRendering="crispEdges">
        {LEAVES.map((_, i) => (
          <polyline
            key={i}
            points={polyline(leafPath(i))}
            fill="none"
            strokeWidth="3"
            className={`transition-[stroke] duration-300 ${leafP[i] > 0 ? 'stroke-brand' : 'stroke-line-strong/50'}`}
          />
        ))}
        {TEAMS.map((_, j) => (
          <polyline
            key={j}
            points={polyline(teamPath(j))}
            fill="none"
            strokeWidth="3"
            className={`transition-[stroke] duration-300 ${f.teamProgress[j] > 0 ? 'stroke-creamer' : 'stroke-line-strong/50'}`}
          />
        ))}
      </svg>

      {/* the Pokémon, greyed once they've been filed */}
      {LEAVES.map((id, i) => {
        const p = pct(leafAt(i))
        return (
          <span
            key={id}
            className={`absolute aspect-square bg-surface p-[2px] transition-[opacity,filter] duration-300 ${leafP[i] > 0 ? 'opacity-40 grayscale' : ''}`}
            style={{ ...at(p.x, p.y, 13), ...pixelCorners(2) }}
          >
            <PokemonArt dex={look.dexOf(id)} alt="" className="h-full w-full" />
          </span>
        )
      })}
      {/* ...and the copies travelling up the branches */}
      {LEAVES.map((id, i) => {
        if (leafP[i] <= 0 || leafP[i] >= 1) return null
        const p = pct(along(leafPath(i), leafP[i]))
        return (
          <span key={id} className="absolute aspect-square" style={at(p.x, p.y, 9)}>
            <PokemonArt dex={look.dexOf(id)} alt="" className="h-full w-full" />
          </span>
        )
      })}

      {TEAMS.map((team, j) => {
        const p = pct(team)
        const moving = f.teamProgress[j] > 0 && f.teamProgress[j] < 1
        const q = pct(along(teamPath(j), f.teamProgress[j]))
        return (
          <div key={j}>
            <span className="absolute" style={at(p.x, p.y, 15)}>
              <Folder sheets={f.teamProgress[j] > 0 ? 0 : filed[j]} dim={f.teamProgress[j] > 0} />
            </span>
            {moving && (
              <span className="absolute" style={at(q.x, q.y, 11)}>
                <Folder sheets={3} />
              </span>
            )}
          </div>
        )
      })}

      <span className="absolute" style={at(pct(ROOT).x, pct(ROOT).y, 20)}>
        <Folder sheets={f.stacked * 2} glow={f.glow} />
      </span>
      {f.glow && (
        <span
          className="absolute animate-[pop-in_0.25s_ease-out] bg-brand px-1.5 py-0.5 font-display text-[10px] text-on-brand"
          style={{ ...at(pct(ROOT).x + 16, pct(ROOT).y - 9), ...pixelCorners(2) }}
        >
          #1
        </span>
      )}

      <span className="absolute top-[4%] left-[4%] font-display text-[10px] text-subtle">RANKED BY COVERAGE</span>
      <span className="absolute top-[45%] left-[4%] font-display text-[9px] text-faint">TEAMS</span>
      <span className="absolute bottom-[1%] left-[4%] font-display text-[9px] text-faint">YOUR STRONGEST</span>
    </div>
  )
}

/** A pixel folder with `sheets` stacked inside, poking out the top. */
function Folder({ sheets, glow = false, dim = false }: { sheets: number; glow?: boolean; dim?: boolean }) {
  return (
    <span
      className={`relative block aspect-[5/4] w-full transition-[opacity,filter] duration-300 ${dim ? 'opacity-40' : ''} ${
        glow ? 'drop-shadow-[0_0_6px_rgb(241_220_192/0.8)]' : ''
      }`}
    >
      <span className="absolute top-0 left-0 h-[22%] w-[42%] bg-brand-dark" />
      <span className="absolute inset-x-0 top-[14%] bottom-0 bg-brand-dark" />
      {Array.from({ length: Math.min(sheets, 6) }, (_, k) => (
        <span
          key={k}
          className="absolute inset-x-[12%] h-[50%] animate-[pop-in_0.2s_ease-out] border border-espresso/40 bg-ink"
          style={{ top: `${26 - k * 6}%` }}
        />
      ))}
      <span className={`absolute inset-x-0 top-[38%] bottom-0 ${glow ? 'bg-creamer' : 'bg-brand'} transition-colors duration-300`} />
    </span>
  )
}

// 3. Pull: full 3v3s with shields and switches, every lineup, 100 meta teams each

const ORDERS = lineups(DEMO_RUN.best.members.map((m) => m.speciesId))

function PullScreen({ ms, look }: { ms: number; look: Look }) {
  const f = pullFrame(ms)
  const order = ORDERS[f.lineup]
  const you = order[f.active]
  const foesDown = f.phaseMs >= 1700 ? 3 : f.phaseMs >= 1300 ? 2 : f.phaseMs >= 900 ? 1 : 0
  const foe = META[Math.min(2, foesDown)]
  const callout = f.shielding ? 'SHIELD!' : (f.phaseMs >= 900 && f.phaseMs < 1100) || (f.phaseMs >= 1300 && f.phaseMs < 1450) ? 'SWITCH!' : null
  const ids = DEMO_RUN.best.members.map((m) => m.speciesId)

  return (
    <div className="absolute inset-0">
      {/* top bar: which lineup, which meta team */}
      <div className="absolute inset-x-[4%] top-[4%] flex items-center justify-between text-[10px]">
        <span className="flex items-center gap-1.5 font-display text-subtle">
          LINEUP {f.lineup + 1}/6
          <span className="flex gap-[2px]">
            {ORDERS.map((_, i) => (
              <span key={i} className={`h-[6px] w-[6px] ${i <= f.lineup ? 'bg-brand' : 'bg-raised-strong'}`} />
            ))}
          </span>
        </span>
        <span className="font-mono text-subtle">
          meta team <span className="text-ink">{String(f.metaTeam).padStart(3, ' ')}</span>/100
        </span>
      </div>

      {/* the field */}
      <Platform x={28} y={56} />
      <Platform x={68} y={38} />
      <span key={`you-${f.lineup}-${you}`} className="absolute aspect-square animate-[slide-in-left_0.2s_ease-out]" style={at(28, 44, 24)}>
        <Facing>
          <PokemonArt dex={look.dexOf(you)} alt="" className="h-full w-full" />
        </Facing>
        {f.shielding && (
          <span className="absolute -inset-[12%] animate-[pop-in_0.2s_ease-out] border-[3px] border-creamer/90 bg-creamer/15" style={pixelCorners(4)} />
        )}
      </span>
      <span
        key={`foe-${f.lineup}-${foe}`}
        className="absolute aspect-square animate-[pop-in_0.2s_ease-out] transition-opacity duration-300"
        style={{ ...at(68, 26, 20), opacity: foesDown === 3 ? 0 : blink(f.hitting, f.phaseMs) ? 0.2 : 1 }}
      >
        <PokemonArt dex={look.dexOf(foe)} alt="" className="h-full w-full" />
      </span>
      {callout && (
        <span key={callout + f.lineup} className="absolute animate-[pop-in_0.2s_ease-out] font-display text-sm text-creamer" style={at(50, 22)}>
          {callout}
        </span>
      )}
      {foesDown === 3 && (
        <span className="absolute animate-[pop-in_0.2s_ease-out] bg-brand px-2 py-0.5 font-display text-sm text-on-brand" style={{ ...at(68, 26), ...pixelCorners(2) }}>
          KO
        </span>
      )}

      {/* shields left, and the foe's team going down */}
      <span className="absolute top-[56%] left-[4%] flex gap-1">
        {[0, 1].map((k) => (
          <PixelShield key={k} spent={k >= f.shieldsLeft} />
        ))}
      </span>
      <span className="absolute top-[14%] right-[4%] flex flex-col gap-[3px]">
        {META.map((id, k) => (
          <span key={id} className={`h-5 w-5 bg-surface transition-[opacity,filter] duration-300 ${k < foesDown ? 'opacity-30 grayscale' : ''}`}>
            <PokemonArt dex={look.dexOf(id)} alt="" className="h-full w-full" />
          </span>
        ))}
      </span>

      {/* your three, sliding into this lineup's order */}
      <div className="absolute inset-x-[4%] bottom-[4%] h-[27%]">
        {ROLES.map((role, slot) => (
          <span
            key={role}
            className={`absolute bottom-0 h-full w-[31%] bg-surface transition-colors duration-200 ${slot === f.active ? 'ring-2 ring-brand' : ''}`}
            style={{ left: `${slot * 34.5}%`, ...pixelCorners(3) }}
          >
            <span className={`absolute inset-x-0 bottom-[6%] text-center text-[8px] font-bold uppercase ${slot === f.active ? 'text-brand' : 'text-faint'}`}>
              {role}
            </span>
          </span>
        ))}
        {ids.map((id) => {
          const slot = order.indexOf(id)
          return (
            <span
              key={id}
              className={`absolute top-[4%] h-[64%] transition-[left,opacity] duration-200 ease-out ${slot < f.active ? 'opacity-40' : ''}`}
              style={{ left: `${slot * 34.5 + 15.5}%`, transform: 'translateX(-50%)', aspectRatio: '1' }}
            >
              <PokemonArt dex={look.dexOf(id)} alt="" className="h-full w-full" />
            </span>
          )
        })}
      </div>
    </div>
  )
}

function PixelShield({ spent }: { spent: boolean }) {
  const rows = ['XXXXXXX', 'XWWWWWX', 'XWWWWWX', 'XWWWWWX', '.XWWWX.', '..XWX..', '...X...']
  return (
    <svg viewBox="0 0 7 7" className={`h-4 w-4 transition-opacity duration-300 ${spent ? 'opacity-20' : ''}`} shapeRendering="crispEdges">
      {rows.flatMap((row, y) =>
        [...row].map((c, x) => (c === '.' ? null : <rect key={`${x}-${y}`} x={x} y={y} width="1" height="1" fill={c === 'X' ? '#392a1c' : '#f1dcc0'} />)),
      )}
    </svg>
  )
}

// 4. Taste: 400 held-out battles fill in, then the odds and their range

function TasteScreen({ ms }: { ms: number; look: Look }) {
  const f = tasteFrame(ms)
  const { low, high, rate } = DEMO_RUN.best
  const rateNow = f.revealed === 0 ? 0 : f.wins / f.revealed
  // the number line runs 40% to 70%
  const x = (v: number) => ((v - 0.4) / 0.3) * 100

  return (
    <div className={`absolute inset-0 transition-opacity duration-500 ${f.fading ? 'opacity-0' : 'opacity-100'}`}>
      <div className="absolute top-[6%] left-[4%] grid aspect-square h-[88%] grid-cols-20 gap-[1px]">
        {TASTE_GRID.map((won, i) => (
          <span
            key={i}
            className={
              i >= f.revealed ? 'bg-raised/50' : i === f.revealed - 1 && f.revealed < TASTE_BATTLES ? 'bg-creamer' : won ? 'bg-brand' : 'bg-raised-strong'
            }
          />
        ))}
      </div>

      <div className="absolute top-[6%] right-[4%] bottom-[6%] left-[73%] flex flex-col">
        <p className="font-display text-[10px] text-subtle">BATTLES</p>
        <p className="font-mono text-sm text-ink">
          {f.revealed}
          <span className="text-faint">/{TASTE_BATTLES}</span>
        </p>
        <p className="mt-2 font-display text-[10px] text-subtle">WINS</p>
        <p className="font-mono text-sm text-brand">{f.wins}</p>
        <p className="mt-2 font-display text-[10px] text-subtle">ODDS</p>
        <p className="font-display text-2xl text-ink">{Math.round(rateNow * 100)}%</p>

        <div className={`mt-auto transition-opacity duration-300 ${f.showRange ? 'opacity-100' : 'opacity-0'}`}>
          <p className="font-display text-[10px] text-subtle">LIKELY</p>
          <div className="relative mt-1 h-5">
            <span className="absolute inset-x-0 top-1/2 h-[3px] -translate-y-1/2 bg-raised-strong" />
            <span
              className="absolute top-0 h-full border-x-2 border-creamer bg-creamer/25 transition-[left,width] duration-500 ease-out"
              style={
                f.showRange
                  ? { left: `${x(low)}%`, width: `${x(high) - x(low)}%` }
                  : { left: `${x(rate)}%`, width: '0%' }
              }
            />
            <span className="absolute -top-0.5 -bottom-0.5 w-[3px] -translate-x-1/2 bg-brand" style={{ left: `${x(rate)}%` }} />
          </div>
          <p className="mt-1 font-mono text-xs text-creamer">
            {Math.round(low * 100)}–{Math.round(high * 100)}%
          </p>
        </div>
      </div>
    </div>
  )
}

/**
 * Milliseconds the slide in front has played: restarts when another slide comes to the front, runs only while the
 * carousel is on screen, and is null with reduced motion (every slide then shows its still frame).
 */
function useActiveClock(ref: RefObject<HTMLDivElement | null>, active: number): number | null {
  const [clock, setClock] = useState({ slide: active, ms: 0 })
  const reduced = prefersReducedMotion()
  useEffect(() => {
    if (reduced || !ref.current) return
    let timer = 0
    let last = 0
    const tick = () => {
      const now = performance.now()
      const dt = Math.min(100, now - last) // no leap after the tab was hidden
      last = now
      setClock((c) => (c.slide === active ? { slide: active, ms: c.ms + dt } : { slide: active, ms: dt }))
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
  }, [ref, active, reduced])
  if (reduced) return null
  return clock.slide === active ? clock.ms : 0
}

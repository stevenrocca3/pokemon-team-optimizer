import { useRef, type ReactNode } from 'react'
import { PhoneCase, PixelHand } from './DemoPhone'
import { PokemonArt } from './PokemonArt'
import { useSpecies } from '../species/species-context'
import { useDemoClock } from '../lib/demo-clock'
import { typed } from '../lib/demo-run'
import { DEMO_SCORE, MISTAKE_AT, analyzeDemoFrame, type AnalyzeDemoFrame } from '../lib/analyze-demo'
import { pixelCorners } from '../lib/pixel'

const YOU = { id: 'azumarill', name: 'Azumarill' }
const FOE = { id: 'medicham', name: 'Medicham' }

const TITLES: Record<AnalyzeDemoFrame['phase'], string> = {
  battle: 'Reading…',
  charts: 'Your Battle',
  mistake: 'Your Battle',
  score: 'Battle Score',
  fade: 'Battle Score',
}
const CAPTIONS: Record<AnalyzeDemoFrame['phase'], string> = {
  battle: 'Your recording plays back, turn by turn.',
  charts: 'Every turn charted: energy, shields, switches.',
  mistake: 'Then it pins the exact turn it went wrong.',
  score: 'And grades your whole battle out of 100.',
  fade: 'And grades your whole battle out of 100.',
}

// your energy across the battle, 12 turns (0 to 1); the dip is the shield going up
const ENERGY = [0.1, 0.22, 0.34, 0.45, 0.55, 0.62, 0.5, 0.58, 0.7, 0.8, 0.9, 1]
const SKILLS = [
  { label: 'Fast moves', value: 0.92 },
  { label: 'Charged', value: 0.81 },
  { label: 'Switches', value: 0.74 },
  { label: 'Shields', value: 0.38, bad: true },
]
const DEDUCTIONS = [
  { points: 14, label: 'Shield spent on a bait' },
  { points: 8, label: 'Charged move a turn late' },
]

const CHART_W = 236
const CHART_H = 120

/** The Analyze page's live preview: a battle read as a mini game, then charts, a mistake pinned in red, the score. */
export function AnalyzeDemoPhone() {
  const ref = useRef<HTMLDivElement>(null)
  // with reduced motion it rests on the finished score
  const frame = analyzeDemoFrame(useDemoClock(ref, 15000))
  const species = useSpecies()
  const dexOf = (id: string) => (species.status === 'ready' ? species.byId.get(id)?.dex : undefined)
  const caption = CAPTIONS[frame.phase]
  const shown = typed(caption, frame.phaseMs)
  const onCharts = frame.phase === 'charts' || frame.phase === 'mistake'

  return (
    <div
      ref={ref}
      className="relative mx-auto w-[300px] select-none"
      role="img"
      aria-label="A preview of the analysis: a battle plays back, becomes charts, a mistake is flagged in red, and the battle gets a score"
    >
      <PhoneCase>
        <p
          key={TITLES[frame.phase]}
          className="px-3 pt-1 font-display text-lg text-ink"
          style={{ animation: 'slide-in-left 300ms ease-out both' }}
        >
          {TITLES[frame.phase]}
        </p>

        <Screen shown={frame.phase === 'battle'}>
          <Battle frame={frame} dexOf={dexOf} />
        </Screen>
        <Screen shown={onCharts}>
          <Charts frame={frame} />
        </Screen>
        <Screen shown={frame.phase === 'score'}>
          <Score frame={frame} />
        </Screen>

        {/* the red alarm flash before the mistake pops up */}
        <div
          className={`pointer-events-none absolute inset-0 bg-tier-critical transition-opacity ${
            frame.phase === 'mistake' && frame.flash ? 'opacity-50 duration-75' : 'opacity-0 duration-500'
          }`}
        />
        {frame.phase === 'mistake' && frame.popped && <MistakePopup shake={frame.shake} />}

        <div className="absolute inset-x-2 bottom-2 min-h-[64px] border-2 border-creamer/80 bg-[#1d140e] px-2.5 py-2 text-[12px] leading-snug text-ink">
          {shown}
          {shown.length < caption.length ? (
            <span className="ml-px inline-block h-3 w-1.5 translate-y-0.5 animate-pulse bg-creamer" />
          ) : (
            <span className="absolute right-2 bottom-1 animate-bounce text-[10px] text-creamer">▼</span>
          )}
        </div>
      </PhoneCase>
    </div>
  )
}

/** Each screen fades and rises in, and fades out as the next arrives: the smooth hand-offs between steps. */
function Screen({ shown, children }: { shown: boolean; children: ReactNode }) {
  return (
    <div
      className={`absolute inset-x-0 top-[62px] bottom-[76px] transition-[opacity,transform,filter] duration-500 ease-out ${
        shown ? 'translate-y-0 opacity-100 blur-0' : 'pointer-events-none translate-y-3 opacity-0 blur-[2px]'
      }`}
    >
      {children}
    </div>
  )
}

/** GO's battle header for one side: name, a thick HP bar, shields left in pink, and the team's three Poké Balls. */
function SideStatus({
  name,
  hp,
  shields,
  align,
}: {
  name: string
  hp: number
  shields: number
  align: 'left' | 'right'
}) {
  const colour = hp > 0.5 ? 'bg-[#5ee08a]' : hp > 0.25 ? 'bg-[#f2c14e]' : 'bg-[#ff5a5a]'
  const right = align === 'right'
  return (
    <div className={`w-[112px] ${right ? 'text-right' : ''}`}>
      <p className="text-[11px] leading-none font-bold text-white [text-shadow:0_1px_0_#000]">{name}</p>
      <div className="mt-1 h-[7px] bg-black/60 p-px ring-1 ring-white/70">
        <div
          className={`h-full transition-[width] duration-200 ${colour} ${right ? 'ml-auto' : ''}`}
          style={{ width: `${hp * 100}%` }}
        />
      </div>
      <div className={`mt-1 flex items-center gap-1 ${right ? 'flex-row-reverse' : ''}`}>
        {[0, 1].map((i) => (
          <ShieldIcon key={i} used={i >= shields} />
        ))}
        <span className="mx-0.5" />
        {[0, 1, 2].map((i) => (
          <span key={i} className="h-[7px] w-[7px] rounded-full bg-[#ff5a5a] ring-1 ring-white/80" />
        ))}
      </div>
    </div>
  )
}

/** A pink Protect Shield, greyed out once used. */
function ShieldIcon({ used, className = 'h-3 w-2.5' }: { used: boolean; className?: string }) {
  const rows = ['XXXXX', 'XWWWX', 'XWWWX', '.XWX.', '..X..']
  return (
    <svg
      viewBox="0 0 5 5"
      className={`${className} transition-opacity duration-300 ${used ? 'opacity-25 grayscale' : ''}`}
      shapeRendering="crispEdges"
      aria-hidden="true"
    >
      {rows.flatMap((row, y) =>
        [...row].map((c, x) =>
          c === '.' ? null : (
            <rect key={`${x}-${y}`} x={x} y={y} width="1" height="1" fill={c === 'X' ? '#e8559a' : '#ffc4df'} />
          ),
        ),
      )}
    </svg>
  )
}

// where each fast-move tap lands on the screen (px), one per hit, so the taps wander like a real thumb
const TAPS = [
  { left: 150, top: 200 },
  { left: 120, top: 170 },
  { left: 170, top: 225 },
  { left: 135, top: 190 },
  { left: 185, top: 175 },
]

/**
 * The battle as Pokémon GO shows it: both HP bars across the top with shields and team, the opponent further back,
 * yours up close, taps for fast moves, round charged-move buttons filling with energy, and the Protect Shield prompt.
 * A scanner reads it.
 */
function Battle({ frame, dexOf }: { frame: AnalyzeDemoFrame; dexOf: (id: string) => number | undefined }) {
  const f =
    frame.phase === 'battle'
      ? frame
      : {
          phaseMs: 5000,
          youHp: 0.58,
          foeHp: 0.45,
          energy: 1,
          strike: false,
          foeThrow: false,
          shielding: false,
          shieldsLeft: 1,
        }
  const turn = Math.min(24, Math.floor(f.phaseMs / 210) + 1)
  const scan = (f.phaseMs % 1600) / 1600
  const prompt = f.foeThrow && !f.shielding
  // the thumb comes down on the shield a moment before it goes up
  const pressing = prompt && f.phaseMs >= 2850
  const tap = TAPS[Math.floor(f.phaseMs / 500) % TAPS.length]
  return (
    <div className="relative h-full overflow-hidden bg-gradient-to-b from-[#3b6f8f] via-[#5f9a6e] to-[#3f7a4a]">
      {/* the arena floor */}
      <div className="absolute inset-x-0 top-[44%] bottom-0 bg-[repeating-linear-gradient(to_bottom,#4c8a57_0_6px,#457f50_6px_12px)]" />

      {/* the scanner's readout */}
      <p className="relative flex justify-between bg-black/45 px-3 py-0.5 font-mono text-[10px] text-brand">
        <span>● REC 0:{String(Math.floor(f.phaseMs / 120) % 60).padStart(2, '0')}</span>
        <span>TURN {turn}</span>
      </p>

      {/* GO's header: you on the left, them on the right */}
      <div className="relative flex justify-between px-2.5 pt-1.5">
        <SideStatus name={YOU.name} hp={f.youHp} shields={f.shieldsLeft} align="left" />
        <SideStatus name={FOE.name} hp={f.foeHp} shields={2} align="right" />
      </div>

      {/* their charged move's name, in its type's colour, as GO announces it */}
      <div
        className={`absolute inset-x-0 top-[78px] z-10 text-center transition-[opacity,translate] duration-200 ${
          f.foeThrow ? 'translate-y-0 opacity-100' : 'pointer-events-none -translate-y-2 opacity-0'
        }`}
      >
        <span className="inline-block rounded-full bg-[#74cfe6] px-3 py-0.5 text-[11px] font-black text-[#0d3340] ring-2 ring-white/80">
          Ice Punch
        </span>
      </div>

      {/* the opponent, further back on the right */}
      <div className="absolute top-[100px] right-6 h-[72px] w-[72px]">
        <span className="absolute inset-x-1 bottom-0 h-3 rounded-[50%] bg-black/30" />
        <PokemonArt
          dex={dexOf(FOE.id)}
          alt=""
          className={`relative h-full w-full transition-[filter,translate] duration-75 ${f.strike ? 'translate-x-1 brightness-[2.2]' : ''}`}
        />
      </div>

      {/* yours, up close on the left */}
      <div className="absolute bottom-[62px] left-3 h-[112px] w-[112px]">
        <span className="absolute inset-x-2 bottom-0 h-4 rounded-[50%] bg-black/30" />
        {/* mirrored on a wrapper: the sprite's own transform fits it to the box */}
        <span className="relative block h-full w-full -scale-x-100">
          <PokemonArt dex={dexOf(YOU.id)} alt="" className="h-full w-full" />
        </span>
        {/* the Protect Shield's pink bubble */}
        <span
          className={`absolute -inset-3 rounded-full border-[3px] border-[#ff9fcb] bg-[#ff9fcb]/25 shadow-[0_0_16px_2px] shadow-[#ff9fcb]/60 transition-[opacity,scale] duration-200 ${
            f.shielding ? 'scale-100 opacity-100' : 'scale-50 opacity-0'
          }`}
        />
      </div>

      {/* fast moves are taps on the screen: a ripple where each lands */}
      {f.strike && (
        <span
          className="absolute h-8 w-8 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-white/90"
          style={{ ...tap, animation: 'pop-in 120ms ease-out both' }}
        />
      )}

      {/* the bottom bar: switch on the left, the two charged moves filling with energy */}
      <div className="absolute inset-x-0 bottom-2 flex items-end justify-between px-3">
        <span
          className="grid h-9 w-9 place-items-center rounded-full bg-black/45 ring-2 ring-white/70"
          aria-hidden="true"
        >
          <span className="h-4 w-4 rounded-full bg-[linear-gradient(to_bottom,#ff5a5a_0_45%,#1d140e_45%_55%,#fff_55%)] ring-1 ring-black" />
        </span>
        <div className="flex items-end gap-2">
          <ChargedButton label="Play Rough" fill={Math.min(1, f.energy / 0.85) * 0.6} colour="#f49ac1" />
          <ChargedButton label="Hydro Pump" fill={f.energy} colour="#5aa9e6" big />
        </div>
      </div>

      {/* GO's Protect Shield prompt, and the thumb that taps it */}
      <div
        className={`absolute inset-0 z-20 grid place-content-center justify-items-center gap-2 bg-black/45 transition-opacity duration-150 ${
          prompt ? 'opacity-100' : 'pointer-events-none opacity-0'
        }`}
      >
        <p className="text-[11px] font-black tracking-wide text-white [text-shadow:0_1px_0_#000]">
          USE PROTECT SHIELD?
        </p>
        <span
          className={`relative grid h-16 w-16 place-items-center rounded-full bg-[#e8559a] ring-4 ring-white transition-transform duration-100 ${
            pressing ? 'scale-90' : 'scale-100'
          }`}
        >
          <ShieldIcon used={false} className="h-8 w-7" />
          <PixelHand
            className={`absolute top-8 left-8 transition-opacity duration-150 ${prompt ? 'opacity-100' : 'opacity-0'}`}
            style={{}}
          />
        </span>
        <span className="h-1 w-24 bg-white/30">
          <span
            className="block h-full bg-white"
            style={{ width: `${prompt ? Math.max(0, 1 - (f.phaseMs - 2400) / 600) * 100 : 0}%` }}
          />
        </span>
      </div>

      {/* the scanner's beam sweeping the recording */}
      <span
        className="pointer-events-none absolute inset-x-0 z-30 h-[2px] bg-brand shadow-[0_0_10px_3px] shadow-brand/60"
        style={{ top: `${8 + scan * 84}%` }}
      />
    </div>
  )
}

/** A round charged-move button that fills from the bottom with energy and glows once it's ready. */
function ChargedButton({
  label,
  fill,
  colour,
  big = false,
}: {
  label: string
  fill: number
  colour: string
  big?: boolean
}) {
  const ready = fill >= 1
  return (
    <span className={`grid justify-items-center gap-0.5 ${big ? '' : 'mb-1'}`}>
      <span
        className={`relative overflow-hidden rounded-full bg-black/50 ring-2 ${big ? 'h-12 w-12' : 'h-9 w-9'} ${
          ready ? 'animate-pulse ring-white shadow-[0_0_12px_3px] shadow-white/60' : 'ring-white/60'
        }`}
      >
        <span
          className="absolute inset-x-0 bottom-0 transition-[height] duration-200"
          style={{ height: `${fill * 100}%`, background: colour }}
        />
      </span>
      <span className="text-[8px] font-bold text-white [text-shadow:0_1px_0_#000]">{label}</span>
    </span>
  )
}

/** Turn by turn: your energy as a stepped line, then a bar per skill. In the mistake beat, the bad turn is pinned. */
function Charts({ frame }: { frame: AnalyzeDemoFrame }) {
  const line = frame.phase === 'charts' ? frame.line : frame.phase === 'mistake' ? 1 : 0
  const bars = frame.phase === 'charts' ? frame.bars : frame.phase === 'mistake' ? 1 : 0
  const alarm = frame.phase === 'mistake'
  const step = CHART_W / ENERGY.length
  // a stepped path, like a pixel chart
  const d = ENERGY.map((v, i) => {
    const y = CHART_H - v * (CHART_H - 8)
    return `${i === 0 ? 'M' : 'H'}${i * step} ${i === 0 ? y : ''}${i === 0 ? '' : ` V${y}`}`
  }).join(' ')
  const pinX = MISTAKE_AT * CHART_W
  return (
    <div className="px-3">
      <div className="bg-surface p-2" style={pixelCorners(3)}>
        <p className="flex justify-between text-[9px] font-bold tracking-wide text-subtle uppercase">
          <span>Energy by turn</span>
          <span className={alarm ? 'text-tier-critical' : ''}>Turn 14</span>
        </p>
        <svg
          viewBox={`-2 -2 ${CHART_W + 4} ${CHART_H + 4}`}
          className="mt-1 block w-full overflow-visible"
          aria-hidden="true"
        >
          {[0.25, 0.5, 0.75].map((g) => (
            <line
              key={g}
              x1="0"
              x2={CHART_W}
              y1={CHART_H * g}
              y2={CHART_H * g}
              className="stroke-line"
              strokeDasharray="2 3"
            />
          ))}
          <path
            d={`${d} H${CHART_W}`}
            pathLength={1}
            fill="none"
            strokeWidth="3"
            className="stroke-brand"
            strokeDasharray="1"
            strokeDashoffset={1 - line}
          />
          {/* where the shield went up */}
          <line
            x1={pinX}
            x2={pinX}
            y1="0"
            y2={CHART_H}
            strokeWidth="2"
            strokeDasharray="3 3"
            className={`transition-[opacity,stroke] duration-300 ${alarm ? 'stroke-tier-critical opacity-100' : 'stroke-subtle opacity-0'}`}
          />
          {alarm && (
            <g transform={`translate(${pinX} ${CHART_H - ENERGY[7] * (CHART_H - 8)})`}>
              <circle r="9" fill="none" strokeWidth="2" className="stroke-tier-critical">
                <animate attributeName="r" values="6;13;6" dur="0.9s" repeatCount="indefinite" />
                <animate attributeName="opacity" values="1;0.3;1" dur="0.9s" repeatCount="indefinite" />
              </circle>
              <rect x="-4" y="-4" width="8" height="8" className="fill-tier-critical" />
            </g>
          )}
        </svg>
      </div>

      <ul className="mt-3 grid gap-2.5">
        {SKILLS.map((s, i) => {
          // each bar starts a beat after the one above
          const grown = Math.min(1, Math.max(0, bars * 1.3 - i * 0.1)) * s.value
          return (
            <li key={s.label} className="grid grid-cols-[76px_1fr_28px] items-center gap-2 text-[11px]">
              <span className={`font-semibold ${alarm && s.bad ? 'text-tier-critical' : 'text-ink-soft'}`}>
                {s.label}
              </span>
              <span className="h-3 bg-raised">
                <span
                  className={`block h-full ${s.bad ? 'bg-tier-critical' : 'creamer-fill'}`}
                  style={{ width: `${grown * 100}%` }}
                />
              </span>
              <span className="text-right font-mono text-ink">{Math.round(grown * 100)}</span>
            </li>
          )
        })}
      </ul>
    </div>
  )
}

/** The drama: a big red error box over the charts, with a pointer up at the turn it's about. */
function MistakePopup({ shake }: { shake: boolean }) {
  return (
    <div
      className="pointer-events-none absolute inset-x-3 top-[176px] z-20"
      style={{
        animation: 'pop-in 260ms cubic-bezier(0.2, 0.9, 0.3, 1.4) both',
      }}
    >
      {/* the pointer, up at the pinned turn */}
      <span
        className="absolute -top-[9px] h-0 w-0 border-x-[9px] border-b-[10px] border-x-transparent border-b-[#ff4d4d]"
        style={{ left: `calc(${MISTAKE_AT * 100}% - 9px)` }}
      />
      <div
        className={`bg-[#ff4d4d] p-[3px] shadow-[0_0_24px_4px] shadow-[#ff4d4d]/50 ${shake ? 'demo-shake' : ''}`}
        style={pixelCorners(4)}
      >
        <div className="bg-[#3a0d0d] px-3 py-2.5" style={pixelCorners(3)}>
          <p className="flex items-center gap-2 font-display text-xl leading-none text-[#ff6b6b]">
            <span
              className="grid h-6 w-6 place-items-center bg-[#ff4d4d] font-mono text-base font-black text-[#3a0d0d]"
              style={pixelCorners(2)}
            >
              !
            </span>
            MISTAKE
            <span className="ml-auto font-mono text-[10px] text-[#ffb3b3]">TURN 14</span>
          </p>
          <p className="mt-2 text-[12px] leading-snug font-semibold text-[#ffe0e0]">
            You shielded Ice Punch. It was a bait.
          </p>
          <p className="mt-1 text-[11px] leading-snug text-[#ffb3b3]">
            Dynamic Punch came next, with no shield left for your closer.
          </p>
        </div>
      </div>
    </div>
  )
}

/** The verdict: the score counting up in its ring, the tier, and where the points went. */
function Score({ frame }: { frame: AnalyzeDemoFrame }) {
  const score = frame.phase === 'score' ? frame.score : frame.phase === 'fade' ? DEMO_SCORE : 0
  const tier = frame.phase === 'score' ? frame.tier : frame.phase === 'fade'
  const R = 46
  const C = 2 * Math.PI * R
  return (
    <div className="px-3 pt-2 text-center">
      <div className="relative mx-auto h-[128px] w-[128px]">
        <svg viewBox="0 0 120 120" className="h-full w-full -rotate-90" aria-hidden="true">
          <circle cx="60" cy="60" r={R} fill="none" strokeWidth="10" className="stroke-raised" />
          <circle
            cx="60"
            cy="60"
            r={R}
            fill="none"
            strokeWidth="10"
            className="stroke-tier-veteran"
            strokeDasharray={C}
            strokeDashoffset={C * (1 - score / 100)}
          />
        </svg>
        <span className="absolute inset-0 grid place-content-center">
          <span className="font-display text-4xl leading-none text-ink">{score}</span>
          <span className="font-mono text-[10px] text-subtle">/100</span>
        </span>
      </div>
      <p
        className={`mt-2 inline-block bg-tier-veteran/15 px-3 py-1 font-display text-sm text-tier-veteran ring-1 ring-tier-veteran/60 transition-[opacity,scale] duration-300 ${
          tier ? 'scale-100 opacity-100' : 'scale-75 opacity-0'
        }`}
        style={pixelCorners(2)}
      >
        VETERAN
      </p>
      <ul className="mt-3 grid gap-1 text-left text-[11px]">
        {DEDUCTIONS.map((x, i) => (
          <li
            key={x.label}
            className={`flex items-center gap-2 bg-surface px-2 py-1.5 transition-[opacity,translate] duration-300 ${
              tier ? 'translate-x-0 opacity-100' : 'translate-x-4 opacity-0'
            }`}
            style={{
              ...pixelCorners(2),
              transitionDelay: tier ? `${150 + i * 120}ms` : '0ms',
            }}
          >
            <span className="font-mono font-bold text-tier-critical">−{x.points}</span>
            <span className="text-ink-soft">{x.label}</span>
          </li>
        ))}
      </ul>
    </div>
  )
}

import { useRef, type ReactNode } from 'react'
import { PhoneCase, PixelHand } from './DemoPhone'
import { PokemonArt } from './PokemonArt'
import { useSpecies } from '../species/species-context'
import { useDemoClock } from '../lib/demo-clock'
import { typed } from '../lib/demo-run'
import { IMPORT_DEMO_MS, importDemoFrame, type ImportDemoFrame } from '../lib/import-demo'
import { pixelCorners } from '../lib/pixel'

// three real readings from appraisal screenshots
const MONS = [
  { id: 'guzzlord', name: 'Guzzlord', ivs: [10, 15, 10], level: 20, cp: 1600 },
  { id: 'zigzagoon', name: 'Zigzagoon', ivs: [13, 13, 12], level: 20, cp: 276 },
  { id: 'sizzlipede', name: 'Sizzlipede', ivs: [0, 11, 11], level: 16, cp: 411 },
]
// which of the nine photos get ticked, in order, and where the hand's fingertip lands (px inside the picker; the
// screen is 260 px wide, so each photo is about 75 px plus a 6 px gap)
const PICKS = [0, 4, 7]
const CELL_W = 75
const CELL_H = 92
const cellCenter = (i: number) => ({ left: 12 + (i % 3) * (CELL_W + 6) + CELL_W / 2 - 8, top: 4 + Math.floor(i / 3) * (CELL_H + 6) + CELL_H / 2 })
const ADD_BUTTON = { left: 122, top: 332 }
const PHOTO_MONS = ['guzzlord', 'pikachu', 'machamp', 'eevee', 'zigzagoon', 'snorlax', 'gengar', 'sizzlipede', 'lapras']

const TITLES = { appraise: 'Appraise', pick: 'Your Photos', read: 'Reading…', saved: 'Collection' }
const CAPTIONS = {
  appraise: 'In Pokémon GO, appraise a Pokémon and screenshot the bars.',
  pick: 'Choose your screenshots. As many as you like.',
  read: 'Read right on your phone. Nothing is uploaded.',
  saved: 'Pick moves and save. Ready for the Team Builder!',
}

/** The Import page's live preview: screenshotting an appraisal, picking screenshots, reading them, saving. */
export function ImportDemoPhone() {
  const ref = useRef<HTMLDivElement>(null)
  const frame = importDemoFrame(useDemoClock(ref, IMPORT_DEMO_MS - 1))
  const species = useSpecies()
  const dexOf = (id: string) => (species.status === 'ready' ? species.byId.get(id)?.dex : undefined)
  const caption = CAPTIONS[frame.phase]
  const shown = typed(caption, frame.phaseMs)

  return (
    <div ref={ref} className="relative mx-auto w-[300px] select-none" role="img" aria-label="A phone importing Pokémon from appraisal screenshots">
      <PhoneCase>
        <p key={frame.phase} className="px-3 pt-1 font-display text-lg text-ink" style={{ animation: 'slide-in-left 300ms ease-out both' }}>
          {TITLES[frame.phase]}
        </p>

        <Screen shown={frame.phase === 'appraise'}>
          <Appraise frame={frame} dex={dexOf('guzzlord')} />
        </Screen>
        <Screen shown={frame.phase === 'pick'}>
          <Pick frame={frame} dexOf={dexOf} />
        </Screen>
        <Screen shown={frame.phase === 'read'}>
          <Read frame={frame} dexOf={dexOf} />
        </Screen>
        <Screen shown={frame.phase === 'saved'}>
          <Saved frame={frame} dexOf={dexOf} />
        </Screen>

        {/* the camera flash as the screenshot is taken */}
        <div
          className={`pointer-events-none absolute inset-0 bg-white transition-opacity ${frame.phase === 'appraise' && frame.flash ? 'opacity-80 duration-75' : 'opacity-0 duration-300'}`}
        />

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

function Screen({ shown, children }: { shown: boolean; children: ReactNode }) {
  return (
    <div
      className={`absolute inset-x-0 top-[62px] bottom-[76px] transition-[opacity,transform] duration-300 ease-out ${
        shown ? 'translate-y-0 opacity-100' : 'pointer-events-none translate-y-2 opacity-0'
      }`}
    >
      {children}
    </div>
  )
}

/** A schematic appraisal: the Pokémon, its name, and three bars filling up. */
function Appraise({ frame, dex }: { frame: ImportDemoFrame; dex: number | undefined }) {
  const fill = frame.phase === 'appraise' ? frame.bars : 1
  const mon = MONS[0]
  return (
    <div className="px-3">
      <p className="text-center font-display text-sm text-ink">
        CP <span className="text-lg">{mon.cp}</span>
      </p>
      <PokemonArt dex={dex} alt="" className="mx-auto h-24 w-24" />
      <div className="mt-1 bg-[#f6efe6] px-3 py-2.5 text-[#2a1d15]" style={pixelCorners(4)}>
        <p className="text-center text-sm font-bold">{mon.name}</p>
        <div className="mx-auto mt-1 h-1.5 w-3/4 bg-[#7fd8a8]" />
        <p className="mt-0.5 text-center text-[10px] font-semibold opacity-70">268 / 268 HP</p>
        <div className="mt-2 grid gap-1.5">
          {['Attack', 'Defense', 'HP'].map((label, i) => {
            const iv = mon.ivs[i]
            return (
              <div key={label}>
                <p className="text-[9px] font-bold uppercase opacity-70">{label}</p>
                <div className="mt-0.5 grid h-2 grid-cols-3 gap-[2px]">
                  {[0, 1, 2].map((seg) => {
                    // each third of the bar is five IV points
                    const part = Math.min(1, Math.max(0, (iv * fill - seg * 5) / 5))
                    return (
                      <span key={seg} className="bg-[#d9d4cc]">
                        <span className={`block h-full ${iv === 15 ? 'bg-[#e07070]' : 'bg-brand'}`} style={{ width: `${part * 100}%` }} />
                      </span>
                    )
                  })}
                </div>
              </div>
            )
          })}
        </div>
      </div>
    </div>
  )
}

/** A photo picker: nine screenshots, three get ticked, then Add. */
function Pick({ frame, dexOf }: { frame: ImportDemoFrame; dexOf: (id: string) => number | undefined }) {
  const picked = frame.phase === 'pick' ? frame.picked : 0
  const pressing = frame.phase === 'pick' && frame.tapAdd
  // the hand rests on the photo it last ticked, then moves to the button
  const target = pressing || picked === 3 ? ADD_BUTTON : cellCenter(PICKS[Math.min(picked, 2)])
  return (
    <div className="relative h-full px-3">
      <ul className="mt-1 grid grid-cols-3 gap-[6px]">
        {PHOTO_MONS.map((id, i) => {
          const ticked = PICKS.indexOf(i) !== -1 && PICKS.indexOf(i) < picked
          return (
            <li
              key={id}
              className={`relative h-[92px] bg-[#f6efe6] p-1 transition-transform duration-150 ${ticked ? 'scale-90' : ''}`}
              style={pixelCorners(2)}
            >
              <PokemonArt dex={dexOf(id)} alt="" className="mx-auto h-9 w-9" />
              <div className="mt-1 grid gap-[3px] px-1">
                {[0, 1, 2].map((b) => (
                  <span key={b} className="h-[3px] bg-[#d9d4cc]">
                    <span className="block h-full bg-brand" style={{ width: `${40 + ((i * 7 + b * 13) % 60)}%` }} />
                  </span>
                ))}
              </div>
              {ticked && (
                <span
                  className="absolute top-1 right-1 grid h-4 w-4 place-items-center bg-brand text-[10px] font-bold text-on-brand"
                  style={{ animation: 'pop-in 200ms ease-out both' }}
                >
                  {PICKS.indexOf(i) + 1}
                </span>
              )}
            </li>
          )
        })}
      </ul>
      <div
        className={`absolute right-3 bottom-1 left-3 bg-brand py-2 text-center text-sm font-bold text-on-brand transition-transform duration-100 ${pressing ? 'translate-y-[3px]' : ''}`}
        style={{
          ...pixelCorners(3),
          boxShadow: pressing ? 'inset 0 3px 0 rgb(241 220 192 / 0.7)' : 'inset 0 3px 0 rgb(241 220 192 / 0.7), 0 4px 0 #392a1c',
        }}
      >
        Add {picked > 0 ? picked : ''} screenshot{picked === 1 ? '' : 's'}
      </div>
      <PixelHand
        className={`absolute transition-[left,top,opacity] duration-300 ease-out ${frame.phase === 'pick' ? 'opacity-100' : 'opacity-0'}`}
        style={target}
      />
    </div>
  )
}

/** One screenshot being scanned, then the readings listing in. */
function Read({ frame, dexOf }: { frame: ImportDemoFrame; dexOf: (id: string) => number | undefined }) {
  const scan = frame.phase === 'read' ? frame.scan : frame.phase === 'saved' ? 1 : 0
  const rows = frame.phase === 'read' ? frame.rows : 0
  return (
    <div className="px-3">
      <div className="flex items-center gap-3">
        <div className="relative h-[92px] w-[52px] shrink-0 overflow-hidden bg-[#f6efe6]" style={pixelCorners(2)}>
          <PokemonArt dex={dexOf('guzzlord')} alt="" className="mx-auto mt-2 h-7 w-7" />
          <div className="mt-2 grid gap-[3px] px-1.5">
            {[0.66, 1, 0.66].map((w, b) => (
              <span key={b} className="h-[3px] bg-[#d9d4cc]">
                <span className="block h-full bg-brand" style={{ width: `${w * 100}%` }} />
              </span>
            ))}
          </div>
          {scan < 1 && (
            <span className="absolute inset-x-0 h-[3px] bg-brand shadow-[0_0_8px_2px] shadow-brand/70" style={{ top: `${scan * 100}%` }} />
          )}
        </div>
        <div className="text-[11px] text-subtle">
          <p className="font-display text-sm text-brand">{scan < 1 ? 'Reading bars…' : 'Checked ✓'}</p>
          <p>IVs from the bars</p>
          <p>CP and HP checked</p>
        </div>
      </div>
      <ul className="mt-3 grid gap-1.5">
        {MONS.map((m, i) => (
          <li
            key={m.id}
            className={`flex items-center gap-2 bg-surface p-1.5 transition-[transform,opacity] duration-300 ease-out ${
              i < rows ? 'translate-x-0 opacity-100' : 'translate-x-6 opacity-0'
            }`}
            style={pixelCorners(3)}
          >
            <PokemonArt dex={dexOf(m.id)} alt="" className="h-8 w-8 shrink-0" />
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-1.5">
                <span className="truncate text-xs font-semibold">{m.name}</span>
                <span className="ml-auto bg-brand/20 px-1 text-[9px] font-bold text-brand uppercase">Ready</span>
              </div>
              <div className="mt-1 flex items-center gap-1" aria-hidden="true">
                {m.ivs.map((iv, k) => (
                  <span key={k} className="h-1 w-7 bg-raised">
                    <span
                      className="creamer-fill block h-1 transition-[width] duration-500 ease-out"
                      style={{ width: i < rows ? `${(iv / 15) * 100}%` : '0%', transitionDelay: `${k * 80}ms` }}
                    />
                  </span>
                ))}
                <span className="ml-auto text-[9px] text-subtle">Lv {m.level}</span>
              </div>
            </div>
          </li>
        ))}
      </ul>
    </div>
  )
}

/** The three Pokémon popping into the Collection. */
function Saved({ frame, dexOf }: { frame: ImportDemoFrame; dexOf: (id: string) => number | undefined }) {
  const shown = frame.phase === 'saved' ? frame.shown : 0
  return (
    <div className="px-3 pt-4 text-center">
      <ul className="flex justify-center gap-2">
        {MONS.map((m, i) => (
          <li key={m.id} className="h-[76px] w-[76px] bg-surface p-1.5" style={pixelCorners(3)}>
            {i < shown && (
              <span className="block h-full w-full" style={{ animation: 'pop-in 300ms cubic-bezier(0.2, 0.9, 0.3, 1.4) both' }}>
                <PokemonArt dex={dexOf(m.id)} alt="" className="h-full w-full" />
              </span>
            )}
          </li>
        ))}
      </ul>
      <p className={`mt-5 font-display text-2xl text-brand transition-opacity duration-300 ${shown === 3 ? 'opacity-100' : 'opacity-0'}`}>Saved ✓</p>
      <p className={`mt-1 text-xs text-subtle transition-opacity duration-300 ${shown === 3 ? 'opacity-100' : 'opacity-0'}`}>3 Pokémon added to your Collection</p>
    </div>
  )
}

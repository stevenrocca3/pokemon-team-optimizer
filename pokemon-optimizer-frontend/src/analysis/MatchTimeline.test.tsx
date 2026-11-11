// @vitest-environment jsdom
// Issue #106: the interactive match timeline, with a red flag on every mistake.
import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MatchTimeline } from './MatchTimeline'
import { entry, sampleResult } from './match-fixture'

afterEach(cleanup)

const flags = () => screen.getAllByRole('button', { name: /^Turn \d+:/ })

describe('MatchTimeline', () => {
  // Four tracks, yours against the opponent's: HP and energy each, on a clock from 00:00 to the end (00:20).
  it('draws HP and energy for both sides over the match clock', () => {
    render(<MatchTimeline result={sampleResult()} />)
    for (const name of ['Your HP', 'Opponent HP', 'Your energy', 'Opponent energy']) {
      expect(screen.getByRole('img', { name })).toBeTruthy()
    }
    const axis = screen.getByTestId('timeline-axis')
    expect(axis.firstElementChild?.textContent).toBe('00:00')
    expect(axis.lastElementChild?.textContent).toBe('00:20')
  })

  // The opponent's energy isn't on screen in GO Battle League, so its track says so instead of drawing nothing.
  it('says the opponent energy is not visible', () => {
    render(<MatchTimeline result={sampleResult()} />)
    expect(
      within(screen.getByRole('img', { name: 'Opponent energy' }).parentElement!).getByText('Not shown on screen'),
    ).toBeTruthy()
  })

  // Each Pokémon is named where it comes in: Swampert for you; Togekiss, then Charizard at 00:10, for the opponent.
  it('names each Pokémon where it comes in', () => {
    render(<MatchTimeline result={sampleResult()} />)
    const charizard = screen.getByText('Charizard')
    expect(screen.getByText('Swampert')).toBeTruthy()
    expect(screen.getByText('Togekiss')).toBeTruthy()
    expect(charizard.style.left).toBe('52.5%') // turn 21 = 10.5 s of 20 s
  })

  // One red flag per mistake, at its exact moment: turn 16 (8 s) is 40% along, turn 30 (15 s) is 75%.
  it('flags each mistake at its moment', () => {
    render(<MatchTimeline result={sampleResult()} />)
    expect(flags().map((f) => f.parentElement!.style.left)).toEqual(['40%', '75%'])
    expect(flags()[0].getAttribute('aria-label')).toBe(
      'Turn 16: Threw Charge Move on 2-turn alignment (+1 free Mud Shot given to opponent)',
    )
  })

  // Hovering a flag explains it (the finding's sentence, its category and what it cost); moving away hides it.
  it('explains a flag on hover', async () => {
    const user = userEvent.setup()
    render(<MatchTimeline result={sampleResult()} />)
    await user.hover(flags()[1])
    const tip = screen.getByRole('tooltip')
    expect(tip.textContent).toContain('wasted a shield on Body Slam')
    expect(tip.textContent).toContain('Shield')
    expect(tip.textContent).toContain('−15')
    await user.unhover(flags()[1])
    expect(screen.queryByRole('tooltip')).toBeNull()
  })

  // On touch screens a tap pins the explanation open (it stays when the pointer leaves); Escape closes it. Keyboard
  // focus shows it too.
  it('pins a flag on click and shows it on focus', async () => {
    const user = userEvent.setup()
    render(<MatchTimeline result={sampleResult()} />)
    await user.click(flags()[0])
    await user.unhover(flags()[0])
    expect(screen.getByRole('tooltip').textContent).toContain('+1 free Mud Shot')
    expect(flags()[0].getAttribute('aria-expanded')).toBe('true')
    await user.keyboard('{Escape}')
    expect(screen.queryByRole('tooltip')).toBeNull()
    await user.tab() // focus moves on from the first flag to the second, which explains itself
    expect(screen.getByRole('tooltip').textContent).toContain('wasted a shield on Body Slam')
  })

  // Pokémon that come in moments apart (a quick faint) would print their names over each other, so the later name
  // drops to a second row; names far apart share the first.
  it('staggers names that come in close together', () => {
    const result = sampleResult()
    result.turn_log.turn_log = result.turn_log.turn_log.map((e) => (e.turn >= 22 ? { ...e, opp_active: 'Weezing' } : e))
    render(<MatchTimeline result={result} />)
    expect(screen.getByText('Togekiss').dataset.row).toBe('0')
    expect(screen.getByText('Charizard').dataset.row).toBe('0')
    expect(screen.getByText('Weezing').dataset.row).toBe('1')
  })

  // The clock starts when the battle does: 3 s of lobby at the start of the recording isn't on the axis, and a
  // flag's explanation tells the time on that same clock (turn 16 is 8 s into the video, 5 s into the battle).
  it('starts the clock when the battle starts', async () => {
    const result = sampleResult()
    result.turn_log.turn_log = result.turn_log.turn_log.map((e) =>
      e.turn < 6
        ? entry(e.turn, {
            hud_visible: false,
            user_hp_pct: null,
            opp_hp_pct: null,
            user_active: null,
            opp_active: null,
          })
        : e,
    )
    render(<MatchTimeline result={result} />)
    expect(screen.getByTestId('timeline-axis').lastElementChild?.textContent).toBe('00:17')
    expect(flags()[0].parentElement!.style.left).toBe(`${+((5_000 / 17_000) * 100).toFixed(2)}%`)
    await userEvent.setup().hover(flags()[0])
    expect(screen.getByRole('tooltip').textContent).toContain('00:05')
  })
})

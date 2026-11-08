# Team Builder ranks Teams by sampled 3v3 win odds against PvPoke's meta groups

The Team Builder's headline is a win probability with an uncertainty ("wins 64%, 60–68%"), not a single matchup score, because that is what our own 3v3 engine can offer and rating-based tools cannot. Opponents come from PvPoke's published meta group for each open League (`great`, `ultra`, `master`), a snapshot committed next to the gamemaster (ADR 0004), each meta Pokémon at PvPoke's default IVs and level for that League (Master: Level 50, 15/15/15). A 3v3 battle costs ~8–25 ms because TrainingAI simulates several 1v1s per decision, so we cannot simulate every Team against every opposing Team. The search runs in stages, each cheap enough to narrow the next:

1. **Screen** every eligible Caught Pokémon with 1-1 shield 1v1 battles against each meta Pokémon, keeping the best per Species (GBL bans duplicates). The best 30 Species are then rated at 0-0, 1-1 and 2-2 shields, and the top 10 by that average go on.
2. **Shortlist** the 120 Teams of those 10 by coverage: for each meta Pokémon, the Team's best 1v1 rating against it, averaged. Keep the top 5.
3. **Select** a lead for each shortlisted Team: 3v3 battles of each of its 3 lead orders against the same 100 sampled opposing Teams (common random numbers, so differences between Teams aren't sampling noise).
4. **Estimate** the 3 best Teams' win odds on 400 *fresh* opposing Teams, with a Wilson 95% interval (about ±5 points). Fresh samples because the best-looking of several noisy estimates is biased upward (winner's curse); selection and estimation never share data.

Ties count as half a win. Sampling uses a fixed seed, so the same Collection and League always give the same answer. A Caught Pokémon is evaluated as built to the highest level under the League's CP Cap (at most 50), never below its current level; one already over the Cap is not eligible.

## Considered Options

- **Rank Teams by averaged 1v1 battle ratings** (what PvPoke-data tools do). Rejected as the headline: it ignores switching, shared shields and energy carried across a 3v3. Kept as the cheap screening stage.
- **Simulate every Team exhaustively.** Rejected: 220 Teams × 3 leads × 200 opponents is ~2.6 hours of single-core work per request at the original 20 ms per battle.
- **Random draws inside TrainingAI** for opponent behaviour. Deferred: the bot stays deterministic (argmax), so the uncertainty reported is over which meta Team you face, not over in-battle choices. Adding seeded randomness to the bot is the next step if Teams overfit to its predictability.
- **Top N of PvPoke's rankings as the meta.** Rejected: rankings measure strength, not what players actually bring; meta groups are PvPoke's curated list of common picks.

## Consequences

A request runs ~2,700 3v3 battles plus one 1-1 battle per eligible Pokémon per meta Pokémon: 7–10 s for a 3,000-Pokémon box on 8 cores, proportionally longer on a small server. If that is too slow in production, the next step is running builds as background jobs. Results depend on the meta snapshot, which must be refreshed by hand like the gamemaster, and each result names the snapshot it used.

# Build our own battle simulator instead of using PvPoke's matchup data

The Team Builder evaluates Teams from the Trainer's own Collection (real IV Spreads and Movesets), which PvPoke's published matchup data cannot reflect, and it needs to reason about Roles and Shield Scenarios. We therefore build our own battle simulator in the backend. To keep it trustworthy, PvPoke's simulator is used as a test oracle: the same Matchup must produce the same result in both. The first version simulates 1v1 Matchups across Shield Scenarios; full 3v3 simulation with switching comes later.

import { useEffect, useState } from "react";
import { Link } from "react-router";
import { SearchBar } from "../components/SearchBar";
import { PokemonArt } from "../components/PokemonArt";
import { Reveal } from "../components/Reveal";
import { useScrollVariable } from "../lib/motion";
import { useSpecies } from "../species/species-context";
import { formatDex } from "../lib/species";
import type { SpeciesOption } from "../types";

// Staples of GO Battle League — quick ways into the app
const POPULAR_IDS = [
  "medicham",
  "azumarill",
  "registeel",
  "skarmory",
  "altaria",
  "trevenant",
  "lickitung",
  "giratina_altered",
];

const SECTIONS = [
  { id: "top", label: "Search" },
  { id: "popular", label: "Popular" },
  { id: "menu", label: "What it does" },
  { id: "brew", label: "How teams are built" },
  { id: "glossary", label: "Glossary" },
];

const GLOSSARY = [
  {
    title: "Stat Product Rank",
    body: "Attack × Defense × HP at the highest level under the league cap. Rank 1 is the bulkiest overall spread.",
  },
  {
    title: "Mirror Rank",
    body: "Spreads ordered by Attack. A better Mirror Rank wins more CMP ties when both sides throw a charged move together.",
  },
  {
    title: "League caps",
    body: "Great League caps at 1500 CP and Ultra at 2500, so low Attack IVs often let a Pokémon level higher and end up stronger.",
  },
];

// What the Team Builder does, in order (ADR 0005); the same steps it shows while it runs
const BREW_STEPS = [
  {
    title: "Grind",
    body: "Each of your Pokémon battles every meta Pokémon 1v1 at the best level the league allows.",
  },
  {
    title: "Dose",
    body: "Your ten strongest are combined into teams, ranked by how well they cover the meta together.",
  },
  {
    title: "Pull",
    body: "The best teams play full 3v3s, with switching and shields, against 100 meta teams, with every lead tried.",
  },
  {
    title: "Taste",
    body: "Win odds come from 400 fresh battles the teams were never picked on, with a 95% range around them.",
  },
];

export function HomePage() {
  const species = useSpecies();
  const hero = useScrollVariable<HTMLElement>();
  const popular = POPULAR_IDS.map((id) => ({
    id,
    option: species.status === "ready" ? species.byId.get(id) : undefined,
  }));

  return (
    <div>
      <SectionDots />

      {/* 1. Hero: the search bar over the highlands, steam drifting up, the staples floating below */}
      <section
        id="top"
        ref={hero}
        className="relative isolate flex min-h-[calc(100svh-4rem)] flex-col overflow-hidden"
      >
        <HeroScene />
        <Steam />
        <div className="hero-fade relative mx-auto flex w-full max-w-3xl flex-1 flex-col items-center justify-center px-4 py-16 text-center">
          <p className="animate-rise eyebrow tracking-[0.3em] text-brand">
            Pokémon GO PvP · brewed fresh
          </p>
          <h1 className="mt-5 animate-rise text-5xl leading-[1.02] font-black tracking-tight text-balance [animation-delay:120ms] sm:text-7xl">
            Find the best IVs for{" "}
            <span className="text-brand italic">every</span> league
          </h1>
          <p className="mx-auto mt-6 max-w-xl animate-rise text-lg text-ink-soft [animation-delay:240ms]">
            Every one of a species' 4,096 IV spreads, ranked for Little, Great,
            Ultra and Master League. Then a Team Builder that battles your own
            Pokémon against the meta.
          </p>
          <div className="relative z-10 mt-10 w-full max-w-2xl animate-rise text-left [animation-delay:360ms]">
            <SearchBar size="large" autoFocus />
          </div>
          {species.status === "error" && (
            <p className="mt-4 text-sm text-red-300">
              Couldn't load species ({species.message}). Check that the Spring
              backend is running on port 8080.
            </p>
          )}

          <ul
            className="parallax-fast mt-12 flex flex-wrap justify-center gap-3 sm:gap-5"
            aria-label="Popular picks"
          >
            {popular.slice(0, 5).map(({ id, option }, i) => (
              <li
                key={id}
                className={`animate-rise ${i === 4 ? 'hidden sm:block' : ''}`}
                style={{ animationDelay: `${500 + i * 90}ms` }}
              >
                <Link
                  to={`/species/${id}`}
                  className="block h-16 w-16 animate-[bob_4s_ease-in-out_infinite] rounded-full bg-surface/80 p-2 ring-1 ring-line-strong/60 backdrop-blur transition hover:scale-110 hover:ring-brand sm:h-20 sm:w-20"
                  style={{ animationDelay: `${i * 0.6}s` }}
                  aria-label={option?.name ?? id}
                >
                  <PokemonArt
                    dex={option?.dex}
                    alt=""
                    className="h-full w-full"
                  />
                </Link>
              </li>
            ))}
          </ul>
        </div>

        <Ridge />

        <a
          href="#popular"
          className="hero-cue absolute bottom-6 left-1/2 z-10 -translate-x-1/2 text-xs font-semibold tracking-[0.25em] text-ink-soft uppercase hover:text-ink"
        >
          Scroll
          <span
            className="mx-auto mt-2 block h-6 w-px animate-[scroll-cue_1.8s_ease-in-out_infinite] bg-ink-soft"
            aria-hidden="true"
          />
        </a>
      </section>

      {/* 2. Popular */}
      <section id="popular" className="scroll-mt-16 bg-canvas py-24">
        <div className="mx-auto max-w-6xl px-4">
          <Reveal>
            <SectionTitle
              eyebrow="On the menu today"
              title="Popular in GO Battle League"
            />
          </Reveal>
          <ul className="mt-12 grid grid-cols-2 gap-4 sm:grid-cols-4 sm:gap-6">
            {popular.map(({ id, option }, i) => (
              <Reveal as="li" key={id} delayMs={(i % 4) * 90}>
                <PopularCard id={id} option={option} />
              </Reveal>
            ))}
          </ul>
        </div>
      </section>

      {/* 3. About: what the tool does */}
      <section id="menu" className="scroll-mt-16 py-24">
        <div className="mx-auto max-w-6xl px-4">
          <Reveal>
            <SectionTitle
              eyebrow="About DexPresso"
              title="Three things, done properly"
            >
              A free tool for Pokémon GO battlers. No account needed to try any
              of it.
            </SectionTitle>
          </Reveal>

          <div className="mt-16 grid gap-20">
            <Feature
              number="01"
              title="IV rankings for every species"
              body="Search any Pokémon and see all 4,096 of its IV spreads ranked by stat product for each league, with the level each one reaches under the cap. Enter your own IVs to see where yours lands."
              to="/species/medicham"
              cta="Try Medicham"
              visual={
                <BigStat
                  value="4,096"
                  label="spreads ranked per species, per league"
                />
              }
            />
            <Feature
              flip
              number="02"
              title="A Collection of your Pokémon"
              body="Add Pokémon by name, appraisal IVs and CP, and the level works itself out. Or import a whole box from a Poké Genie or Calcy IV export. Without logging in, they stay in your browser."
              to="/collection"
              cta="Open your Collection"
              visual={
                <BigStat
                  value="CP → Lv"
                  label="levels found from the CP you see in game"
                />
              }
            />
            <Feature
              number="03"
              title="A Team Builder that actually battles"
              body="It plays full 3v3 battles with your Pokémon against teams drawn from PvPoke's current meta, and tells you how often each team wins, with an honest range around the number."
              to="/team-builder"
              cta="Build a team"
              visual={
                <BigStat
                  value="400"
                  label="fresh battles behind every team's win odds"
                />
              }
            />
          </div>
        </div>
      </section>

      {/* 4. How the Team Builder brews */}
      <section
        id="brew"
        className="scroll-mt-16 border-y border-line bg-surface/90 py-24"
      >
        <div className="mx-auto max-w-6xl px-4">
          <Reveal>
            <SectionTitle
              eyebrow="How teams are built"
              title="From beans to a cup in four steps"
            />
          </Reveal>
          <div className="relative mt-14">
            <div
              className="absolute top-6 right-[12%] left-[12%] hidden h-px bg-gradient-to-r from-transparent via-line-strong to-transparent md:block"
              aria-hidden="true"
            />
            <ol className="relative grid gap-6 md:grid-cols-4">
              {BREW_STEPS.map((step, i) => (
                <Reveal
                  as="li"
                  key={step.title}
                  delayMs={i * 140}
                  className="relative text-center"
                >
                  <span className="relative mx-auto grid h-12 w-12 place-items-center rounded-full bg-canvas font-display text-lg font-black text-brand ring-2 ring-brand/70">
                    {i + 1}
                  </span>
                  <h3 className="mt-4 font-display text-xl font-black">
                    {step.title}
                  </h3>
                  <p className="mt-2 text-sm leading-relaxed text-ink-soft">
                    {step.body}
                  </p>
                </Reveal>
              ))}
            </ol>
          </div>
        </div>
      </section>

      {/* 5. Glossary */}
      <section id="glossary" className="scroll-mt-16 bg-canvas py-24">
        <div className="mx-auto max-w-6xl px-4">
          <Reveal>
            <SectionTitle
              eyebrow="Barista's notes"
              title="Words worth knowing"
            />
          </Reveal>
          <div className="mt-12 grid gap-5 sm:grid-cols-3">
            {GLOSSARY.map((item, i) => (
              <Reveal key={item.title} delayMs={i * 120} className="card p-6">
                <h3 className="font-display text-lg font-black text-brand">
                  {item.title}
                </h3>
                <p className="mt-3 text-sm leading-relaxed text-ink-soft">
                  {item.body}
                </p>
              </Reveal>
            ))}
          </div>
        </div>
      </section>

      {/* 6. Closing call */}
      <section className="relative py-28">
        <Reveal className="mx-auto max-w-3xl px-4 text-center">
          <p className="eyebrow tracking-[0.3em] text-brand">
            Ready when you are
          </p>
          <h2 className="mt-4 font-display text-4xl font-black tracking-tight sm:text-5xl">
            Pull your first shot
          </h2>
          <p className="mx-auto mt-4 max-w-lg text-ink-soft">
            Add three Pokémon and see which team brews the strongest.
          </p>
          <div className="mt-8 flex flex-wrap justify-center gap-3">
            <Link to="/collection" className="btn btn-primary btn-lg">
              Add your Pokémon
            </Link>
            <Link to="/team-builder" className="btn btn-secondary btn-lg">
              Open the Team Builder
            </Link>
          </div>
        </Reveal>
      </section>
    </div>
  );
}

function SectionTitle({
  eyebrow,
  title,
  children,
}: {
  eyebrow: string;
  title: string;
  children?: React.ReactNode;
}) {
  return (
    <div className="mx-auto max-w-2xl text-center">
      <p className="eyebrow tracking-[0.3em] text-brand">{eyebrow}</p>
      <h2 className="mt-3 font-display text-4xl font-black tracking-tight text-balance sm:text-5xl">
        {title}
      </h2>
      <Ornament />
      {children && <p className="mt-4 text-lg text-ink-soft">{children}</p>}
    </div>
  );
}

/** A thin rule with a coffee bean in the middle, under each section title. */
function Ornament() {
  return (
    <div
      className="mx-auto mt-5 flex max-w-xs items-center gap-3"
      aria-hidden="true"
    >
      <span className="h-px flex-1 bg-gradient-to-r from-transparent to-line-strong" />
      <svg viewBox="0 0 20 28" className="h-5 w-4 fill-brand">
        <ellipse cx="10" cy="14" rx="9" ry="13" />
        <path
          d="M10 2 C5 9 15 19 10 26"
          fill="none"
          className="stroke-canvas"
          strokeWidth="2"
        />
      </svg>
      <span className="h-px flex-1 bg-gradient-to-l from-transparent to-line-strong" />
    </div>
  );
}

function PopularCard({ id, option }: { id: string; option?: SpeciesOption }) {
  return (
    <Link
      to={`/species/${id}`}
      className="group card block overflow-hidden transition duration-300 hover:-translate-y-1.5 hover:ring-brand/70"
    >
      <div className="relative aspect-square overflow-hidden bg-gradient-to-b from-raised to-surface p-5">
        <div
          className="absolute inset-x-6 bottom-4 h-6 rounded-full bg-black/40 blur-md transition group-hover:inset-x-10"
          aria-hidden="true"
        />
        <PokemonArt
          dex={option?.dex}
          alt={option?.name ?? ""}
          className="relative h-full w-full transition duration-500 group-hover:-translate-y-2 group-hover:scale-110"
        />
      </div>
      <div className="flex items-baseline justify-between gap-2 p-4">
        <span className="truncate font-semibold">{option?.name ?? "…"}</span>
        <span className="text-xs tabular-nums text-subtle">
          {option ? formatDex(option.dex) : ""}
        </span>
      </div>
    </Link>
  );
}

function Feature({
  number,
  title,
  body,
  to,
  cta,
  visual,
  flip = false,
}: {
  number: string;
  title: string;
  body: string;
  to: string;
  cta: string;
  visual: React.ReactNode;
  flip?: boolean;
}) {
  return (
    <div className="grid items-center gap-10 md:grid-cols-2 md:gap-16">
      <Reveal className={flip ? "md:order-2" : ""}>
        <span className="font-display text-6xl font-black text-brand/40">
          {number}
        </span>
        <h3 className="mt-2 font-display text-3xl font-black tracking-tight">
          {title}
        </h3>
        <p className="mt-4 leading-relaxed text-ink-soft">{body}</p>
        <Link to={to} className="btn btn-secondary mt-6">
          {cta} <span aria-hidden="true">→</span>
        </Link>
      </Reveal>
      <Reveal delayMs={150}>{visual}</Reveal>
    </div>
  );
}

/** A big figure in a cup-rim ring, for the About rows. Every number on it is a real property of the tool. */
function BigStat({ value, label }: { value: string; label: string }) {
  return (
    <div className="card relative mx-auto grid aspect-[4/3] max-w-md place-items-center overflow-hidden p-8 text-center">
      <div
        className="absolute -top-24 -right-24 h-64 w-64 rounded-full bg-brand/10 blur-2xl"
        aria-hidden="true"
      />
      <div
        className="absolute -bottom-16 -left-16 h-48 w-48 rounded-full border-[14px] border-brand/10"
        aria-hidden="true"
      />
      <div className="relative">
        <div className="font-display text-6xl font-black tracking-tight text-brand sm:text-7xl">
          {value}
        </div>
        <p className="mx-auto mt-3 max-w-[16rem] text-sm text-ink-soft">
          {label}
        </p>
      </div>
    </div>
  );
}

/**
 * The highlands again, brighter than the page backdrop: the overlay stays at 78%+ down to the search bar so text
 * passes AA, then opens up where only the Pokémon and the hills sit. Drifts slower than the page for depth.
 */
function HeroScene() {
  return (
    <div className="pointer-events-none absolute inset-0 -z-20 overflow-hidden" aria-hidden="true">
      <div
        className="parallax-slow absolute -inset-y-24 inset-x-0"
        style={{
          background: `linear-gradient(to bottom, rgb(21 16 13 / 0.86) 0%, rgb(21 16 13 / 0.8) 40%, rgb(21 16 13 / 0.78) 62%,
            rgb(21 16 13 / 0.35) 82%, rgb(21 16 13 / 0.55) 100%), url("/scene-highlands.svg") center 70% / cover no-repeat`,
        }}
      />
    </div>
  )
}

/** Wisps of steam drifting up the hero. Decorative; frozen with reduced motion. */
function Steam() {
  const wisps = [
    { left: "12%", delay: "0s", duration: "9s" },
    { left: "24%", delay: "3s", duration: "11s" },
    { left: "71%", delay: "1.5s", duration: "10s" },
    { left: "84%", delay: "5s", duration: "12s" },
    { left: "50%", delay: "7s", duration: "13s" },
  ];
  return (
    <div
      className="pointer-events-none absolute inset-0 -z-10 overflow-hidden"
      aria-hidden="true"
    >
      {wisps.map((w) => (
        <span
          key={w.left}
          className="absolute bottom-24 h-40 w-10 rounded-full bg-ink/10 blur-xl"
          style={{
            left: w.left,
            animation: `drift ${w.duration} ease-in-out ${w.delay} infinite`,
          }}
        />
      ))}
    </div>
  );
}

/** Dark foreground hills at the bottom of the hero; they stay put while the scene behind drifts, for depth. */
function Ridge() {
  return (
    <svg
      viewBox="0 0 1600 160"
      preserveAspectRatio="none"
      className="pointer-events-none absolute inset-x-0 -bottom-px -z-10 h-28 w-full sm:h-40"
      aria-hidden="true"
    >
      <path
        d="M0 90 C200 40 380 70 560 95 C740 120 900 60 1080 62 C1260 64 1420 105 1600 85 L1600 160 L0 160 Z"
        fill="#1f1611"
      />
      <path
        d="M0 130 C240 100 420 115 640 132 C860 149 1040 110 1240 112 C1400 114 1500 128 1600 124 L1600 160 L0 160 Z"
        fill="#15100d"
      />
    </svg>
  );
}

/** A diamond per section down the right edge, filled for the one in view. Wide screens only. */
function SectionDots() {
  const [active, setActive] = useState("top");
  useEffect(() => {
    // the section crossing the middle of the screen is the current one
    const observer = new IntersectionObserver(
      (entries) =>
        entries.forEach((e) => e.isIntersecting && setActive(e.target.id)),
      { rootMargin: "-50% 0px -50% 0px" },
    );
    SECTIONS.forEach((s) => {
      const el = document.getElementById(s.id);
      if (el) observer.observe(el);
    });
    return () => observer.disconnect();
  }, []);

  return (
    <nav
      aria-label="Sections"
      className="fixed top-1/2 right-6 z-20 hidden -translate-y-1/2 flex-col gap-5 xl:flex"
    >
      {SECTIONS.map((s) => (
        <a
          key={s.id}
          href={`#${s.id}`}
          className="group relative flex items-center justify-end"
          aria-label={s.label}
          aria-current={active === s.id ? "location" : undefined}
        >
          <span className="pointer-events-none absolute right-7 rounded-md bg-surface px-2 py-1 text-xs font-semibold whitespace-nowrap text-ink opacity-0 ring-1 ring-line transition group-hover:opacity-100 group-focus-visible:opacity-100">
            {s.label}
          </span>
          <span
            className={`block h-3 w-3 rotate-45 border transition group-hover:scale-125 group-hover:border-brand ${
              active === s.id
                ? "scale-125 border-brand bg-brand"
                : "border-line-strong"
            }`}
          />
        </a>
      ))}
    </nav>
  );
}

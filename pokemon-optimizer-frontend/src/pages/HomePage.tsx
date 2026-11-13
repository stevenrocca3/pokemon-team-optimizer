import { useEffect, useState } from "react";
import { Link } from "react-router";
import { SearchBar } from "../components/SearchBar";
import { PokemonArt } from "../components/PokemonArt";
import { Reveal } from "../components/Reveal";
import { CreamerRule, LogoText } from "../components/Creamer";
import { DemoPhone } from "../components/DemoPhone";
import { BrewCarousel } from "../components/BrewCarousel";
import { ProofStrip } from "../components/ProofStrip";
import { DEMO_RUN, type DemoPhase } from "../lib/demo-run";
import { useScrollVariable } from "../lib/motion";
import { useSpecies } from "../species/species-context";
import { usePageTitle } from '../lib/page-title'

// Staples of GO Battle League, floating under the hero search: quick ways into the app
const POPULAR_IDS = ["medicham", "azumarill", "registeel", "skarmory", "altaria"];

const SECTIONS = [
  { id: "top", label: "Search" },
  { id: "menu", label: "Watch it brew" },
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
    title: "League Caps",
    body: "Great League caps at 1500 CP and Ultra at 2500, so low Attack IVs often let a Pokémon level higher and end up stronger.",
  },
];

// The demo's steps beside the phone, lit up as it reaches each one
const DEMO_STEPS: { title: string; body: string; phases: DemoPhase[] }[] = [
  { title: "Tick Your Pokémon", body: "No IVs needed: each gets a good spread for the league.", phases: ["pick"] },
  { title: "Pick a League or Cup", body: "Great, Ultra, Master, or any Cup running now.", phases: ["league", "tap"] },
  { title: "It Plays Full 3v3s", body: "Every lineup against 100 meta teams, then 400 fresh battles for the odds.", phases: ["brew"] },
  { title: "Your Best Three, in Order", body: "Lead, safe swap and closer, with honest win odds.", phases: ["result"] },
];

export function HomePage() {
  usePageTitle(null);
  const species = useSpecies();
  const [demoPhase, setDemoPhase] = useState<DemoPhase>("pick");
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
        <div className="hero-fade relative mx-auto flex w-full max-w-3xl flex-1 flex-col items-center justify-center px-4 pt-10 pb-20 text-center">
          <p className="animate-rise eyebrow tracking-[0.3em] text-brand">
            Pokémon GO PvP · Brewed Fresh
          </p>
          <h1 className="mt-4 animate-rise [animation-delay:120ms]">
            <LogoText text="DexPresso" className="text-[5rem] sm:text-[8.5rem]" />
          </h1>
          <p className="mt-4 animate-rise font-display text-3xl leading-tight text-balance [animation-delay:200ms] sm:text-4xl">
            Find the Best IVs for <span className="text-brand">Every</span> League
          </p>
          <p className="mx-auto mt-4 max-w-xl animate-rise text-lg text-ink-soft [animation-delay:240ms]">
            Every one of a species' 4,096 IV spreads, ranked for Little, Great,
            Ultra and Master League. Then a Team Builder that battles your own
            Pokémon against the meta.
          </p>
          <div className="relative z-10 mt-8 w-full max-w-2xl animate-rise text-left [animation-delay:360ms]">
            <SearchBar size="large" autoFocus />
          </div>
          {species.status === "error" && (
            <p className="mt-4 text-sm text-red-300">
              Couldn't load the Pokémon list. {species.message}
            </p>
          )}

          <ul
            className="parallax-fast mt-8 flex flex-wrap justify-center gap-3 sm:gap-5"
            aria-label="Popular picks"
          >
            {popular.map(({ id, option }, i) => (
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
          href="#menu"
          className="hero-cue absolute bottom-6 left-1/2 z-10 -translate-x-1/2 text-xs font-semibold tracking-[0.25em] text-ink-soft uppercase hover:text-ink"
        >
          Scroll
          <span
            className="mx-auto mt-2 block h-6 w-px animate-[scroll-cue_1.8s_ease-in-out_infinite] bg-ink-soft"
            aria-hidden="true"
          />
        </a>
      </section>

      {/* The real totals, once they're big enough to be worth showing */}
      <ProofStrip />

      {/* 2. The Team Builder itself, running on a pixel phone */}
      <section id="menu" className="scroll-mt-16 py-24">
        <div className="mx-auto max-w-6xl px-4">
          <Reveal>
            <SectionTitle eyebrow="The Team Builder" title="Watch It Brew">
              A real run, played back: eight Pokémon in, the best three out.
            </SectionTitle>
          </Reveal>
          <div className="mt-14 grid items-center gap-12 md:grid-cols-[auto_1fr] md:gap-16">
            <Reveal>
              <DemoPhone onFrame={(frame) => setDemoPhase(frame.phase === "fade" ? "result" : frame.phase)} />
            </Reveal>
            <Reveal delayMs={150}>
              <ol className="grid gap-3">
                {DEMO_STEPS.map((step) => {
                  const now = step.phases.includes(demoPhase);
                  return (
                    <li
                      key={step.title}
                      className={`rounded-xl p-4 ring-1 transition-[background-color,box-shadow,transform] duration-300 ${
                        now ? "translate-x-2 bg-surface ring-brand/70 inset-shadow-[0_2px_0] inset-shadow-creamer/40" : "ring-transparent"
                      }`}
                    >
                      <p className={`font-display text-xl transition-colors duration-300 ${now ? "text-brand" : "text-ink-soft"}`}>
                        {step.title}
                      </p>
                      <p className="mt-1 text-sm text-ink-soft">{step.body}</p>
                    </li>
                  );
                })}
              </ol>
              <div className="mt-8 flex flex-wrap gap-3">
                <Link to="/team-builder" className="btn btn-primary btn-lg">
                  Build Your Team
                </Link>
                <Link to="/collection" className="btn btn-secondary btn-lg">
                  Add Your Pokémon
                </Link>
              </div>
              <p className="mt-6 text-sm text-subtle">
                Also here: <Link to="/species/medicham" className="text-brand underline">IV rankings for any species</Link>, all
                4,096 spreads per league, and{" "}
                <Link to="/import" className="text-brand underline">importing from appraisal screenshots</Link> or a Poké Genie or Calcy IV export.
              </p>
              <p className="mt-2 text-xs text-subtle">
                Saved from a real run on PvPoke's {DEMO_RUN.league} meta of {DEMO_RUN.metaDate}, with good league IVs
                assumed for each Pokémon.
              </p>
            </Reveal>
          </div>
        </div>
      </section>

      {/* 3. How the Team Builder brews */}
      <section
        id="brew"
        className="scroll-mt-16 border-y border-line bg-surface/90 py-24"
      >
        <div className="mx-auto max-w-6xl px-4">
          <Reveal>
            <SectionTitle
              eyebrow="How teams are built"
              title="From Beans to a Cup in Four Steps"
            />
          </Reveal>
          <Reveal className="mt-10">
            <BrewCarousel />
          </Reveal>
        </div>
      </section>

      {/* 4. Glossary */}
      <section id="glossary" className="scroll-mt-16 bg-canvas py-24">
        <div className="mx-auto max-w-6xl px-4">
          <Reveal>
            <SectionTitle
              eyebrow="Barista's notes"
              title="Words Worth Knowing"
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

      {/* 5. Closing call */}
      <section className="relative py-28">
        <Reveal className="mx-auto max-w-3xl px-4 text-center">
          <p className="eyebrow tracking-[0.3em] text-brand">
            Ready when you are
          </p>
          <h2 className="mt-4 font-display text-4xl font-black tracking-tight sm:text-5xl">
            Pull Your First Shot
          </h2>
          <p className="mx-auto mt-4 max-w-lg text-ink-soft">
            Add your Pokémon and see which three win most.
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
      <CreamerRule className="mt-5" />
      {children && <p className="mt-4 text-lg text-ink-soft">{children}</p>}
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

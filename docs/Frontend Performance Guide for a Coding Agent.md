# Frontend Performance Guide for a Coding Agent

Nov 7, 2026 · @Steven Rocca

## How to use this guide

You are a coding agent improving the load speed and responsiveness of this website. Work measure-first: find the biggest bottleneck, fix it, prove the fix with numbers, then move to the next one. Do not apply every technique below by default; apply the ones the measurements justify.

**Operating rules**

1. Measure a baseline before editing any code (Phase 0).
2. Work on a separate branch. Make one optimization per commit with a message that names the metric it targets.
3. Never change visible behavior, content, layout, or styling unless asked. A faster page that looks different is a regression.
4. Prefer the framework's built-in tools (Next.js `Image`, Vite chunking, Astro islands) over hand-rolled replacements.
5. Do not add a new dependency to save bytes elsewhere unless the net bundle size goes down. Check with the bundle analyzer.
6. Run the existing build, lint, and test commands after every change. If something breaks, revert that change and note it.
7. Stop and ask before touching server or hosting config, deleting files, upgrading major framework versions, or removing third-party scripts (analytics, ads, chat widgets). Those are business decisions.
8. When a technique does not apply to this stack (for example, Webpack advice on a Vite project), translate it to the stack's equivalent or skip it and say why.

## Phase 0: Detect the stack and measure a baseline

Before any change, know what you are optimizing and how slow it is now.

**Detect the stack.** Read `package.json`, lockfiles, and config files. Record:

- Framework and version (React, Next.js, Vite, Astro, Vue, plain HTML)
- Bundler (Vite, Webpack, Turbopack, Rollup, esbuild)
- Rendering mode (client-only SPA, SSR, static, hybrid)
- Hosting/CDN if visible (Vercel, Netlify, Cloudflare, S3 + CloudFront)
- Whether the React Compiler is enabled (it changes the memoization advice in Rendering)

**Measure.** Build for production and serve the production build, never the dev server.

1. Run Lighthouse (mobile preset) on the 3 most important pages, usually the home page, the main content page, and one heavy page. Run each 3 times and use the median.
2. Record LCP, CLS, Total Blocking Time (lab proxy for INP), Speed Index, and the total transfer size.
3. Note the LCP element Lighthouse reports for each page.
4. Generate a bundle report (`rollup-plugin-visualizer` for Vite, `@next/bundle-analyzer` for Next.js, `webpack-bundle-analyzer` for Webpack). List the 10 largest modules.
5. In Chrome DevTools, record a Performance trace of the main user interaction (opening a menu, filtering a list, typing in search) and note long tasks over 50 ms.

**Prioritize.** Rank problems by expected impact on the worst metric, then by effort. Usual order of payoff: the LCP image, render-blocking resources, oversized JS bundles, third-party scripts, then re-render work. Write this ranked list down before coding.

## Targets

Aim for Google's "good" Core Web Vitals thresholds on mobile, plus a JavaScript budget. Interaction to Next Paint (INP) replaced First Input Delay as a Core Web Vital in 2024, so ignore any FID advice.

| Metric | What it measures | Good target | Main levers in this guide |
| --- | --- | --- | --- |
| LCP (Largest Contentful Paint) | When the main content appears | ≤ 2.5 s | Images, fonts, render-blocking scripts, CDN |
| INP (Interaction to Next Paint) | Delay between a tap/click and the screen updating | ≤ 200 ms | JS size, re-renders, long tasks, third-party scripts |
| CLS (Cumulative Layout Shift) | How much the page jumps while loading | ≤ 0.1 | Image dimensions, font loading, reserved space for embeds |
| FCP (First Contentful Paint) | When anything first appears | ≤ 1.8 s | Compression, async/defer, critical CSS |
| TBT (Total Blocking Time, lab only) | Main-thread blocking during load | ≤ 200 ms | Code splitting, deferring scripts |
| Initial JS (compressed) | Script sent before the first page works | ≤ 170 KB as a starting budget | Code splitting, tree shaking, lighter libraries |

If the baseline already meets a target, leave that area alone unless a cheap fix exists.

## 1. Code splitting and lazy loading

Ship only the code the current page needs; load the rest on demand. Targets LCP, TBT, and INP.

**Check first:** Does the initial bundle contain code for routes or features the first page never uses? The bundle report from Phase 0 answers this.

**Do:**

- Split by route first. Next.js, Remix, SvelteKit, and Nuxt already do this; confirm it is working rather than re-implementing it. For a plain React SPA, wrap each route component in `React.lazy()` with a `<Suspense>` fallback.
- Lazy-load heavy components that are not visible on first paint: modals, charts, rich-text editors, maps, date pickers, admin panels.
- Load heavy libraries at the moment they are needed with dynamic `import()` (for example, load a PDF or chart library only when the user opens that view).
- Use the bundler's native splitting: Vite/Rollup `build.rollupOptions.output.manualChunks` or Webpack `splitChunks` to separate large, rarely changing vendor code so it caches well.
- Optionally prefetch the next likely route on hover or when idle so lazy loading does not feel slower.

**Don't:**

- Lazy-load anything above the fold, especially the component that renders the LCP element.
- Split into dozens of tiny chunks; each request has overhead. Aim for a few meaningful chunks.
- Use a fallback that changes layout size when the real component arrives (causes CLS). Reserve the space.

**Done when:** Initial JS drops measurably in the bundle report and the first page still renders identically.

## 2. Images and the LCP element

Images are usually the heaviest bytes on a page and often the LCP element itself. Targets LCP, CLS, and total page weight.

**Check first:** List every image over 100 KB and every image displayed much smaller than its file's pixel size. Identify which image (if any) is the LCP element on each key page.

**Do:**

- Serve AVIF or WebP with a fallback, using `<picture>` or the framework's image component (`next/image`, `astro:assets`, `@unpic`). Prefer converting at build time over committing new binary files by hand.
- Serve responsive sizes with `srcset` and an accurate `sizes` attribute so phones do not download desktop-size images.
- Set `width` and `height` (or CSS `aspect-ratio`) on every image so the browser reserves space. This prevents CLS.
- Add `loading="lazy"` and `decoding="async"` to images below the fold.
- Treat the LCP image differently: no lazy loading, add `fetchpriority="high"`, and preload it with `<link rel="preload" as="image">` if it is discovered late (for example, a CSS background or an image injected by JavaScript).
- Compress with a build step (`sharp`, `vite-imagetools`, `imagemin`) at roughly quality 75–80 for photos. Use SVG for icons and logos; inline small SVG icons instead of loading an icon font.
- Replace autoplaying GIFs with muted, looping `<video>` in MP4/WebM.

**Don't:**

- Lazy-load the hero or any image visible on first paint. This is the most common way "optimization" makes LCP worse.
- Overwrite original source images; generate optimized variants alongside them.

**Done when:** No image above the fold is lazy-loaded, every image has dimensions, and page weight from images is down in the Lighthouse report.

## 3. Fonts

Web fonts can block text from appearing and shift layout when they swap in. Targets FCP, LCP (when the LCP element is text), and CLS.

**Do:**

- Self-host fonts in WOFF2 instead of loading them from a third-party font server; this removes an extra connection. Next.js `next/font` and Fontsource do this automatically.
- Set `font-display: swap` (or `optional` for non-brand fonts) so text shows immediately.
- Preload only the one or two font files used above the fold, with `crossorigin` on the preload link.
- Subset fonts to the character sets actually used, and drop weights and styles the site never uses.
- Use `size-adjust` / `ascent-override` fallback metrics (built into `next/font` and `fontaine`) so the swap does not move text.

**Don't:** Preload every weight, or load an entire icon font for a handful of icons.

**Done when:** Lighthouse shows no font-related render blocking and CLS from font swaps is gone.

## 4. Minify, compress, tree-shake

Send the smallest possible version of every file. Targets FCP, LCP, and TBT.

**Check first:** Most modern bundlers minify production builds by default. Confirm the deployed site is a production build and check response headers for `content-encoding: br` or `gzip`.

**Do:**

- Confirm JS, CSS, and HTML minification is on in the production build.
- Enable Brotli (preferred) with Gzip fallback for text assets. Many hosts do this automatically; if not, flag it rather than changing server config yourself (rule 7).
- Make tree shaking effective: use ES module imports, import specific functions (`import debounce from 'lodash-es/debounce'` instead of all of lodash), and set `"sideEffects": false` in `package.json` only if it is actually true.
- Find and replace heavy dependencies with lighter ones when usage is small: `moment` → `date-fns` or `dayjs`, full `lodash` → native methods or `lodash-es`, large icon packs → per-icon imports.
- Remove unused CSS. With Tailwind, confirm the content paths are set so unused classes are purged. Inline critical above-the-fold CSS only if the framework supports it cleanly.
- Delete dead code, unused dependencies (`depcheck` or `knip`), and leftover `console.log` calls.

**Don't:** Replace a dependency in a way that changes behavior (date formatting, time zones, rounding) without tests covering it.

**Done when:** Compressed transfer size for JS and CSS is down in the bundle report and Lighthouse.

## 5. Rendering and re-renders

Keep the main thread free so taps and typing respond instantly. Targets INP and smooth scrolling/animation.

**Check first:** Use the React DevTools Profiler ("Highlight updates") and the Chrome Performance trace from Phase 0. Fix only components that re-render often and take measurable time. Memoizing cheap components adds complexity for no gain.

**Do:**

- If the React Compiler is enabled, skip manual `React.memo`, `useMemo`, and `useCallback` unless the profiler shows a specific need. If it is not enabled, apply them only to measured hot spots: expensive child components receiving stable props, expensive calculations, and callbacks passed to memoized children.
- Move state down to the component that uses it, so a keystroke does not re-render the whole page.
- Virtualize long lists and tables (over \~100 rows) with `@tanstack/react-virtual` or `react-window`.
- Debounce search inputs and throttle scroll/resize handlers. Use `useDeferredValue` or `startTransition` for expensive updates triggered by typing.
- Break up long tasks over 50 ms: move heavy computation to a Web Worker, or yield to the browser between chunks.
- Avoid layout thrashing: do not read layout (`offsetHeight`, `getBoundingClientRect`) and write styles in the same loop.
- Animate only `transform` and `opacity`. Avoid animating `width`, `height`, `top`, `left`, or large `box-shadow` and `filter` effects.
- Add `content-visibility: auto` with `contain-intrinsic-size` to long below-the-fold sections.

**Don't:** Wrap every component in `React.memo` by reflex.

**Done when:** The main interaction's trace shows no long tasks over 200 ms and the profiler shows fewer unnecessary renders.

## 6. Scripts and third-party code

Non-critical scripts should never block the first render. Targets FCP, LCP, TBT, and INP.

**Check first:** List every `<script>` tag and third-party domain in the Network panel (analytics, tag managers, chat widgets, A/B testing, social embeds, ads). Note each one's size and main-thread time from Lighthouse's "Reduce the impact of third-party code" audit.

**Do:**

- Add `defer` to scripts that need the DOM or must run in order; add `async` to independent scripts like analytics. `defer` in the `<head>` is the modern replacement for moving scripts to the bottom of the page.
- Use the framework's script loader where one exists (Next.js `<Script strategy="lazyOnload">` or `"afterInteractive"`).
- Load chat widgets, video embeds, and maps on interaction with a lightweight placeholder (a "facade"): show a static image, and load the real embed when the user clicks.
- Add `<link rel="preconnect">` for the one or two third-party origins needed early (for example, an image CDN or API host). Do not preconnect to everything.
- Report scripts that look unused or duplicated (two analytics tools, an old tag) instead of removing them (rule 7).

**Done when:** Lighthouse lists no render-blocking scripts and third-party main-thread time is down.

## 7. State management and data fetching

Inefficient state and over-fetching cause slow updates and wasted network time. Targets INP and LCP for data-driven pages.

**Check first:** Look for global stores holding state only one component uses, context providers near the app root that change often, request waterfalls in the Network panel (request B waits for request A when it does not need to), and API responses much larger than what the UI shows.

**Do:**

- Keep state local unless several distant components truly share it.
- Split large React contexts so a frequently changing value does not re-render every consumer. Use selectors (Zustand, Redux Toolkit `useSelector`) so components subscribe only to the slice they read.
- Do not migrate state libraries (for example Redux → Zustand) as a performance fix unless profiling proves the library is the bottleneck. Ask first; it is a large refactor.
- Use a data-fetching cache (TanStack Query, SWR, or the framework's loader/fetch cache) to dedupe requests and avoid refetching on every mount.
- Fetch independent data in parallel (`Promise.all`) and, with SSR, start fetches on the server instead of after hydration.
- Paginate or infinite-scroll large lists, and request only the fields the UI needs.
- Normalize nested data when the same entity is updated in several places.

**Done when:** No unnecessary request waterfalls remain on key pages and payload sizes match what the UI displays.

## 8. CDN and caching

Serve static files from close to the user and let repeat visitors skip downloads entirely. Targets LCP and repeat-visit speed.

**Check first:** Inspect response headers for HTML, JS, CSS, images, and fonts. Note `cache-control`, `etag`, `content-encoding`, and whether a CDN header (`cf-cache-status`, `x-vercel-cache`, `x-cache`) shows a cache hit.

**Do:**

- Confirm static assets are served from a CDN. Vercel, Netlify, and Cloudflare Pages do this by default.
- Fingerprinted assets (file names with a content hash, like `app.3f9a1c.js`) get `Cache-Control: public, max-age=31536000, immutable`.
- HTML gets a short or revalidating policy (`no-cache` or a short `max-age` with `stale-while-revalidate`) so deploys show up immediately.
- Keep `ETag` or `Last-Modified` on non-fingerprinted files so revalidation is cheap.
- If header changes require hosting config (`vercel.json`, `netlify.toml`, `_headers`, server config), propose the exact change and ask before applying it (rule 7).

**Don't:** Put long cache lifetimes on files without hashed names; users will be stuck with stale versions.

**Done when:** A repeat page load in DevTools serves hashed assets from cache with no network download.

## 9. Verify and report

Every change needs before/after numbers measured the same way as the baseline.

**Verify:**

1. Rebuild for production and rerun Lighthouse on the same pages, same preset, median of 3 runs.
2. Rerun the bundle analyzer and compare initial JS size.
3. Click through every page you touched and compare it against the original visually, including mobile width.
4. Run the full build, lint, type-check, and test suite.
5. Optionally add a guardrail so performance does not regress: Lighthouse CI or a bundle-size check (`size-limit`) in CI, with budgets from the Targets table.

**Report** in this format when finished:

| Page | Metric | Before | After |
| --- | --- | --- | --- |
| / | LCP |  |  |
| / | TBT |  |  |
| / | CLS |  |  |
| all | Initial JS (KB, compressed) |  |  |

Then list: each change made and the commit that made it; anything tried and reverted, with the reason; and recommendations that need a human decision (server config, removing third-party scripts, library migrations).

Lab numbers are a proxy. Real-user INP and LCP come from field data (Search Console's Core Web Vitals report, the `web-vitals` library, or a RUM tool like Sentry Performance or New Relic). If none is set up, recommend adding the `web-vitals` library to report to existing analytics.

## Kickoff prompt

Export this doc as Markdown, save it in the repo as `docs/PERFORMANCE_GUIDE.md`, then paste this to the agent:

```text
Read docs/PERFORMANCE_GUIDE.md and follow it to improve this website's frontend performance.

Start with Phase 0 only: detect the stack, build for production, measure the baseline, and give me the ranked list of problems with the change you propose for each. Wait for my go-ahead before editing code.

After I approve, work through the list one change per commit on a new branch, following the operating rules. Finish with the report described in section 9.

Key pages to measure: [list your URLs or routes here]
```

The pause after Phase 0 lets you check the plan before anything changes. Remove it if you want the agent to run start to finish.

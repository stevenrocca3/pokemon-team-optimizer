# Launch checklist

Everything here is done before DexPresso goes public (target: 2026-11-30).
Numbers match the original list; 7 and 8 were skipped.

## Legal and privacy
- [ ] 1. Privacy policy — Google asks for a privacy policy URL before the consent screen can leave Testing mode
- [ ] 2. Terms of service
- [ ] 3. Cookie consent banner — today's cookies are `JSESSIONID` and `XSRF-TOKEN`, both strictly necessary; recheck if analytics is added
- [ ] 4. Check form consents
- [ ] 5. No unnecessary data — `Trainer` stores Google ID and name only
- [ ] 6. Audit 3rd party SDKs — Google OAuth, Google Fonts (Fraunces), PokeAPI sprites from GitHub
- [ ] 14. Add business details
- [ ] 15. Age consent for kids' data — Pokémon players skew young; decide on a minimum age (COPPA: under 13)
- [ ] 17. License fonts/images — Fraunces is OFL; official Pokémon artwork is Nintendo/TPC IP, add a fan-site disclaimer
- [ ] 18. Data deletion request — delete a Trainer and their collection

## UX and accessibility
- [ ] 9. Remove dark patterns
- [ ] 10. Remove unsupported claims
- [ ] 11. Accessibility alt text
- [ ] 12. Fix color contrast
- [ ] 13. Keyboard navigation
- [ ] 21. Add loading states
- [ ] 22. Add empty states
- [ ] 23. Handle failed requests
- [ ] 24. Handle API timeouts

## Email and subscriptions
- [ ] 16. Unsubscribe link in emails — only if DexPresso sends email
- [ ] 25. Prevent duplicate subscriptions — only if there are paid plans

## Backend hardening
- [ ] 19. Add rate limiting — the battle sim and team builder are CPU-heavy
- [ ] 20. Set API limits
- [ ] 26. Optimize DB queries
- [ ] 27. Add DB indexes — `caught_pokemon.trainer_id` (every collection query filters on it)
- [ ] 28. Paginate large results
- [ ] 29. Compress files
- [ ] 30. Limit upload size — Poké Genie CSV import
- [ ] 31. Cache repeat requests — gamemaster data, rankings

## Web presence and SEO
- [ ] W1. Meta titles and descriptions — `<title>` exists; no `<meta name="description">` yet, and SPA routes all share one title
- [ ] W2. Social preview image (`og:image`, `twitter:card`)
- [x] W3. Favicon — `public/favicon.svg`; still add PNG/apple-touch sizes for iOS and social cards
- [ ] W4. Sitemap and robots.txt — keep `/api` and `/oauth2` out of crawls
- [ ] W5. Alt text on images — same as #11
- [ ] W6. Compress images — Pokémon art loads from PokeAPI; see #29
- [ ] W7. Check page load speed (Lighthouse)
- [ ] W8. Make mobile friendly
- [x] W9. Custom 404 page — `NotFoundPage.tsx`; the server also has to send unknown routes to `index.html` when deployed
- [ ] W10. Fix broken links
- [ ] W11. Form validation — backend returns field errors; frontend should show them per field
- [ ] W12. Spam protection — CSV import and collection writes (pairs with #19 rate limiting)
- [ ] W13. Set up analytics — if it sets non-essential cookies, #3 needs a real consent banner
- [ ] W14. One clear call to action

## Operations
- [ ] 32. Uptime monitoring
- [ ] 33. Error logging
- [ ] 34. Test simultaneous users
- [ ] 35. Test backup restore — Postgres

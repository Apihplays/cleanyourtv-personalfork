# Official JAKIM prayer times in the prayer-times add-on

Date: 2026-11-02
Status: approved (human partner confirmed TRG01, Kuala Terengganu / Kampung Paloh)

## Intent

The TV shows Kuala Terengganu's prayer times exactly as JAKIM publishes them
in E-Solat. Matching the official table matters: a minute of drift could make
a Ramadan fast break early or late, so the data must come from the published
table, not from a calculation engine.

## Goals

- A data source reading `https://api.waktusolat.app/v2/solat/{zone}` —
  JAKIM E-Solat data, no key, one call returns the whole month as epoch
  seconds plus Hijri dates (verified live: zone `trg01`).
- Works for any JAKIM zone by config; this TV uses `trg01`.
- Status line shows `JAKIM · TRG01 · <fetched time>`, so debugging is one
  glance.
- Reminders, countdown, launcher module card keep working as they do.

## Non-goals

- No self-hosted server (the linked Laravel fork is unnecessary; the public
  API serves the same data no-key).
- No launcher changes, no launcher `INTERNET` (add-ons may have it; this one
  already does).
- Aladhan stays the untouched default source for other users.

## Approaches considered

1. **Reconfigure Aladhan only** — zero code, but Aladhan has no JAKIM
   method; times drift from the official table and a hand-tuned offset needs
   re-verifying forever. Rejected: official times are the point.
2. **Self-host the Laravel API fork** — months of PHP/MySQL/Docker ops to
   serve data the public API already gives free. Rejected.
3. **Add a waktusolat source to the existing add-on (chosen)** — contained
   change to the fetch/parse layer; UI untouched.

## Constraints

- Add-on style: plain Java, `minSdk` 26, unit-test pattern follows
  `addons/weather` (pure-Java class + JUnit local tests).
- Nothing built until the human partner approves the plan.

## Debugging properties

- Each prayer is an exact epoch timestamp (seconds); the maghrib alarm can
  fire at the published second. JAKIM publishes whole minutes, and the
  existing scheduler already uses minute resolution — unchanged.
- `curl https://api.waktusolat.app/v2/solat/trg01` diffable against the
  app's month cache.
- Rounded minute and status-line dates so it is visible which fetch is on
  screen.

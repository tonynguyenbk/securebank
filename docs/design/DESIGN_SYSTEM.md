# SecureBank — Design System ("Ledger & Banknote")

Status: **v1 — in use** · Owner: frontend · Stack: React + TS + Vite + Tailwind v4

## 1. Subject, audience, job

| | |
|---|---|
| Subject | A retail bank's internal-transfer platform (VND), with a back-office for fraud & audit |
| Audiences | **Customer** (checks balance, sends money, reads receipts) · **Bank staff** (triages fraud, freezes accounts) · **Auditor** (reads, never mutates) |
| Single job per portal | Customer: *"Do I have the money, and did it arrive?"* · Ops: *"What needs a human decision right now?"* |

The visual language is taken from the bank's own physical artifacts — **ledger books, passbooks,
banknote security printing, rubber posting stamps, cheques** — not from generic SaaS dashboards.

## 2. Input from `/ui-ux-pro-max` and what was changed

| Skill recommendation | Kept? | Decision |
|---|---|---|
| Pattern: Trust & Authority, Swiss/minimal, dense dashboard | ✅ Kept | Grid-based, high-contrast, functional |
| Typography: IBM Plex Sans | ✅ Kept (body) | Plus Plex Mono for every figure, see §4 |
| Palette: amber `#F59E0B` + purple `#8B5CF6` on navy `#0F172A` | ❌ Replaced | Dark-navy + bright accent is one of the most common AI-dashboard looks, and purple has nothing to do with banking. Replaced by an ink-green "banknote" palette |
| Subtle motion (300 ms fades), Lucide icons, no emoji | ✅ Kept | No GSAP needed — CSS transitions only |
| Avoid: playful design, purple/pink gradients | ✅ Kept | No gradients at all, except the guilloché line art |

Defaults we also avoid on purpose (self-critique against generic outputs): cream + terracotta serif look,
near-black + acid-green accent, broadsheet hairline newspaper layout, "big number + gradient chip" KPI cards,
glassmorphism, blurred colour blobs, `rounded-2xl shadow-xl` on everything, stock shadcn grey.

## 3. Colour tokens

Defined once in `frontend/src/index.css` (`@theme`) — components never use raw hex.

| Token | Hex | Role |
|---|---|---|
| `paper` | `#F3F6F4` | App background — cool green-grey, like banknote paper (not cream) |
| `sheet` | `#FFFFFF` | Cards / ledger sheets |
| `ink` | `#13201E` | Primary text, ops sidebar |
| `ink-2` | `#4A5A57` | Secondary text (≈7:1 on paper) |
| `rule` | `#D3DCD8` | Ledger rules, borders |
| `vault` | `#0D5C55` | Primary action, links, focus ring (deep banknote green) |
| `vault-deep` | `#0A4843` | Primary hover |
| `vault-tint` | `#E2EFEC` | Selected rows, active nav |
| `credit` | `#11734B` | Money in (+) |
| `debit` | `#9E2A1E` | Money out (−), destructive, FROZEN, CRITICAL (seal red) |
| `brass` | `#9A6B12` | Warnings, MEDIUM/HIGH risk, "pending" — used sparingly |

Risk levels map to a 4-step scale: LOW `ink-2` · MEDIUM `brass` · HIGH `debit` outline · CRITICAL `debit` solid.
Colour is never the only signal: every status also carries a text label and an icon/shape.

**Dark mode — "vault at night"** (not near-black + neon). Tokens are CSS variables on `:root`, overridden by
`data-theme="dark"` or by the OS preference when the user hasn't picked light. Toggle cycles System → Light → Dark,
stored in `localStorage` (`sb.theme`) and applied by an inline script before first paint.

| Token | Dark hex | Note |
|---|---|---|
| `paper` | `#0D1615` | Deep green-ink, not neutral black |
| `sheet` | `#142120` | Raised sheet |
| `ink` / `ink-2` | `#E4ECE9` / `#9CAFAA` | |
| `rule` | `#283936` | |
| `vault` / `on-vault` | `#5DBCAF` / `#0D1615` | Primary flips to light teal with dark text |
| `credit` / `debit` / `brass` | `#57C793` / `#F08070` / `#DCAE55` | Lifted for ≥4.5:1 on `sheet` |
| `band` | `#0F3F3A` | Cheque header band (light: `#0D5C55`) |

## 4. Typography

| Role | Face | Usage |
|---|---|---|
| Display | **Be Vietnam Pro** 600–700, tracking −1% | Page titles, section heads, login headline. Vietnamese-designed face with full diacritics (replaced Schibsted Grotesk, which has no Vietnamese subset) |
| Body / UI | **IBM Plex Sans** 400/500/600 | Everything else. Full Vietnamese coverage |
| Figures | **IBM Plex Mono** 400/500, `tabular-nums` (`figures` utility) | Amounts, account numbers, transaction references, timestamps, correlation IDs |

All fonts self-hosted via `@fontsource` (no external requests, no layout shift).
Scale (px): 11 · 13 · 14 (base UI) · 16 · 20 · 28 · 40. Line-height 1.5 body, 1.15 display.
Amounts are always right-aligned, in mono, with VND grouping `25.000.000 ₫` via `Intl.NumberFormat('vi-VN')`
in both languages, and a sign column so `+` / `−` line up vertically like a real ledger.

## 5. Layout

Customer portal — top bar + content, max-width 1120 px:

```
┌───────────────────────────────────────────────────────────────────────┐
│ SecureBank   Overview  Accounts  Transfer  Activity     EN|VI  ◔ A ▾  │
├───────────────────────────────────────────────────────────────────────┤
│ Good afternoon, Nguyen Van A                       [ Send money → ]   │
│                                                                       │
│ ┌─ PASSBOOK ──────────────────────────────┐  ┌─ This month ─────────┐ │
│ │ ░░ guilloché ░░░░░░░░░░░░░░░░░░░░░░░░░░ │  │ In     +11.000.000   │ │
│ │ Current account · 1000 0000 01  ACTIVE  │  │ Out     −4.250.000   │ │
│ │                    25.000.000 ₫         │  │ ▁▃▅▂▇▃  (30 days)    │ │
│ └─────────────────────────────────────────┘  └──────────────────────┘ │
│                                                                       │
│ Recent activity                                        View all →     │
│ ───────────────────────────────────────────────────────────────────── │
│ 08 Oct  TX202610080001  To ****0002  Dinner     −1.000.000  POSTED    │
│ 07 Oct  TX202610070014  From ****0002           +5.000.000  POSTED    │
└───────────────────────────────────────────────────────────────────────┘
```

Ops portal — dark ink sidebar (the only dark surface; tells staff instantly which portal they are in), dense tables:

```
┌────────────┬──────────────────────────────────────────────────────────┐
│ SecureBank │ Fraud queue                     12 open · 3 critical     │
│ OPERATIONS │ ┌──────────────────────────────────────────────────────┐ │
│            │ │ Risk  Txn ref         Customer   Amount     Status   │ │
│ ▸ Today    │ │ ■ 90  TX20261008…     Nguyen A   150.000.000 OPEN    │ │
│   Fraud 12 │ │ ▣ 60  TX20261008…     Tran B      80.000.000 REVIEW  │ │
│   Accounts │ └──────────────────────────────────────────────────────┘ │
│   Customers│                                                          │
│   Txns     │                                                          │
│   Audit    │                                                          │
└────────────┴──────────────────────────────────────────────────────────┘
```

Breakpoints: 375 / 768 / 1024 / 1440. Ops sidebar collapses to a top drawer below 1024 px. Tables become stacked rows below 768 px.

Login: a single cheque-style sheet — guilloché header band, sign-in form on the left, demo-accounts ledger
on the right, a dashed "perforation" footer with the build hash (so a reviewer can see which commit is live).

## 6. Signature element — the double-entry receipt

The one memorable thing: **every transfer is shown as a double-entry ledger slip.** The receipt
and the transaction-detail page render the two real ledger entries returned by the API side by side —
`DEBIT ****0001 −1.000.000` / `CREDIT ****0002 +1.000.000` — joined by a rule, with balance-before /
balance-after, and a rotated rubber-stamp mark (`POSTED`, `REJECTED`, `FROZEN`) set in Plex Mono inside
a double-rule border. It is not decoration: it is the product's main technical claim (ACID double-entry ledger)
made visible to the user and to an interviewer.

Supporting (quiet) element: a **procedurally generated guilloché pattern** (`components/Guilloche.tsx`,
parametric sine bands / rosettes like banknote security printing) seeded from the account number, so every
account card is unique but consistent. Used only on account cards and the login band, at low opacity.

Everything else stays plain: 1 px rules, 6 px radius, no drop shadows.

## 7. Components

`AppShell` (customer) · `OpsShell` · `Money` (formatter + sign column) · `AccountNumber` (masked/full toggle) ·
`StatusStamp` · `RiskBadge` · `LedgerSlip` · `Guilloche` · `LanguageSwitch` · `PreviewBadge` · `DataTable`
(sortable, paginated, empty/loading/error rows) · `FilterBar` · `Field` (label + helper + inline error) ·
`ConfirmDialog` (focus-trapped) · `Toast` · `EmptyState` · `ErrorState` · `Skeleton` · `Pagination`.

## 8. Motion

- 150 ms colour/border transitions on hover/focus; 200 ms dialog fade + 4 px rise; exit faster than enter.
- One orchestrated moment only: the stamp on a successful receipt "lands" (scale 1.08 → 1, 180 ms).
- All motion disabled under `prefers-reduced-motion`.

## 9. Copy rules

- Bank vernacular, plain verbs: "Send money", "Review transfer", "Confirm and send", toast "Sent".
- Errors say what happened + what to do: *"This account is frozen. Outgoing transfers are blocked — contact the branch."*
  (mapped from API codes such as `ACCOUNT_FROZEN`, `DAILY_LIMIT_EXCEEDED`).
- Empty states invite action: *"No transfers yet. Send your first one."*
- No exclamation marks, no "Oops", no marketing adjectives.
- Bilingual EN/VI: every string lives in `src/i18n/{en,vi}.json`, keyed by meaning (`transfer.confirm`), never by English text.
  Vietnamese copy is written natively (banking vernacular: "Chuyển tiền", "Số dư khả dụng", "Đã ghi sổ"), not machine-translated.
  Layouts must tolerate VI strings ~30 % longer than EN. API error codes map to both languages.

## 10. "Doesn't look AI-made" — techniques applied

1. Visual identity derived from the subject (ledger, passbook, stamp, cheque, guilloché) instead of a template.
2. Hand-picked token set with contrast checked; no default Tailwind palette (`slate-*`, `indigo-*`) in components.
3. Procedural SVG guilloché — custom art, no stock illustration, no AI-generated imagery.
4. Real typography craft: tabular numerals, sign column, VND grouping, masked account numbers, a Vietnamese-designed display face.
5. Realistic, deterministic seed data (Vietnamese names, plausible amounts and times) — no "Lorem ipsum", no "John Doe".
6. Self-hosted fonts via `@fontsource`.
7. Visual QA with Playwright screenshots at 375/1024/1440 after each UI phase, then one "remove an accessory" pass.
8. Icons: Lucide at stroke 1.5, sized to the text's cap height, never icon-in-a-coloured-circle.

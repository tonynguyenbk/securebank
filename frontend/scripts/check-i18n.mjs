// Lists translation keys used in src/ that are missing from en.json / vi.json, and keys present in only one locale.
// Usage: node scripts/check-i18n.mjs  (exit code 1 when something is missing)
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = join(dirname(fileURLToPath(import.meta.url)), '..', 'src')
const files = []
const walk = (d) =>
  readdirSync(d).forEach((f) => {
    const p = join(d, f)
    if (statSync(p).isDirectory()) walk(p)
    else if (/\.tsx?$/.test(f)) files.push(p)
  })
walk(root)

const used = new Set()
const dynamic = new Set()
for (const f of files) {
  const src = readFileSync(f, 'utf8')
  for (const m of src.matchAll(/\bt\(\s*'([a-zA-Z0-9_.]+)'/g)) used.add(m[1])
  for (const m of src.matchAll(/\b(?:titleKey|key)[=:]\s*['"]([a-z][a-zA-Z0-9_]*\.[a-zA-Z0-9_.]+)['"]/g)) used.add(m[1])
  for (const m of src.matchAll(/'((?:[a-z][a-zA-Z]*\.)+[a-zA-Z_]+)'/g)) if (/^(register|dashboard|transfer|ops|errors|notification)\./.test(m[1])) used.add(m[1])
  for (const m of src.matchAll(/\bt\(\s*`([a-zA-Z0-9_.]+)\.\$\{/g)) dynamic.add(m[1])
}
const flat = (o, p = '') => Object.entries(o).flatMap(([k, v]) => (typeof v === 'object' ? flat(v, `${p}${k}.`) : [`${p}${k}`]))
const en = new Set(flat(JSON.parse(readFileSync(join(root, 'i18n/en.json'), 'utf8'))))
const vi = new Set(flat(JSON.parse(readFileSync(join(root, 'i18n/vi.json'), 'utf8'))))
const has = (set, k) => set.has(k) || set.has(`${k}_one`) || set.has(`${k}_other`) || [...set].some((x) => x.startsWith(`${k}.`))
const missEn = [...used].filter((k) => !has(en, k))
const missVi = [...used].filter((k) => !has(vi, k))
const onlyEn = [...en].filter((k) => !vi.has(k))
const onlyVi = [...vi].filter((k) => !en.has(k))
console.log('missing in en:', missEn.join(' '))
console.log('missing in vi:', missVi.join(' '))
console.log('only in en:', onlyEn)
console.log('only in vi:', onlyVi)
console.log('dynamic prefixes:', [...dynamic].filter((p) => !has(en, p)))
process.exit(missEn.length || missVi.length || onlyEn.length || onlyVi.length ? 1 : 0)

import { useMemo } from 'react'

/**
 * Banknote-style guilloché line art, generated from a seed string (e.g. an account number)
 * so each account gets its own pattern while staying visually consistent.
 */
type Props = {
  seed: string
  variant?: 'band' | 'rosette'
  className?: string
}

function hash(seed: string): number {
  let h = 2166136261
  for (let i = 0; i < seed.length; i++) {
    h ^= seed.charCodeAt(i)
    h = Math.imul(h, 16777619)
  }
  return h >>> 0
}

function rng(seed: string) {
  let s = hash(seed) || 1
  return () => {
    s ^= s << 13
    s ^= s >>> 17
    s ^= s << 5
    return (s >>> 0) / 4294967296
  }
}

function bandPaths(seed: string, width: number, height: number): string[] {
  const rand = rng(seed)
  const f1 = 2 + Math.floor(rand() * 3)
  const f2 = 7 + Math.floor(rand() * 5)
  const amp = height * (0.18 + rand() * 0.1)
  const lines = 22
  const paths: string[] = []
  for (let family = 0; family < 2; family++) {
    for (let i = 0; i < lines; i++) {
      const phase = (i / lines) * Math.PI * 2 + family * Math.PI
      let d = ''
      for (let x = 0; x <= width; x += 4) {
        const t = (x / width) * Math.PI * 2
        const y =
          height / 2 +
          amp * Math.sin(f1 * t + phase) +
          amp * 0.35 * Math.sin(f2 * t - phase * 0.5)
        d += `${x === 0 ? 'M' : 'L'}${x} ${y.toFixed(2)}`
      }
      paths.push(d)
    }
  }
  return paths
}

function rosettePaths(seed: string, size: number): string[] {
  const rand = rng(seed)
  const c = size / 2
  const petals = 9 + Math.floor(rand() * 6)
  const rings = 26
  const paths: string[] = []
  for (let k = 0; k < rings; k++) {
    const base = size * (0.16 + (k / rings) * 0.3)
    const amp = size * 0.055
    const phase = (k / rings) * Math.PI * (1 + rand() * 0.2)
    let d = ''
    const steps = 360
    for (let s = 0; s <= steps; s++) {
      const t = (s / steps) * Math.PI * 2
      const r = base + amp * Math.sin(petals * t + phase) + amp * 0.4 * Math.sin(petals * 3 * t - phase)
      const x = c + r * Math.cos(t)
      const y = c + r * Math.sin(t)
      d += `${s === 0 ? 'M' : 'L'}${x.toFixed(2)} ${y.toFixed(2)}`
    }
    paths.push(d + 'Z')
  }
  return paths
}

export function Guilloche({ seed, variant = 'band', className }: Props) {
  const width = variant === 'band' ? 600 : 400
  const height = variant === 'band' ? 120 : 400
  const paths = useMemo(
    () => (variant === 'band' ? bandPaths(seed, width, height) : rosettePaths(seed, width)),
    [seed, variant, width, height],
  )
  return (
    <svg
      aria-hidden="true"
      focusable="false"
      viewBox={`0 0 ${width} ${height}`}
      preserveAspectRatio={variant === 'band' ? 'none' : 'xMidYMid meet'}
      className={className}
    >
      <g fill="none" stroke="currentColor" strokeWidth={0.6} vectorEffect="non-scaling-stroke">
        {paths.map((d, i) => (
          <path key={i} d={d} vectorEffect="non-scaling-stroke" />
        ))}
      </g>
    </svg>
  )
}

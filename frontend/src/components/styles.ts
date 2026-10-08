// Shared class builders for form controls (kept out of component files for fast refresh).

export type Variant = 'primary' | 'secondary' | 'ghost' | 'danger'
export type Size = 'sm' | 'md'

const VARIANTS: Record<Variant, string> = {
  primary: 'bg-vault text-on-vault hover:bg-vault-deep border border-transparent',
  secondary: 'bg-sheet text-ink border border-rule hover:border-ink-2/60',
  ghost: 'text-vault hover:bg-vault-tint border border-transparent',
  danger: 'bg-debit text-on-debit border border-transparent hover:opacity-90',
}
const SIZES: Record<Size, string> = {
  sm: 'h-9 px-3 text-[13px] gap-1.5',
  md: 'h-11 px-5 gap-2',
}

export function buttonClass(variant: Variant = 'primary', size: Size = 'md', extra = '') {
  return `inline-flex shrink-0 cursor-pointer items-center justify-center rounded-sheet font-medium whitespace-nowrap transition-colors duration-150 disabled:cursor-not-allowed disabled:opacity-60 ${VARIANTS[variant]} ${SIZES[size]} ${extra}`
}

export function inputClass(invalid = false, extra = '') {
  return `h-11 w-full min-w-0 rounded-sheet border bg-sheet px-3 text-[15px] text-ink placeholder:text-ink-2/70 outline-none transition-colors duration-150 focus-visible:outline-2 focus-visible:outline-offset-0 disabled:opacity-60 ${
    invalid ? 'border-debit' : 'border-rule hover:border-ink-2/60 focus:border-vault'
  } ${extra}`
}


import type { ReactNode } from 'react'

export function PageHeader({ title, eyebrow, actions, children }: { title: ReactNode; eyebrow?: ReactNode; actions?: ReactNode; children?: ReactNode }) {
  return (
    <header className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
      <div className="min-w-0">
        {eyebrow && <p className="mb-1 text-[12px] font-medium tracking-wider text-ink-2 uppercase">{eyebrow}</p>}
        <h1 className="font-display text-[24px] leading-tight font-semibold tracking-tight text-balance sm:text-[28px]">{title}</h1>
        {children && <div className="mt-1.5 text-ink-2">{children}</div>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </header>
  )
}

/** A ledger sheet: white card with a 1 px rule. Optional heading row with actions. */
export function Sheet({ title, actions, children, className = '', bodyClassName = '', as: Tag = 'section' }: {
  title?: ReactNode
  actions?: ReactNode
  children: ReactNode
  className?: string
  bodyClassName?: string
  as?: 'section' | 'div' | 'aside'
}) {
  return (
    <Tag className={`min-w-0 rounded-sheet border border-rule bg-sheet ${className}`}>
      {title && (
        <div className="flex flex-wrap items-center justify-between gap-2 px-5 pt-4 sm:px-6">
          <h2 className="font-display text-base font-semibold">{title}</h2>
          {actions}
        </div>
      )}
      <div className={`px-5 py-4 sm:px-6 ${bodyClassName}`}>{children}</div>
    </Tag>
  )
}

/** Definition list in two columns, used for detail pages. */
export function Facts({ items, className = '' }: { items: [ReactNode, ReactNode][]; className?: string }) {
  return (
    <dl className={`grid grid-cols-1 gap-x-6 sm:grid-cols-[minmax(9rem,auto)_minmax(0,1fr)] ${className}`}>
      {items.map(([k, v], i) => (
        <div key={i} className="contents">
          <dt className="pt-2.5 text-[13px] text-ink-2 sm:border-b sm:border-rule sm:pb-2.5">{k}</dt>
          <dd className="min-w-0 border-b border-rule pb-2.5 break-words sm:pt-2.5">{v}</dd>
        </div>
      ))}
    </dl>
  )
}

export function Wordmark({ tone = 'default', sub }: { tone?: 'default' | 'console'; sub?: string }) {
  return (
    <span className="inline-flex items-center gap-2 font-display text-lg font-semibold tracking-tight">
      <svg width="22" height="22" viewBox="0 0 22 22" aria-hidden="true">
        <rect x="0.5" y="0.5" width="21" height="21" rx="4" className={tone === 'console' ? 'fill-on-console' : 'fill-vault'} />
        <path d="M5 8h12M5 11h12M5 14h7" className={tone === 'console' ? 'stroke-console' : 'stroke-on-vault'} strokeWidth="1.4" />
      </svg>
      <span className="flex flex-col leading-none">
        <span>SecureBank</span>
        {sub && <span className="figures mt-1 text-[10px] font-medium tracking-[0.2em] opacity-70">{sub}</span>}
      </span>
    </span>
  )
}

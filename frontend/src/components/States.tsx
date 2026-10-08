import { RotateCw } from 'lucide-react'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { ApiError, normalizeError } from '../api/errors'
import { errorText } from '../utils/errorMessage'
import { Button } from './Button'

export function Skeleton({ className = '' }: { className?: string }) {
  return <span aria-hidden className={`block animate-pulse rounded-[3px] bg-rule/70 ${className}`} />
}

/** Several skeleton lines that mimic ledger rows. */
export function SkeletonRows({ rows = 5, label }: { rows?: number; label?: string }) {
  const { t } = useTranslation()
  return (
    <div role="status" aria-label={label ?? t('common.loading')} className="divide-y divide-rule">
      {Array.from({ length: rows }, (_, i) => (
        <div key={i} className="flex items-center gap-4 py-3.5">
          <Skeleton className="h-3 w-14" />
          <Skeleton className="h-3 flex-1" />
          <Skeleton className="h-3 w-24" />
        </div>
      ))}
    </div>
  )
}

export function EmptyState({ title, body, action, compact = false }: { title: string; body?: ReactNode; action?: ReactNode; compact?: boolean }) {
  return (
    <div className={`flex flex-col items-start gap-1 border-t border-dashed border-rule ${compact ? 'py-6' : 'py-10'}`}>
      <p className="font-display text-base font-semibold">{title}</p>
      {body && <p className="max-w-[52ch] text-ink-2">{body}</p>}
      {action && <div className="mt-3">{action}</div>}
    </div>
  )
}

export function ErrorState({ error, onRetry, compact = false }: { error: unknown; onRetry?: () => void; compact?: boolean }) {
  const { t } = useTranslation()
  const e = error instanceof ApiError ? error : normalizeError(error)
  return (
    <div role="alert" className={`flex flex-col items-start gap-1 border-l-2 border-debit pl-4 ${compact ? 'py-3' : 'py-6'}`}>
      <p className="font-medium">{t('common.loadFailed')}</p>
      <p className="max-w-[60ch] text-ink-2">{errorText(t, e)}</p>
      {e.correlationId && (
        <p className="figures mt-1 text-[11px] text-ink-2">
          {t('common.correlationId')}: {e.correlationId}
        </p>
      )}
      {onRetry && (
        <Button variant="secondary" size="sm" className="mt-3" icon={<RotateCw size={14} strokeWidth={1.5} aria-hidden />} onClick={onRetry}>
          {t('common.retry')}
        </Button>
      )}
    </div>
  )
}

/** Inline alert box used inside forms. */
export function Alert({ tone = 'error', children, id }: { tone?: 'error' | 'warning' | 'info' | 'success'; children: ReactNode; id?: string }) {
  const rule = { error: 'border-debit', warning: 'border-brass', info: 'border-vault', success: 'border-credit' }[tone]
  return (
    <div id={id} role={tone === 'error' ? 'alert' : 'status'} className={`border-l-2 ${rule} bg-paper px-3.5 py-3 text-[13px]`}>
      {children}
    </div>
  )
}

import { ChevronLeft, ChevronRight } from 'lucide-react'
import { useTranslation } from 'react-i18next'

type Props = { page: number; size: number; totalElements: number; totalPages: number; onChange: (page: number) => void }

export function Pagination({ page, size, totalElements, totalPages, onChange }: Props) {
  const { t } = useTranslation()
  if (totalElements === 0) return null
  const from = page * size + 1
  const to = Math.min(totalElements, (page + 1) * size)
  const btn =
    'inline-flex size-9 cursor-pointer items-center justify-center rounded-sheet border border-rule bg-sheet text-ink-2 hover:text-ink disabled:cursor-not-allowed disabled:opacity-40'
  return (
    <nav aria-label={t('pagination.label')} className="flex flex-wrap items-center justify-between gap-3 pt-4">
      <p className="figures text-[12px] text-ink-2">{t('pagination.range', { from, to, total: totalElements })}</p>
      <div className="flex items-center gap-2">
        <button type="button" className={btn} disabled={page === 0} onClick={() => onChange(page - 1)} aria-label={t('pagination.prev')}>
          <ChevronLeft size={16} strokeWidth={1.5} aria-hidden />
        </button>
        <span className="figures min-w-16 text-center text-[12px]" aria-current="page">
          {page + 1} / {Math.max(1, totalPages)}
        </span>
        <button type="button" className={btn} disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)} aria-label={t('pagination.next')}>
          <ChevronRight size={16} strokeWidth={1.5} aria-hidden />
        </button>
      </div>
    </nav>
  )
}

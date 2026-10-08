import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Money } from '../../components/Money'
import { Skeleton } from '../../components/States'
import type { TransactionSummary } from '../../types/api'
import { formatDayKey } from '../../utils/format'
import { buildDays } from './flow'

const W = 300
const H = 84
const MID = 46 // baseline: money in rises above, money out hangs below

/**
 * 30-day in/out. Direction is encoded by position (above/below the baseline) first and colour second,
 * because credit-green vs debit-red alone fails colour-vision separation.
 */
export function FlowSummary({ items, loading, days = 30 }: { items: TransactionSummary[] | undefined; loading: boolean; days?: number }) {
  const { t, i18n } = useTranslation()
  const [hover, setHover] = useState<number | null>(null)
  const data = useMemo(() => buildDays(items ?? [], days), [items, days])
  const totalIn = data.reduce((s, d) => s + d.inflow, 0)
  const totalOut = data.reduce((s, d) => s + d.outflow, 0)
  const max = Math.max(1, ...data.map((d) => Math.max(d.inflow, d.outflow)))
  const slot = W / days
  const bw = Math.max(2, slot - 2) // 2 px gap between bars
  const up = MID - 6
  const down = H - MID - 4
  const h = hover !== null ? data[hover] : null

  return (
    <div>
      <dl className="space-y-1.5">
        <div className="flex items-baseline justify-between gap-3">
          <dt className="flex items-center gap-2 text-ink-2">
            <span aria-hidden className="inline-block h-2.5 w-1 rounded-t-[1px] bg-credit" />
            {t('flow.in')}
          </dt>
          <dd>{loading ? <Skeleton className="h-4 w-28" /> : <Money value={totalIn} sign="+" className="text-[15px]" />}</dd>
        </div>
        <div className="flex items-baseline justify-between gap-3">
          <dt className="flex items-center gap-2 text-ink-2">
            <span aria-hidden className="inline-block h-2.5 w-1 rounded-b-[1px] bg-debit" />
            {t('flow.out')}
          </dt>
          <dd>{loading ? <Skeleton className="h-4 w-28" /> : <Money value={totalOut} sign="-" className="text-[15px]" />}</dd>
        </div>
        <div className="flex items-baseline justify-between gap-3 border-t border-rule pt-1.5">
          <dt className="text-ink-2">{t('flow.net')}</dt>
          <dd>{loading ? <Skeleton className="h-4 w-24" /> : <Money value={totalIn - totalOut} sign="auto" className="text-[15px] font-medium" />}</dd>
        </div>
      </dl>

      <div className="relative mt-4">
        <svg viewBox={`0 0 ${W} ${H}`} className="block h-[84px] w-full" role="img" aria-label={t('flow.chartAria', { days })} onMouseLeave={() => setHover(null)}>
          <line x1={0} x2={W} y1={MID} y2={MID} className="stroke-rule" strokeWidth={1} vectorEffect="non-scaling-stroke" />
          {!loading &&
            data.map((d, i) => {
              const x = i * slot + (slot - bw) / 2
              const hi = (d.inflow / max) * up
              const ho = (d.outflow / max) * down
              const dim = hover !== null && hover !== i ? 0.35 : 1
              return (
                <g key={d.day} opacity={dim}>
                  {hi > 0 && <path d={roundTop(x, MID - 1, bw, Math.max(1.5, hi))} className="fill-credit" />}
                  {ho > 0 && <path d={roundBottom(x, MID + 1, bw, Math.max(1.5, ho))} className="fill-debit" />}
                  {/* hit target wider than the mark */}
                  <rect x={i * slot} y={0} width={slot} height={H} fill="transparent" onMouseEnter={() => setHover(i)} />
                </g>
              )
            })}
        </svg>
        <div className="figures mt-1 flex justify-between text-[10px] text-ink-2">
          <span>{data[0] ? formatDayKey(data[0].day, i18n.language) : ''}</span>
          <span>{t('flow.today')}</span>
        </div>
        {h && (
          <div
            className="pointer-events-none absolute -top-2 z-10 -translate-y-full rounded-sheet border border-rule bg-sheet px-3 py-2 text-[12px] whitespace-nowrap shadow-[0_6px_16px_-10px_rgb(19_32_30/0.4)]"
            style={{ left: `clamp(0px, calc(${((hover! + 0.5) / days) * 100}% - 70px), calc(100% - 150px))` }}
          >
            <p className="figures mb-1 text-ink-2">{formatDayKey(h.day, i18n.language)}</p>
            <p className="flex justify-between gap-4">
              <span>{t('flow.in')}</span>
              <Money value={h.inflow} sign="+" />
            </p>
            <p className="flex justify-between gap-4">
              <span>{t('flow.out')}</span>
              <Money value={h.outflow} sign="-" />
            </p>
          </div>
        )}
      </div>

      {/* Table view for assistive tech */}
      <table className="sr-only">
        <caption>{t('flow.chartAria', { days })}</caption>
        <thead>
          <tr>
            <th scope="col">{t('flow.date')}</th>
            <th scope="col">{t('flow.in')}</th>
            <th scope="col">{t('flow.out')}</th>
          </tr>
        </thead>
        <tbody>
          {data
            .filter((d) => d.inflow || d.outflow)
            .map((d) => (
              <tr key={d.day}>
                <th scope="row">{formatDayKey(d.day, i18n.language)}</th>
                <td>{d.inflow}</td>
                <td>{d.outflow}</td>
              </tr>
            ))}
        </tbody>
      </table>
    </div>
  )
}

function roundTop(x: number, base: number, w: number, h: number) {
  const r = Math.min(1.5, w / 2, h)
  return `M${x} ${base}V${base - h + r}Q${x} ${base - h} ${x + r} ${base - h}H${x + w - r}Q${x + w} ${base - h} ${x + w} ${base - h + r}V${base}Z`
}
function roundBottom(x: number, base: number, w: number, h: number) {
  const r = Math.min(1.5, w / 2, h)
  return `M${x} ${base}V${base + h - r}Q${x} ${base + h} ${x + r} ${base + h}H${x + w - r}Q${x + w} ${base + h} ${x + w} ${base + h - r}V${base}Z`
}

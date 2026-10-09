import { useTranslation } from 'react-i18next'
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { Money } from '../../components/Money'
import type { DailyStat } from '../../types/api'
import { formatCompactVnd, formatDayKey } from '../../utils/format'

type TipProps = { active?: boolean; payload?: readonly { payload: DailyStat }[] }

function ChartTip({ active, payload }: TipProps) {
  const { t, i18n } = useTranslation()
  const lang = i18n.language
  const d = payload?.[0]?.payload
  if (!active || !d) return null
  return (
    <div className="rounded-sheet border border-rule bg-sheet px-3 py-2 text-[12px] shadow-[0_6px_16px_-10px_rgb(19_32_30/0.4)]">
      <p className="figures mb-1 text-ink-2">{formatDayKey(d.date, lang)}</p>
      <p className="flex justify-between gap-6">
        <span>{t('ops.dashboard.volume')}</span>
        <Money value={d.amount} />
      </p>
      <p className="flex justify-between gap-6">
        <span>{t('ops.dashboard.count')}</span>
        <span className="figures">{d.count}</span>
      </p>
    </div>
  )
}

/**
 * Posted volume per day. One measure on one axis (VND); the transaction count rides in the tooltip
 * and the table view instead of a second axis. Colours come from the design tokens, not Recharts defaults.
 */
export function VolumeChart({ data }: { data: DailyStat[] }) {
  const { t, i18n } = useTranslation()
  const lang = i18n.language

  return (
    <div>
      <div className="h-64 w-full" aria-hidden>
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={data} margin={{ top: 8, right: 4, bottom: 0, left: 0 }} barCategoryGap="28%">
            <CartesianGrid vertical={false} stroke="var(--rule)" strokeDasharray="0" />
            <XAxis
              dataKey="date"
              tickFormatter={(d: string) => formatDayKey(d, lang)}
              tick={{ fill: 'var(--ink-2)', fontSize: 11, fontFamily: 'IBM Plex Mono' }}
              tickLine={false}
              axisLine={{ stroke: 'var(--ink)' }}
              interval="preserveStartEnd"
              minTickGap={18}
            />
            <YAxis
              tickFormatter={(v: number) => formatCompactVnd(v, lang)}
              tick={{ fill: 'var(--ink-2)', fontSize: 11, fontFamily: 'IBM Plex Mono' }}
              tickLine={false}
              axisLine={false}
              width={52}
            />
            <Tooltip content={<ChartTip />} cursor={{ fill: 'var(--vault-tint)' }} isAnimationActive={false} />
            <Bar dataKey="amount" fill="var(--vault)" radius={[4, 4, 0, 0]} maxBarSize={30} isAnimationActive={false} />
          </BarChart>
        </ResponsiveContainer>
      </div>
      <table className="sr-only">
        <caption>{t('ops.dashboard.chartTitle')}</caption>
        <thead>
          <tr>
            <th scope="col">{t('flow.date')}</th>
            <th scope="col">{t('ops.dashboard.volume')}</th>
            <th scope="col">{t('ops.dashboard.count')}</th>
          </tr>
        </thead>
        <tbody>
          {data.map((d) => (
            <tr key={d.date}>
              <th scope="row">{formatDayKey(d.date, lang)}</th>
              <td>{d.amount}</td>
              <td>{d.count}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

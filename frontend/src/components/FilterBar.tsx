import { SlidersHorizontal } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { AmountInput } from './AmountInput'
import { Button } from './Button'
import { Field, Input, Select } from './Field'

export type FilterDef = {
  name: string
  label: string
  type: 'text' | 'select' | 'date' | 'amount'
  options?: { value: string; label: string }[]
  placeholder?: string
  mono?: boolean
}

type Props = {
  defs: FilterDef[]
  values: Record<string, string>
  onApply: (values: Record<string, string>) => void
  label: string
}

/** Filters edited as a draft and applied together (Enter or "Apply"); the page stores them in the URL. */
export function FilterBar({ defs, values, onApply, label }: Props) {
  const { t } = useTranslation()
  const [draft, setDraft] = useState(values)
  const [synced, setSynced] = useState(values)
  // Follow external changes (back/forward, "clear") without an effect.
  if (JSON.stringify(values) !== JSON.stringify(synced)) {
    setSynced(values)
    setDraft(values)
  }
  const activeCount = Object.values(values).filter(Boolean).length
  const active = activeCount > 0
  // Below 640 px the filters fold behind a toggle so the results stay on the first screen.
  const [open, setOpen] = useState(false)

  function submit(e: FormEvent) {
    e.preventDefault()
    onApply(draft)
  }

  return (
    <form role="search" aria-label={label} onSubmit={submit} className="mb-5 rounded-sheet border border-rule bg-sheet px-4 py-3 sm:px-5 sm:py-4">
      <button
        type="button"
        aria-expanded={open}
        onClick={() => setOpen((o) => !o)}
        className="flex min-h-9 w-full cursor-pointer items-center gap-2 text-left font-medium sm:hidden"
      >
        <SlidersHorizontal size={15} strokeWidth={1.5} aria-hidden />
        {label}
        {active && <span className="figures ml-auto text-[12px] text-vault">{activeCount}</span>}
      </button>
      <div className={`${open ? 'mt-3 block' : 'hidden'} sm:mt-0 sm:block`}>
      <div className="grid grid-cols-1 gap-x-4 gap-y-3 sm:grid-cols-2 lg:grid-cols-[repeat(auto-fill,minmax(160px,1fr))]">
        {defs.map((d) => (
          <Field key={d.name} label={<span className="text-[12px] text-ink-2">{d.label}</span>}>
            {(s) => {
              const common = { ...s, value: draft[d.name] ?? '' }
              if (d.type === 'select')
                return (
                  <Select {...common} onChange={(e) => setDraft({ ...draft, [d.name]: e.target.value })} className="h-10 text-[14px]">
                    <option value="">{t('common.all')}</option>
                    {d.options?.map((o) => (
                      <option key={o.value} value={o.value}>
                        {o.label}
                      </option>
                    ))}
                  </Select>
                )
              if (d.type === 'amount')
                return <AmountInput {...common} value={draft[d.name] ?? ''} onValueChange={(v) => setDraft({ ...draft, [d.name]: v })} className="h-10 text-[14px]" placeholder={d.placeholder} />
              return (
                <Input
                  {...common}
                  type={d.type === 'date' ? 'date' : 'text'}
                  placeholder={d.placeholder}
                  onChange={(e) => setDraft({ ...draft, [d.name]: e.target.value })}
                  className={`h-10 text-[14px] ${d.mono ? 'figures' : ''}`}
                />
              )
            }}
          </Field>
        ))}
      </div>
      <div className="mt-4 flex flex-wrap items-center justify-end gap-2">
        {active && (
          <Button
            variant="ghost"
            size="sm"
            onClick={() => {
              const empty = Object.fromEntries(defs.map((d) => [d.name, '']))
              setDraft(empty)
              onApply(empty)
            }}
          >
            {t('common.reset')}
          </Button>
        )}
        <Button type="submit" size="sm">
          {t('common.apply')}
        </Button>
      </div>
      </div>
    </form>
  )
}

import { useLayoutEffect, useRef, type InputHTMLAttributes } from 'react'
import { groupDigits } from '../utils/format'
import { inputClass } from './styles'

type Props = Omit<InputHTMLAttributes<HTMLInputElement>, 'value' | 'onChange' | 'type'> & {
  /** Digits only, e.g. "1500000". */
  value: string
  onValueChange: (digits: string) => void
}

/**
 * VND amount field: groups thousands with "." as you type (1.500.000) and keeps the caret
 * after the same digit it was after, so editing in the middle doesn't jump to the end.
 */
export function AmountInput({ value, onValueChange, className = '', ...rest }: Props) {
  const ref = useRef<HTMLInputElement>(null)
  const caretDigits = useRef<number | null>(null)
  const display = groupDigits(value)

  useLayoutEffect(() => {
    const el = ref.current
    const n = caretDigits.current
    if (!el || n === null || document.activeElement !== el) return
    let pos = 0
    let seen = 0
    while (pos < display.length && seen < n) {
      if (/\d/.test(display[pos])) seen++
      pos++
    }
    el.setSelectionRange(pos, pos)
    caretDigits.current = null
  }, [display])

  return (
    <div className="relative">
      <input
        ref={ref}
        type="text"
        inputMode="numeric"
        autoComplete="off"
        value={display}
        onChange={(e) => {
          const raw = e.target.value
          const caret = e.target.selectionStart ?? raw.length
          caretDigits.current = raw.slice(0, caret).replace(/\D/g, '').replace(/^0+/, '').length
          onValueChange(raw.replace(/\D/g, '').replace(/^0+(?=\d)/, '').slice(0, 15))
        }}
        className={inputClass(rest['aria-invalid'] === true, `figures pr-10 text-right text-[18px] ${className}`)}
        {...rest}
      />
      <span aria-hidden className="figures pointer-events-none absolute inset-y-0 right-3 flex items-center text-ink-2">
        ₫
      </span>
    </div>
  )
}

import { Eye, EyeOff } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { formatAccountNumber, maskAccountNumber } from '../utils/format'

type Props = { value: string; masked?: boolean; revealable?: boolean; className?: string }

/** "1000 0000 01", or masked "•••• 0001" with an optional reveal toggle for the owner. */
export function AccountNumber({ value, masked = false, revealable = false, className = '' }: Props) {
  const { t } = useTranslation()
  const [shown, setShown] = useState(!masked)
  const text = shown && !value.includes('*') ? formatAccountNumber(value) : maskAccountNumber(value)
  return (
    <span className={`figures inline-flex items-center gap-1.5 whitespace-nowrap ${className}`}>
      <span>{text}</span>
      {revealable && masked && !value.includes('*') && (
        <button
          type="button"
          onClick={(e) => {
            e.stopPropagation()
            setShown((s) => !s)
          }}
          aria-label={shown ? t('account.hideNumber') : t('account.showNumber')}
          aria-pressed={shown}
          className="inline-flex size-7 cursor-pointer items-center justify-center rounded-[4px] opacity-70 hover:opacity-100"
        >
          {shown ? <EyeOff size={14} strokeWidth={1.5} aria-hidden /> : <Eye size={14} strokeWidth={1.5} aria-hidden />}
        </button>
      )}
    </span>
  )
}

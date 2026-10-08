import { inputClass } from './styles'
import { useId, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes } from 'react'

type Slot = { id: string; 'aria-invalid': boolean | undefined; 'aria-describedby': string | undefined }

type FieldProps = {
  label: ReactNode
  hint?: ReactNode
  error?: string | null
  /** Extra live content under the input (e.g. beneficiary lookup result). */
  status?: ReactNode
  optional?: string
  className?: string
  children: (slot: Slot) => ReactNode
}

/** Label + control + helper + inline error, wired with ids for assistive tech. */
export function Field({ label, hint, error, status, optional, className = '', children }: FieldProps) {
  const id = useId()
  const hintId = `${id}-hint`
  const errId = `${id}-err`
  const statusId = `${id}-status`
  const describedBy = [hint && hintId, status && statusId, error && errId].filter(Boolean).join(' ') || undefined
  return (
    <div className={className}>
      <label htmlFor={id} className="mb-1.5 flex items-baseline justify-between gap-2 font-medium">
        <span>{label}</span>
        {optional && <span className="text-[12px] font-normal text-ink-2">{optional}</span>}
      </label>
      {children({ id, 'aria-invalid': error ? true : undefined, 'aria-describedby': describedBy })}
      {status && (
        <div id={statusId} aria-live="polite" className="mt-1.5 text-[13px]">
          {status}
        </div>
      )}
      {hint && !error && (
        <p id={hintId} className="mt-1.5 text-[13px] text-ink-2">
          {hint}
        </p>
      )}
      {error && (
        <p id={errId} className="mt-1.5 text-[13px] text-debit">
          {error}
        </p>
      )}
    </div>
  )
}

export function Input({ className = '', ...rest }: InputHTMLAttributes<HTMLInputElement>) {
  return <input className={inputClass(rest['aria-invalid'] === true, className)} {...rest} />
}

export function Select({ className = '', children, ...rest }: SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <select className={inputClass(rest['aria-invalid'] === true, `cursor-pointer pr-8 ${className}`)} {...rest}>
      {children}
    </select>
  )
}

export function Textarea({ className = '', ...rest }: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return <textarea className={inputClass(rest['aria-invalid'] === true, `h-auto min-h-24 py-2.5 ${className}`)} {...rest} />
}

import { ListOrdered, Printer, Send } from 'lucide-react'
import { useEffect, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { Button, ButtonLink } from '../../components/Button'
import { LedgerSlip } from '../../components/LedgerSlip'
import { Alert } from '../../components/States'
import type { TransferResponse } from '../../types/api'
import { slipFromTransfer } from '../transactions/slip'

type Props = { result: TransferResponse; senderName: string; replayed: boolean; onNew: () => void }

/** Success state: the double-entry slip with the stamp landing, plus print / new transfer / activity. */
export function TransferReceipt({ result, senderName, replayed, onNew }: Props) {
  const { t } = useTranslation()
  const heading = useRef<HTMLHeadingElement>(null)
  useEffect(() => heading.current?.focus(), [])

  return (
    <div className="mx-auto max-w-2xl">
      <div className="no-print mb-5" aria-live="polite">
        <p className="text-[12px] font-medium tracking-wider text-credit uppercase">{t('transfer.receipt.eyebrow')}</p>
        <h1 ref={heading} tabIndex={-1} className="mt-1 font-display text-[24px] leading-tight font-semibold tracking-tight outline-none sm:text-[28px]">
          {t('transfer.receipt.title', { name: result.destinationHolderName })}
        </h1>
        {replayed && (
          <div className="mt-3">
            <Alert tone="info">{t('transfer.receipt.replayed')}</Alert>
          </div>
        )}
      </div>
      <div className="print-area">
        <LedgerSlip data={slipFromTransfer(result, senderName)} landStamp />
      </div>
      <div className="no-print mt-5 flex flex-wrap gap-2">
        <Button variant="secondary" icon={<Printer size={15} strokeWidth={1.5} aria-hidden />} onClick={() => window.print()}>
          {t('transfer.receipt.print')}
        </Button>
        <Button variant="secondary" icon={<Send size={15} strokeWidth={1.5} aria-hidden />} onClick={onNew}>
          {t('transfer.receipt.another')}
        </Button>
        <ButtonLink to={`/transactions/${result.transactionId}`} variant="ghost" icon={<ListOrdered size={15} strokeWidth={1.5} aria-hidden />}>
          {t('transfer.receipt.view')}
        </ButtonLink>
      </div>
    </div>
  )
}

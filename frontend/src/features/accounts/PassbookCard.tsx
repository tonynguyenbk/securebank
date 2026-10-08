import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { AccountNumber } from '../../components/AccountNumber'
import { Guilloche } from '../../components/Guilloche'
import { Money } from '../../components/Money'
import { AccountStatusTag } from '../../components/Status'
import type { AccountSummary } from '../../types/api'
import { formatDate } from '../../utils/format'

/** Passbook-style account card: guilloché band seeded from the account number, big mono balance. */
export function PassbookCard({ account, holder, linkTo = true }: { account: AccountSummary; holder?: string; linkTo?: boolean }) {
  const { t, i18n } = useTranslation()
  const frozen = account.status === 'FROZEN'
  const body = (
    <>
      <div className="relative h-16 overflow-hidden border-b border-rule text-vault">
        <Guilloche seed={account.accountNumber} className="absolute inset-0 h-full w-full opacity-30" />
        <div className="relative flex h-full items-start justify-between px-5 pt-3 sm:px-6">
          <span className="text-[11px] font-semibold tracking-[0.18em] text-ink-2 uppercase">{t('account.passbook')}</span>
          <span className="figures text-[11px] text-ink-2">VND</span>
        </div>
      </div>
      <div className="flex flex-1 flex-col px-5 pt-4 pb-5 sm:px-6">
        <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-1">
          <p className="text-[13px] text-ink-2">
            {t('account.type.CURRENT')} · <AccountNumber value={account.accountNumber} className="text-ink" />
          </p>
          <AccountStatusTag status={account.status} />
        </div>
        {holder && <p className="mt-0.5 text-[13px] text-ink-2">{holder}</p>}
        <p className="mt-5 text-[12px] text-ink-2">{t('account.available')}</p>
        <Money value={account.balance} className="self-start text-[28px] leading-tight font-medium tracking-tight sm:text-[32px]" />
        {frozen ? (
          <p className="mt-auto pt-3 text-[13px] text-debit">{t('account.frozenNote')}</p>
        ) : (
          <p className="figures mt-auto pt-3 text-[11px] text-ink-2">{t('account.openedOn', { date: formatDate(account.createdAt, i18n.language) })}</p>
        )}
      </div>
    </>
  )
  const cls = `flex flex-col overflow-hidden rounded-sheet border bg-sheet ${frozen ? 'border-debit/60' : 'border-rule'}`
  return linkTo ? (
    <Link to={`/accounts/${account.id}`} className={`${cls} transition-colors duration-150 hover:border-vault`} aria-label={t('account.openDetail', { number: account.accountNumber })}>
      {body}
    </Link>
  ) : (
    <div className={cls}>{body}</div>
  )
}

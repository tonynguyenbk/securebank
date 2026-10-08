import { ArrowLeft, Send } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router-dom'
import { ApiError } from '../../api/errors'
import { useAuth } from '../../auth/useAuth'
import { AccountNumber } from '../../components/AccountNumber'
import { ButtonLink } from '../../components/Button'
import { Guilloche } from '../../components/Guilloche'
import { Facts, Sheet } from '../../components/Layout'
import { Money } from '../../components/Money'
import { Alert, ErrorState, Skeleton } from '../../components/States'
import { AccountStatusTag } from '../../components/Status'
import { StatementTable } from '../../features/accounts/StatementTable'
import { useAccount } from '../../features/accounts/hooks'
import { LimitMeter } from '../../features/transfers/LimitMeter'
import { formatDate, formatDateTime } from '../../utils/format'
import { MissingRecord } from '../StatusPages'

export function AccountDetailPage() {
  const { id } = useParams()
  const { t, i18n } = useTranslation()
  const { user } = useAuth()
  const q = useAccount(id)

  if (q.isError && q.error instanceof ApiError && (q.error.status === 404 || q.error.status === 403)) {
    return <MissingRecord backTo="/accounts" backLabel={t('accounts.back')} />
  }

  return (
    <div>
      <Link to="/accounts" className="mb-4 inline-flex items-center gap-1.5 text-[13px] text-vault underline-offset-4 hover:underline">
        <ArrowLeft size={14} strokeWidth={1.5} aria-hidden /> {t('accounts.back')}
      </Link>
      {q.isPending && <Skeleton className="h-60 w-full" />}
      {q.isError && <ErrorState error={q.error} onRetry={() => q.refetch()} />}
      {q.data && (
        <div className="space-y-8">
          <section className="overflow-hidden rounded-sheet border border-rule bg-sheet">
            <div className="relative h-14 overflow-hidden border-b border-rule text-vault">
              <Guilloche seed={q.data.accountNumber} className="absolute inset-0 h-full w-full opacity-30" />
            </div>
            <div className="grid gap-6 px-5 py-5 sm:px-7 lg:grid-cols-[minmax(0,1.2fr)_minmax(0,1fr)]">
              <div>
                <p className="text-[12px] font-medium tracking-wider text-ink-2 uppercase">{t('account.type.CURRENT')}</p>
                <h1 className="mt-1 font-display text-[24px] leading-tight font-semibold">
                  <AccountNumber value={q.data.accountNumber} />
                </h1>
                <p className="mt-0.5 text-ink-2">{user?.fullName}</p>
                <p className="mt-5 text-[12px] text-ink-2">{t('account.available')}</p>
                <Money value={q.data.balance} className="text-[32px] leading-tight font-medium tracking-tight" />
                <div className="mt-4 flex flex-wrap items-center gap-3">
                  {q.data.status === 'ACTIVE' && (
                    <ButtonLink to={`/transfer?from=${q.data.id}`} icon={<Send size={15} strokeWidth={1.5} aria-hidden />}>
                      {t('dashboard.sendMoney')}
                    </ButtonLink>
                  )}
                </div>
                {q.data.status === 'FROZEN' && (
                  <div className="mt-4">
                    <Alert>{t('errors.ACCOUNT_FROZEN')}</Alert>
                  </div>
                )}
              </div>
              <div className="space-y-5">
                <Facts
                  items={[
                    [t('accounts.col.status'), <AccountStatusTag key="s" status={q.data.status} />],
                    [t('accounts.currency'), 'VND'],
                    [t('accounts.col.opened'), <span key="o" className="figures text-[13px]">{formatDate(q.data.createdAt, i18n.language)}</span>],
                    [t('accounts.updated'), <span key="u" className="figures text-[13px]">{formatDateTime(q.data.updatedAt, i18n.language)}</span>],
                  ]}
                />
                <LimitMeter limits={q.data.limits} />
              </div>
            </div>
          </section>

          <Sheet title={t('statement.title')} bodyClassName="pt-3">
            <StatementTable accountId={q.data.id} />
          </Sheet>
        </div>
      )}
    </div>
  )
}

import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { useAuth } from '../../auth/useAuth'
import { AccountNumber } from '../../components/AccountNumber'
import { ButtonLink } from '../../components/Button'
import { DataTable, type Column } from '../../components/DataTable'
import { PageHeader } from '../../components/Layout'
import { Money } from '../../components/Money'
import { EmptyState, ErrorState, Skeleton } from '../../components/States'
import { AccountStatusTag } from '../../components/Status'
import { PassbookCard } from '../../features/accounts/PassbookCard'
import { useAccounts } from '../../features/accounts/hooks'
import type { AccountSummary } from '../../types/api'
import { formatDate } from '../../utils/format'

export function AccountsPage() {
  const { t, i18n } = useTranslation()
  const { user } = useAuth()
  const q = useAccounts()
  const total = (q.data ?? []).reduce((s, a) => s + a.balance, 0)

  const columns: Column<AccountSummary>[] = [
    {
      key: 'number',
      header: t('accounts.col.number'),
      mobile: 'title',
      cell: (a) => (
        <Link to={`/accounts/${a.id}`} className="text-vault underline-offset-4 hover:underline">
          <AccountNumber value={a.accountNumber} />
        </Link>
      ),
    },
    { key: 'type', header: t('accounts.col.type'), cell: () => t('account.type.CURRENT') },
    { key: 'opened', header: t('accounts.col.opened'), cell: (a) => <span className="figures text-[13px] text-ink-2">{formatDate(a.createdAt, i18n.language)}</span> },
    { key: 'status', header: t('accounts.col.status'), cell: (a) => <AccountStatusTag status={a.status} /> },
    { key: 'balance', header: t('accounts.col.balance'), align: 'right', mobile: 'end', cell: (a) => <Money value={a.balance} className="font-medium" /> },
  ]

  return (
    <div>
      <PageHeader title={t('accounts.title')} actions={<ButtonLink to="/transfer">{t('dashboard.sendMoney')}</ButtonLink>}>
        {t('accounts.subtitle')}
      </PageHeader>
      {q.isPending && <Skeleton className="h-52 w-full" />}
      {q.isError && <ErrorState error={q.error} onRetry={() => q.refetch()} />}
      {q.data && q.data.length === 0 && <EmptyState title={t('dashboard.noAccounts')} body={t('dashboard.noAccountsBody')} />}
      {q.data && q.data.length > 0 && (
        <div className="space-y-8">
          <div className="grid gap-5 md:grid-cols-2">
            {q.data.map((a) => (
              <PassbookCard key={a.id} account={a} holder={user?.fullName} />
            ))}
          </div>
          <section aria-labelledby="acc-table">
            <h2 id="acc-table" className="mb-2 font-display text-base font-semibold">
              {t('accounts.summary')}
            </h2>
            <DataTable caption={t('accounts.summary')} columns={columns} rows={q.data} rowKey={(a) => a.id} rowHref={(a) => `/accounts/${a.id}`} />
            <div className="flex items-baseline justify-between border-b-2 border-double border-ink/70 py-3">
              <span className="font-medium">{t('accounts.total')}</span>
              <Money value={total} className="font-medium" />
            </div>
          </section>
        </div>
      )}
    </div>
  )
}

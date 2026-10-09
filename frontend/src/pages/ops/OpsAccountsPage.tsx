import { Lock, LockOpen } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { usePermissions } from '../../auth/permissions'
import { AccountNumber } from '../../components/AccountNumber'
import { Button } from '../../components/Button'
import { DataTable, type Column } from '../../components/DataTable'
import { FilterBar, type FilterDef } from '../../components/FilterBar'
import { PageHeader } from '../../components/Layout'
import { Money } from '../../components/Money'
import { Pagination } from '../../components/Pagination'
import { EmptyState } from '../../components/States'
import { AccountStatusTag } from '../../components/Status'
import { FreezeDialog } from '../../features/admin/FreezeDialog'
import { useAdminAccounts } from '../../features/admin/hooks'
import type { AccountAdmin, AccountStatus } from '../../types/api'
import { formatDateTime } from '../../utils/format'
import { strOrUndef, useSearchState } from '../../utils/useSearchState'

const STATUSES: AccountStatus[] = ['ACTIVE', 'FROZEN', 'CLOSED']

export function OpsAccountsPage() {
  const { t, i18n } = useTranslation()
  const perms = usePermissions()
  const { filters, page, apply, setPage } = useSearchState(['q', 'status'] as const)
  const q = useAdminAccounts({ q: strOrUndef(filters.q), status: strOrUndef<AccountStatus>(filters.status), page, size: 20 })
  const [target, setTarget] = useState<AccountAdmin | null>(null)

  const defs: FilterDef[] = [
    { name: 'q', label: t('ops.accounts.search'), type: 'text', placeholder: t('ops.accounts.searchPh') },
    { name: 'status', label: t('accounts.col.status'), type: 'select', options: STATUSES.map((s) => ({ value: s, label: t(`accountStatus.${s}`) })) },
  ]

  const columns: Column<AccountAdmin>[] = [
    {
      key: 'number',
      header: t('accounts.col.number'),
      mobile: 'title',
      cell: (a) => (
        <Link to={`/ops/accounts/${a.id}`} className="text-vault underline-offset-4 hover:underline">
          <AccountNumber value={a.accountNumber} />
        </Link>
      ),
    },
    {
      key: 'customer',
      header: t('fraud.col.customer'),
      mobile: 'title',
      cell: (a) => (
        <Link to={`/ops/customers/${a.customerId}`} className="font-medium hover:underline">
          {a.customerName}
        </Link>
      ),
    },
    { key: 'status', header: t('accounts.col.status'), mobile: 'end', cell: (a) => <AccountStatusTag status={a.status} /> },
    { key: 'balance', header: t('accounts.col.balance'), align: 'right', mobile: 'end', cell: (a) => <Money value={a.balance} /> },
    { key: 'updated', header: t('accounts.updated'), align: 'right', cell: (a) => <span className="figures text-[12px] text-ink-2">{formatDateTime(a.updatedAt, i18n.language)}</span> },
    ...(perms.canMutate
      ? [
          {
            key: 'action',
            header: <span className="sr-only">{t('ops.accounts.actions')}</span>,
            align: 'right' as const,
            cell: (a: AccountAdmin) =>
              a.status === 'CLOSED' ? null : (
                <Button
                  variant="ghost"
                  size="sm"
                  icon={a.status === 'ACTIVE' ? <Lock size={14} strokeWidth={1.5} aria-hidden /> : <LockOpen size={14} strokeWidth={1.5} aria-hidden />}
                  onClick={() => setTarget(a)}
                  aria-label={`${a.status === 'ACTIVE' ? t('ops.freeze.action') : t('ops.freeze.unfreezeAction')} ${a.accountNumber}`}
                  className={a.status === 'ACTIVE' ? 'text-debit hover:bg-debit/10' : ''}
                >
                  {a.status === 'ACTIVE' ? t('ops.freeze.action') : t('ops.freeze.unfreezeAction')}
                </Button>
              ),
          },
        ]
      : []),
  ]

  return (
    <div>
      <PageHeader title={t('ops.accounts.title')}>{t('ops.accounts.subtitle')}</PageHeader>
      <FilterBar defs={defs} values={filters} onApply={apply} label={t('ops.accounts.filter')} />
      <DataTable
        caption={t('ops.accounts.title')}
        columns={columns}
        rows={q.data?.content}
        rowKey={(a) => a.id}
        rowHref={(a) => `/ops/accounts/${a.id}`}
        loading={q.isFetching}
        error={q.error}
        onRetry={() => q.refetch()}
        empty={<EmptyState compact title={t('common.noResults')} body={t('ops.accounts.empty')} />}
      />
      {q.data && <Pagination {...q.data} onChange={setPage} />}
      {target && (
        <FreezeDialog key={target.id + target.status} open onClose={() => setTarget(null)} freeze={target.status === 'ACTIVE'} account={target} />
      )}
    </div>
  )
}

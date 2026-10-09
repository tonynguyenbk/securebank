import { ArrowLeft, Lock, LockOpen } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router-dom'
import { ApiError } from '../../api/errors'
import { usePermissions } from '../../auth/permissions'
import { AccountNumber } from '../../components/AccountNumber'
import { Button } from '../../components/Button'
import { Facts, PageHeader, Sheet } from '../../components/Layout'
import { Money } from '../../components/Money'
import { ErrorState, Skeleton } from '../../components/States'
import { AccountStatusTag } from '../../components/Status'
import { AdminTransactionsTable } from '../../features/admin/AdminTransactionsTable'
import { FreezeDialog } from '../../features/admin/FreezeDialog'
import { LimitsEditor } from '../../features/admin/LimitsEditor'
import { useAdminAccount, useAdminTransactions } from '../../features/admin/hooks'
import { LimitMeter } from '../../features/transfers/LimitMeter'
import { formatDate, formatDateTime } from '../../utils/format'
import { MissingRecord } from '../StatusPages'

export function OpsAccountDetailPage() {
  const { id } = useParams()
  const { t, i18n } = useTranslation()
  const lang = i18n.language
  const perms = usePermissions()
  const q = useAdminAccount(id)
  const [page, setPage] = useState(0)
  const txs = useAdminTransactions({ accountNumber: q.data?.accountNumber, page, size: 10 }, !!q.data)
  const [freezeOpen, setFreezeOpen] = useState(false)

  if (q.isError && q.error instanceof ApiError && q.error.status === 404) return <MissingRecord backTo="/ops/accounts" backLabel={t('ops.accounts.back')} />
  const a = q.data

  return (
    <div>
      <Link to="/ops/accounts" className="mb-4 inline-flex items-center gap-1.5 text-[13px] text-vault underline-offset-4 hover:underline">
        <ArrowLeft size={14} strokeWidth={1.5} aria-hidden /> {t('ops.accounts.back')}
      </Link>
      {q.isPending && <Skeleton className="h-72 w-full" />}
      {q.isError && <ErrorState error={q.error} onRetry={() => q.refetch()} />}
      {a && (
        <div className="space-y-6">
          <PageHeader
            eyebrow={t('account.type.CURRENT')}
            title={<AccountNumber value={a.accountNumber} />}
            actions={
              perms.canMutate &&
              a.status !== 'CLOSED' && (
                <Button
                  variant={a.status === 'ACTIVE' ? 'danger' : 'primary'}
                  icon={a.status === 'ACTIVE' ? <Lock size={15} strokeWidth={1.5} aria-hidden /> : <LockOpen size={15} strokeWidth={1.5} aria-hidden />}
                  onClick={() => setFreezeOpen(true)}
                >
                  {a.status === 'ACTIVE' ? t('ops.freeze.action') : t('ops.freeze.unfreezeAction')}
                </Button>
              )
            }
          >
            <Link to={`/ops/customers/${a.customerId}`} className="text-vault underline-offset-4 hover:underline">
              {a.customerName}
            </Link>
          </PageHeader>

          <div className="grid items-start gap-6 lg:grid-cols-2">
            <Sheet title={t('ops.accounts.overview')}>
              <p className="text-[12px] text-ink-2">{t('accounts.col.balance')}</p>
              <Money value={a.balance} className="text-[26px] font-medium" />
              <Facts
                className="mt-4"
                items={[
                  [t('accounts.col.status'), <AccountStatusTag key="s" status={a.status} />],
                  [t('accounts.currency'), a.currency],
                  [t('accounts.col.opened'), <span key="o" className="figures text-[13px]">{formatDate(a.createdAt, lang)}</span>],
                  [t('accounts.updated'), <span key="u" className="figures text-[13px]">{formatDateTime(a.updatedAt, lang)}</span>],
                  [t('ops.accounts.id'), <span key="i" className="figures text-[12px] break-all text-ink-2">{a.id}</span>],
                ]}
              />
            </Sheet>
            <Sheet title={t('ops.limits.title')}>
              <LimitMeter limits={a.limits} />
              <div className="mt-5 border-t border-rule pt-5">
                {perms.canMutate ? (
                  <LimitsEditor key={a.limits.updatedAt} accountId={a.id} limits={a.limits} />
                ) : (
                  <p className="text-[13px] text-ink-2">{t('ops.readOnly')}</p>
                )}
              </div>
            </Sheet>
          </div>

          <Sheet title={t('ops.accounts.transactions')}>
            <AdminTransactionsTable query={txs} onPage={setPage} dense />
          </Sheet>

          {perms.canMutate && (
            <FreezeDialog key={a.status} open={freezeOpen} onClose={() => setFreezeOpen(false)} freeze={a.status === 'ACTIVE'} account={a} />
          )}
        </div>
      )}
    </div>
  )
}

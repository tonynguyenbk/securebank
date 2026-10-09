import { ArrowLeft, Lock } from 'lucide-react'
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
import { AccountStatusTag, FraudStatusTag, RiskBadge } from '../../components/Status'
import { FreezeDialog } from '../../features/admin/FreezeDialog'
import { useAdminAccount } from '../../features/admin/hooks'
import { ReviewPanel } from '../../features/fraud/ReviewPanel'
import { useFraudAlert } from '../../features/fraud/hooks'
import { formatDateTime } from '../../utils/format'
import { MissingRecord } from '../StatusPages'

export function FraudDetailPage() {
  const { id } = useParams()
  const { t, i18n } = useTranslation()
  const lang = i18n.language
  const perms = usePermissions()
  const q = useFraudAlert(id)
  const account = useAdminAccount(q.data?.sourceAccountId)
  const [freezeOpen, setFreezeOpen] = useState(false)

  if (q.isError && q.error instanceof ApiError && q.error.status === 404) return <MissingRecord backTo="/ops/fraud" backLabel={t('fraud.back')} />

  const a = q.data
  return (
    <div>
      <Link to="/ops/fraud" className="mb-4 inline-flex items-center gap-1.5 text-[13px] text-vault underline-offset-4 hover:underline">
        <ArrowLeft size={14} strokeWidth={1.5} aria-hidden /> {t('fraud.back')}
      </Link>
      {q.isPending && <Skeleton className="h-96 w-full" />}
      {q.isError && <ErrorState error={q.error} onRetry={() => q.refetch()} />}
      {a && (
        <>
          <PageHeader
            eyebrow={t('fraud.alert')}
            title={<span className="figures">{a.transactionReference}</span>}
            actions={
              <div className="flex items-center gap-3">
                <RiskBadge score={a.riskScore} level={a.riskLevel} />
                <FraudStatusTag status={a.status} />
              </div>
            }
          >
            {t('fraud.raised', { time: formatDateTime(a.createdAt, lang) })}
          </PageHeader>

          <div className="grid items-start gap-6 xl:grid-cols-[minmax(0,1.5fr)_minmax(0,1fr)]">
            <div className="space-y-6">
              <Sheet title={t('fraud.rules')}>
                <table className="w-full text-left">
                  <caption className="sr-only">{t('fraud.rules')}</caption>
                  <thead>
                    <tr className="border-b border-ink text-[11px] tracking-wider text-ink-2 uppercase">
                      <th scope="col" className="py-2 pr-3 font-medium">{t('fraud.rule')}</th>
                      <th scope="col" className="py-2 text-right font-medium">{t('fraud.score')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {a.rules.map((r) => (
                      <tr key={r.ruleCode} className="border-b border-rule align-top">
                        <td className="py-3 pr-3">
                          <span className="block font-medium">{t(`fraud.ruleName.${r.ruleCode}`, { defaultValue: r.description })}</span>
                          <span className="block text-[13px] text-ink-2">{t(`fraud.ruleDesc.${r.ruleCode}`, { defaultValue: r.description })}</span>
                          <span className="figures mt-0.5 block text-[11px] text-ink-2">{r.details}</span>
                        </td>
                        <td className="figures py-3 text-right text-[15px]">+{r.scoreContribution}</td>
                      </tr>
                    ))}
                  </tbody>
                  <tfoot>
                    <tr className="border-t-2 border-double border-ink/70">
                      <th scope="row" className="py-2.5 text-left font-medium">
                        {t('fraud.total')} · {t(`risk.${a.riskLevel}`)}
                      </th>
                      <td className="figures py-2.5 text-right text-[17px] font-medium">{a.riskScore}</td>
                    </tr>
                  </tfoot>
                </table>
                <p className="mt-3 text-[12px] text-ink-2">{t('fraud.scale')}</p>
              </Sheet>

              <Sheet title={t('fraud.transaction')}>
                <Facts
                  items={[
                    [t('fraud.col.amount'), <Money key="a" value={a.amount} className="font-medium" />],
                    [t('fraud.from'), <span key="f"><span className="block font-medium">{a.customerName}</span><AccountNumber value={a.sourceAccountNumber} className="text-[13px] text-ink-2" /></span>],
                    [t('fraud.to'), <span key="t"><span className="block font-medium">{a.destinationCustomerName}</span><AccountNumber value={a.destinationAccountNumber} className="text-[13px] text-ink-2" /></span>],
                    [t('fraud.occurred'), <span key="o" className="figures text-[13px]">{formatDateTime(a.transactionOccurredAt, lang)}</span>],
                    [t('fraud.col.transaction'), <Link key="l" to={`/ops/transactions/${a.transactionId}`} className="figures text-vault underline-offset-4 hover:underline">{a.transactionReference}</Link>],
                  ]}
                />
              </Sheet>

              <Sheet title={t('fraud.timeline')}>
                <ol className="relative ml-1.5 border-l border-rule">
                  {a.timeline.map((e, i) => (
                    <li key={i} className="relative pb-5 pl-5 last:pb-0">
                      <span aria-hidden className="absolute top-1.5 -left-[5px] size-[9px] rounded-full border-2 border-sheet bg-ink-2" />
                      <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
                        <FraudStatusTag status={e.status} />
                        <span className="figures text-[12px] text-ink-2">{formatDateTime(e.at, lang)}</span>
                        <span className="figures text-[12px] text-ink-2">{e.actorUsername ?? t('fraud.system')}</span>
                      </div>
                      {e.note && <p className="mt-1 max-w-[60ch] text-[13px]">{e.note}</p>}
                    </li>
                  ))}
                </ol>
              </Sheet>
            </div>

            <aside className="space-y-6">
              <Sheet title={t('fraud.customer')}>
                <Facts
                  items={[
                    [t('fraud.col.customer'), <Link key="c" to={`/ops/customers/${a.customerId}`} className="text-vault underline-offset-4 hover:underline">{a.customerName}</Link>],
                    [t('ops.accounts.account'), <Link key="a" to={`/ops/accounts/${a.sourceAccountId}`} className="text-vault underline-offset-4 hover:underline"><AccountNumber value={a.sourceAccountNumber} /></Link>],
                    [t('accounts.col.status'), account.data ? <AccountStatusTag key="s" status={account.data.status} /> : <Skeleton key="s" className="h-4 w-20" />],
                    [t('accounts.col.balance'), account.data ? <Money key="b" value={account.data.balance} /> : <Skeleton key="b" className="h-4 w-24" />],
                  ]}
                />
                {perms.canMutate && account.data && account.data.status === 'ACTIVE' && (
                  <Button variant="danger" className="mt-4 w-full" icon={<Lock size={15} strokeWidth={1.5} aria-hidden />} onClick={() => setFreezeOpen(true)}>
                    {t('fraud.freeze')}
                  </Button>
                )}
              </Sheet>

              <Sheet title={t('fraud.review.title')}>
                {a.reviewNote && (
                  <div className="mb-4 border-l-2 border-rule pl-3 text-[13px]">
                    <p>{a.reviewNote}</p>
                    <p className="figures mt-1 text-[11px] text-ink-2">
                      {a.reviewedByUsername} · {formatDateTime(a.reviewedAt, lang)}
                    </p>
                  </div>
                )}
                {perms.canMutate ? <ReviewPanel key={a.status} alert={a} /> : <p className="text-[13px] text-ink-2">{t('ops.readOnly')}</p>}
              </Sheet>
            </aside>
          </div>

          {perms.canMutate && account.data && (
            <FreezeDialog
              open={freezeOpen}
              onClose={() => setFreezeOpen(false)}
              freeze
              account={{ id: a.sourceAccountId, accountNumber: a.sourceAccountNumber, customerName: a.customerName }}
              suggestedReason={t('fraud.freezeReason', { ref: a.transactionReference })}
            />
          )}
        </>
      )}
    </div>
  )
}

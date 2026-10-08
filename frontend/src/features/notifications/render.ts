import type { TFunction } from 'i18next'
import type { Notification } from '../../types/api'
import { formatAccountNumber, formatVnd } from '../../utils/format'

/** Renders templateCode + params through i18n; subject/message (English) are only a fallback. */
export function renderNotification(t: TFunction, n: Notification): { title: string; body: string } {
  const p = n.params ?? {}
  const values = {
    amount: p.amount !== undefined ? formatVnd(Number(p.amount)) : '',
    balanceAfter: p.balanceAfter !== undefined ? formatVnd(Number(p.balanceAfter)) : '',
    reference: String(p.reference ?? ''),
    counterpartyName: String(p.counterpartyName ?? ''),
    accountNumber: p.accountNumber ? formatAccountNumber(String(p.accountNumber)) : '',
    reason: p.reasonCode ? t(`errors.${p.reasonCode}`, { defaultValue: String(p.reasonCode) }) : '',
  }
  const key = `notification.${n.templateCode}`
  return {
    title: t(`${key}.title`, { ...values, defaultValue: n.subject }),
    body: t(`${key}.body`, { ...values, defaultValue: n.message }),
  }
}

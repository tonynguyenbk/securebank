import type { TFunction } from 'i18next'
import type { Notification } from '../../types/api'
import { formatAccountNumber, formatVnd } from '../../utils/format'

/*
 * Params per template (contract §6):
 *  TRANSFER_SENT / TRANSFER_RECEIVED: amount, currency, reference, accountNumber, counterpartyName, counterpartyAccountNumber, balanceAfter
 *  TRANSFER_REJECTED: amount, currency, reference, accountNumber, counterpartyAccountNumber, failureCode (no counterpartyName)
 *  ACCOUNT_FROZEN / ACCOUNT_UNFROZEN: accountNumber, status
 *  WELCOME: fullName, username
 * Account numbers arrive masked ("******0002"). Every param is treated as optional: a missing one drops its
 * clause instead of leaving a gap, and subject/message (English) remain the last-resort fallback.
 */
export function renderNotification(t: TFunction, n: Notification): { title: string; body: string } {
  const p = n.params ?? {}
  const has = (k: string) => p[k] !== undefined && p[k] !== null && String(p[k]).trim() !== ''
  const str = (k: string) => (has(k) ? String(p[k]) : '')
  const amount = has('amount') ? formatVnd(Number(p.amount)) : ''
  const acct = (k: string) => (has(k) ? formatAccountNumber(String(p[k])) : '')

  // "Trần Thị Bình (•••• 0002)", or whichever half is present.
  const name = str('counterpartyName')
  const number = acct('counterpartyAccountNumber')
  const party = name && number ? `${name} (${number})` : name || number

  const parts = (...xs: (string | false)[]) => xs.filter(Boolean).join(' ')
  const ref = has('reference') && t('notification.part.ref', { reference: str('reference') })
  const balance = has('balanceAfter') && t('notification.part.balance', { balance: formatVnd(Number(p.balanceAfter)) })
  const fallback = { title: n.subject, body: n.message }

  switch (n.templateCode) {
    case 'TRANSFER_SENT':
      if (!amount) return fallback
      return {
        title: t('notification.TRANSFER_SENT.title', { amount }),
        body: parts(party && t('notification.TRANSFER_SENT.to', { party }), ref, balance) || n.message,
      }
    case 'TRANSFER_RECEIVED':
      if (!amount) return fallback
      return {
        title: t('notification.TRANSFER_RECEIVED.title', { amount }),
        body: parts(party && t('notification.TRANSFER_RECEIVED.from', { party }), ref, balance) || n.message,
      }
    case 'TRANSFER_REJECTED': {
      const code = str('failureCode') || str('reasonCode')
      return {
        title: t('notification.TRANSFER_REJECTED.title'),
        body: parts(
          amount && (party ? t('notification.TRANSFER_REJECTED.bodyTo', { amount, party }) : t('notification.TRANSFER_REJECTED.body', { amount })),
          ref,
          code && t(`errors.${code}`, { defaultValue: code }),
        ) || n.message,
      }
    }
    case 'ACCOUNT_FROZEN':
    case 'ACCOUNT_UNFROZEN': {
      const account = acct('accountNumber')
      const key = `notification.${n.templateCode}`
      return {
        title: t(`${key}.title`),
        body: account ? t(`${key}.body`, { accountNumber: account }) : t(`${key}.bodyNoNumber`),
      }
    }
    case 'WELCOME': {
      const fullName = str('fullName')
      return {
        title: fullName ? t('notification.WELCOME.titleNamed', { fullName }) : t('notification.WELCOME.title'),
        body: has('username') ? t('notification.WELCOME.body', { username: str('username') }) : t('notification.WELCOME.bodyNoUser'),
      }
    }
    default:
      return fallback
  }
}

import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { homeFor } from '../auth/context'
import { useAuth } from '../auth/useAuth'
import { ButtonLink } from '../components/Button'
import { StatusStamp } from '../components/Status'

function Plain({ code, title, body, stamp }: { code: string; title: string; body: string; stamp?: boolean }) {
  const { t } = useTranslation()
  const { user } = useAuth()
  return (
    <main id="main" className="mx-auto flex min-h-dvh max-w-xl flex-col justify-center px-4 py-16">
      <div className="flex items-center gap-4">
        <p className="figures text-ink-2">{code}</p>
        {stamp && <StatusStamp kind="REJECTED" />}
      </div>
      <h1 className="mt-3 font-display text-[28px] leading-tight font-semibold">{title}</h1>
      <p className="mt-2 text-ink-2">{body}</p>
      <div className="mt-7">
        <ButtonLink to={user ? homeFor(user) : '/login'} variant="secondary">
          {user ? t('status.toHome') : t('notFound.back')}
        </ButtonLink>
      </div>
    </main>
  )
}

export function ForbiddenPage() {
  const { t } = useTranslation()
  return <Plain code="403" stamp title={t('status.forbiddenTitle')} body={t('status.forbiddenBody')} />
}

export function NotFoundPage() {
  const { t } = useTranslation()
  return <Plain code="404" title={t('notFound.title')} body={t('status.notFoundBody')} />
}

/** In-page "not found" for a missing record (keeps the shell). */
export function MissingRecord({ backTo, backLabel }: { backTo: string; backLabel: string }) {
  const { t } = useTranslation()
  return (
    <div className="py-10">
      <h1 className="font-display text-[24px] font-semibold">{t('status.recordMissing')}</h1>
      <p className="mt-2 text-ink-2">{t('status.recordMissingBody')}</p>
      <Link to={backTo} className="mt-5 inline-block text-vault underline underline-offset-4">
        {backLabel}
      </Link>
    </div>
  )
}

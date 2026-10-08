import { useTranslation } from 'react-i18next'
import { PageHeader } from '../components/Layout'

/** Temporary placeholder for sections that land in a later milestone of the preview. */
export function InProgress({ titleKey }: { titleKey: string }) {
  const { t } = useTranslation()
  return (
    <div>
      <PageHeader title={t(titleKey)} />
      <p className="max-w-[60ch] border-t border-dashed border-rule pt-6 text-ink-2">{t('status.inProgress')}</p>
    </div>
  )
}

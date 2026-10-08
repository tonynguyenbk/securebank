import { BrowserRouter, Link, Navigate, Route, Routes } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { LoginPage } from './pages/LoginPage'

export default function App() {
  return (
    <BrowserRouter basename={import.meta.env.BASE_URL.replace(/\/$/, '')}>
      <Routes>
        <Route path="/" element={<Navigate to="/login" replace />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="*" element={<NotFound />} />
      </Routes>
    </BrowserRouter>
  )
}

function NotFound() {
  const { t } = useTranslation()
  return (
    <main className="mx-auto max-w-md px-4 py-24">
      <p className="figures text-ink-2">404</p>
      <h1 className="mt-2 font-display text-[28px] font-semibold">{t('notFound.title')}</h1>
      <Link to="/login" className="mt-6 inline-block text-vault underline underline-offset-4">
        {t('notFound.back')}
      </Link>
    </main>
  )
}

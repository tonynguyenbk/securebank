import { lazy, Suspense, type ComponentType, type ReactNode } from 'react'
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { FullPageLoader, HomeRedirect, PublicOnly, RequireRole } from '../auth/guards'
import { SkeletonRows } from '../components/States'
import { AppShell } from '../layouts/AppShell'
import { OpsShell } from '../layouts/OpsShell'
import { DashboardPage } from '../pages/customer/DashboardPage'
import { LoginPage } from '../pages/LoginPage'
import { RegisterPage } from '../pages/RegisterPage'
import { ForbiddenPage, NotFoundPage } from '../pages/StatusPages'
import { STAFF_ROLES } from '../types/api'

const named = <K extends string>(loader: () => Promise<Record<K, ComponentType>>, name: K) =>
  lazy(() => loader().then((m) => ({ default: m[name] })))

// Customer pages
const AccountsPage = named(() => import('../pages/customer/AccountsPage'), 'AccountsPage')
const AccountDetailPage = named(() => import('../pages/customer/AccountDetailPage'), 'AccountDetailPage')
const TransferPage = named(() => import('../pages/customer/TransferPage'), 'TransferPage')
const TransactionsPage = named(() => import('../pages/customer/TransactionsPage'), 'TransactionsPage')
const TransactionDetailPage = named(() => import('../pages/customer/TransactionDetailPage'), 'TransactionDetailPage')
const NotificationsPage = named(() => import('../pages/customer/NotificationsPage'), 'NotificationsPage')
const ProfilePage = named(() => import('../pages/customer/ProfilePage'), 'ProfilePage')
// Ops pages (own chunk: Recharts only loads here)
const OpsDashboardPage = named(() => import('../pages/ops/OpsDashboardPage'), 'OpsDashboardPage')
const OpsCustomersPage = named(() => import('../pages/ops/OpsCustomersPage'), 'OpsCustomersPage')
const OpsCustomerDetailPage = named(() => import('../pages/ops/OpsCustomerDetailPage'), 'OpsCustomerDetailPage')
const OpsAccountsPage = named(() => import('../pages/ops/OpsAccountsPage'), 'OpsAccountsPage')
const OpsAccountDetailPage = named(() => import('../pages/ops/OpsAccountDetailPage'), 'OpsAccountDetailPage')
const OpsTransactionsPage = named(() => import('../pages/ops/OpsTransactionsPage'), 'OpsTransactionsPage')
const OpsTransactionDetailPage = named(() => import('../pages/ops/OpsTransactionDetailPage'), 'OpsTransactionDetailPage')
const FraudQueuePage = named(() => import('../pages/ops/FraudQueuePage'), 'FraudQueuePage')
const FraudDetailPage = named(() => import('../pages/ops/FraudDetailPage'), 'FraudDetailPage')
const AuditPage = named(() => import('../pages/ops/AuditPage'), 'AuditPage')

const page = (el: ReactNode) => <Suspense fallback={<SkeletonRows rows={6} />}>{el}</Suspense>

export function AppRouter() {
  return (
    <BrowserRouter basename={import.meta.env.BASE_URL.replace(/\/$/, '')}>
      <Suspense fallback={<FullPageLoader />}>
        <Routes>
          <Route path="/" element={<HomeRedirect />} />
          <Route element={<PublicOnly />}>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />
          </Route>

          <Route element={<RequireRole roles={['CUSTOMER']} />}>
            <Route element={<AppShell />}>
              <Route path="/dashboard" element={<DashboardPage />} />
              <Route path="/accounts" element={page(<AccountsPage />)} />
              <Route path="/accounts/:id" element={page(<AccountDetailPage />)} />
              <Route path="/transfer" element={page(<TransferPage />)} />
              <Route path="/transactions" element={page(<TransactionsPage />)} />
              <Route path="/transactions/:id" element={page(<TransactionDetailPage />)} />
              <Route path="/notifications" element={page(<NotificationsPage />)} />
              <Route path="/profile" element={page(<ProfilePage />)} />
            </Route>
          </Route>

          <Route element={<RequireRole roles={STAFF_ROLES} />}>
            <Route element={<OpsShell />}>
              <Route path="/ops" element={<HomeRedirect />} />
              <Route path="/ops/dashboard" element={page(<OpsDashboardPage />)} />
              <Route path="/ops/customers" element={page(<OpsCustomersPage />)} />
              <Route path="/ops/customers/:id" element={page(<OpsCustomerDetailPage />)} />
              <Route path="/ops/accounts" element={page(<OpsAccountsPage />)} />
              <Route path="/ops/accounts/:id" element={page(<OpsAccountDetailPage />)} />
              <Route path="/ops/transactions" element={page(<OpsTransactionsPage />)} />
              <Route path="/ops/transactions/:id" element={page(<OpsTransactionDetailPage />)} />
              <Route path="/ops/fraud" element={page(<FraudQueuePage />)} />
              <Route path="/ops/fraud/:id" element={page(<FraudDetailPage />)} />
              <Route path="/ops/audit" element={page(<AuditPage />)} />
            </Route>
          </Route>

          <Route path="/403" element={<ForbiddenPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </Suspense>
    </BrowserRouter>
  )
}

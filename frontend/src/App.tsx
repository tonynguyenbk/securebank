import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ApiError } from './api/errors'
import { AuthProvider } from './auth/AuthProvider'
import { ToastProvider } from './components/Toast'
import { AppRouter } from './router/AppRouter'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 15_000,
      refetchOnWindowFocus: false,
      // Retry once on transient failures only; a 4xx is an answer, not a glitch.
      retry: (count, err) => count < 1 && err instanceof ApiError && err.transient,
    },
    mutations: { retry: false },
  },
})

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <AuthProvider>
          <AppRouter />
        </AuthProvider>
      </ToastProvider>
    </QueryClientProvider>
  )
}

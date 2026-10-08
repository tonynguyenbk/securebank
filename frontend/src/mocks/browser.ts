import { setupWorker } from 'msw/browser'
import { handlers } from './handlers'

/** Starts the MSW service worker for the preview build. Loaded via dynamic import only when VITE_API_MODE=mock. */
export async function startMockApi() {
  const base = import.meta.env.BASE_URL
  const worker = setupWorker(...handlers)
  await worker.start({
    serviceWorker: { url: `${base}mockServiceWorker.js`, options: { scope: base } },
    onUnhandledFrame: 'bypass',
    quiet: true,
  })
}

export { resetMockData } from './handlers'

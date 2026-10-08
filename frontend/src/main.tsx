import '@fontsource/ibm-plex-sans/400.css'
import '@fontsource/ibm-plex-sans/500.css'
import '@fontsource/ibm-plex-sans/600.css'
import '@fontsource/ibm-plex-mono/400.css'
import '@fontsource/ibm-plex-mono/500.css'
import '@fontsource/be-vietnam-pro/600.css'
import '@fontsource/be-vietnam-pro/700.css'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'
import './i18n'
import './index.css'

async function enableMocks() {
  // Statically false in live builds, so the mock layer (and MSW) is never bundled there.
  if (import.meta.env.VITE_API_MODE !== 'mock') return
  const { startMockApi } = await import('./mocks/browser')
  await startMockApi()
}

enableMocks()
  .catch((err) => console.error('Mock API failed to start', err))
  .finally(() => {
    createRoot(document.getElementById('root')!).render(
      <StrictMode>
        <App />
      </StrictMode>,
    )
  })

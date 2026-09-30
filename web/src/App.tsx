import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { lazy, Suspense } from 'react'
import { useTranslation } from 'react-i18next'
import { BrowserRouter, Link, Route, Routes } from 'react-router'
import { ApiError, NETWORK_ERROR } from './api/client'
import { AuthProvider } from './auth/AuthProvider'
import { useAuth } from './auth/useAuth'
import { Layout } from './components/Layout'
import { Loading } from './components/ui'
import { AdminPage } from './pages/AdminPage'
import { DayPage } from './pages/DayPage'
import { FoodDetailPage } from './pages/FoodDetailPage'
import { FoodsPage } from './pages/FoodsPage'
import { LoginPage, SetupPage } from './pages/LoginPage'
import { NutrientDetailPage } from './pages/NutrientDetailPage'
import { NutrientsPage } from './pages/NutrientsPage'
import { SettingsPage } from './pages/SettingsPage'
import { TargetsPage } from './pages/TargetsPage'
import { UnitDetailPage } from './pages/UnitDetailPage'
import { UnitsPage } from './pages/UnitsPage'

// The chart library is large; load it only when the history page is opened.
const HistoryPage = lazy(() => import('./pages/HistoryPage').then((m) => ({ default: m.HistoryPage })))

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 15_000,
      // Retry only when the server was unreachable; 4xx answers won't change on retry.
      retry: (failures, error) => error instanceof ApiError && error.code === NETWORK_ERROR && failures < 2,
    },
  },
})

function NotFound() {
  const { t } = useTranslation()
  return (
    <div className="card">
      <h1>{t('errors.pageNotFound')}</h1>
      <Link to="/">{t('nav.day')}</Link>
    </div>
  )
}

function Gate() {
  const { t } = useTranslation()
  const { state, retry } = useAuth()
  switch (state.status) {
    case 'loading':
      return (
        <div className="center-screen">
          <Loading />
        </div>
      )
    case 'unreachable':
      return (
        <div className="center-screen">
          <div className="card narrow">
            <h1>{t('server.unreachableTitle')}</h1>
            <p>{t('server.unreachableText')}</p>
            <button type="button" className="btn btn-primary" onClick={retry}>
              {t('server.retry')}
            </button>
          </div>
        </div>
      )
    case 'incompatible':
      return (
        <div className="center-screen">
          <div className="card narrow">
            <h1>{t('server.incompatibleTitle')}</h1>
            <p>{t('server.incompatibleText', { server: state.serverApiVersion })}</p>
          </div>
        </div>
      )
    case 'setup':
      return <SetupPage />
    case 'anonymous':
      return <LoginPage />
    case 'authenticated':
      return (
        <Routes>
          <Route element={<Layout />}>
            <Route index element={<DayPage />} />
            <Route path="day/:date" element={<DayPage />} />
            <Route path="foods" element={<FoodsPage />} />
            <Route path="foods/:id" element={<FoodDetailPage />} />
            <Route path="units" element={<UnitsPage />} />
            <Route path="units/:id" element={<UnitDetailPage />} />
            <Route path="nutrients" element={<NutrientsPage />} />
            <Route path="nutrients/:id" element={<NutrientDetailPage />} />
            <Route path="targets" element={<TargetsPage />} />
            <Route
              path="history"
              element={
                <Suspense fallback={<Loading />}>
                  <HistoryPage />
                </Suspense>
              }
            />
            <Route path="settings" element={<SettingsPage />} />
            <Route path="admin" element={<AdminPage />} />
            <Route path="*" element={<NotFound />} />
          </Route>
        </Routes>
      )
  }
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <BrowserRouter>
          <Gate />
        </BrowserRouter>
      </AuthProvider>
    </QueryClientProvider>
  )
}

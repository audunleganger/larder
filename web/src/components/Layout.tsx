import { NavLink, Outlet } from 'react-router'
import { useTranslation } from 'react-i18next'
import { useAuth, useUser } from '../auth/useAuth'

export function Layout() {
  const { t } = useTranslation()
  const { logout } = useAuth()
  const user = useUser()
  const links = [
    { to: '/', label: t('nav.day'), end: true },
    { to: '/foods', label: t('nav.foods') },
    { to: '/units', label: t('nav.units') },
    { to: '/nutrients', label: t('nav.nutrients') },
    { to: '/targets', label: t('nav.targets') },
    { to: '/history', label: t('nav.history') },
    { to: '/settings', label: t('nav.settings') },
    ...(user.isAdmin ? [{ to: '/admin', label: t('nav.admin') }] : []),
  ]
  return (
    <div className="app">
      <header className="topbar">
        <div className="topbar-inner">
          <NavLink to="/" className="brand">
            {t('appName')}
          </NavLink>
          <nav className="nav" aria-label={t('nav.label')}>
            {links.map((link) => (
              <NavLink key={link.to} to={link.to} end={link.end} className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
                {link.label}
              </NavLink>
            ))}
          </nav>
          <div className="user-menu">
            <span className="muted">{user.username}</span>
            <button type="button" className="btn btn-ghost btn-small" onClick={() => void logout()}>
              {t('nav.logout')}
            </button>
          </div>
        </div>
      </header>
      <main className="content">
        <Outlet />
      </main>
    </div>
  )
}

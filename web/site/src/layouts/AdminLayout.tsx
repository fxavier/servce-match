import { Outlet } from 'react-router-dom';
import { NavLink } from 'react-router-dom';
import { SiteFooter } from '../components/layout/SiteFooter';
import { SiteHeader } from '../components/layout/SiteHeader';
import { cn } from '../lib/cn';

const ADMIN_NAV = [{ to: '/admin', label: 'Aprovação de prestadores' }];

/**
 * Shell da área `/admin` — mesmo header/footer públicos + sub-nav própria,
 * no mesmo padrão de `AppLayout`/`ProviderLayout`. A proteção real do dado
 * é do servidor (`@PreAuthorize("hasRole('ADMIN')")` no backend); ver
 * `routes/ProtectedRoute.tsx`.
 */
export function AdminLayout() {
  return (
    <div className="flex min-h-dvh flex-col">
      <a href="#main-content" className="skip-link">
        Saltar para o conteúdo principal
      </a>
      <SiteHeader />
      <div className="border-b border-line bg-surface-2">
        <nav
          aria-label="Navegação da área de administração"
          className="mx-auto flex max-w-[1280px] gap-1 overflow-x-auto px-5 sm:px-8 lg:px-10"
        >
          {ADMIN_NAV.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end
              className={({ isActive }) =>
                cn(
                  'whitespace-nowrap border-b-2 border-transparent px-3 py-3 text-sm font-medium text-muted hover:text-foreground',
                  isActive && 'border-orange-500 text-foreground',
                )
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
      </div>
      <main id="main-content" className="flex-1">
        <Outlet />
      </main>
      <SiteFooter />
    </div>
  );
}

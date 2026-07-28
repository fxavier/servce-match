import { Outlet } from 'react-router-dom';
import { NavLink } from 'react-router-dom';
import { SiteFooter } from '../components/layout/SiteFooter';
import { SiteHeader } from '../components/layout/SiteHeader';
import { cn } from '../lib/cn';

const CUSTOMER_NAV = [
  { to: '/painel', label: 'Painel' },
  { to: '/pedidos', label: 'Os meus pedidos' },
  { to: '/conversas', label: 'Conversas' },
];

/** Shell autenticado do cliente — mesmo header/footer públicos + sub-nav da área privada. */
export function AppLayout() {
  return (
    <div className="flex min-h-dvh flex-col">
      <a href="#main-content" className="skip-link">
        Saltar para o conteúdo principal
      </a>
      <SiteHeader />
      <div className="border-b border-line bg-surface-2">
        <nav
          aria-label="Navegação da área do cliente"
          className="mx-auto flex max-w-[1280px] gap-1 overflow-x-auto px-5 sm:px-8 lg:px-10"
        >
          {CUSTOMER_NAV.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
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

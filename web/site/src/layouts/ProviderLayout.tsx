import { Inbox, LayoutDashboard, MessageSquare, User, Wallet } from 'lucide-react';
import { NavLink, Outlet } from 'react-router-dom';
import { DevMockPanel } from '../components/DevMockPanel';
import { SiteFooter } from '../components/layout/SiteFooter';
import { SiteHeader } from '../components/layout/SiteHeader';
import { cn } from '../lib/cn';

const PROVIDER_NAV = [
  { to: '/pro', label: 'Painel', icon: LayoutDashboard, end: true },
  { to: '/pro/pedidos', label: 'Pedidos elegíveis', icon: Inbox },
  { to: '/pro/propostas', label: 'As minhas propostas', icon: MessageSquare },
  { to: '/pro/perfil', label: 'Perfil', icon: User },
  { to: '/pro/subscricao', label: 'Subscrição', icon: Wallet },
];

/** Shell da área do prestador — sidebar em desktop, tabs horizontais em mobile. */
export function ProviderLayout() {
  return (
    <div className="flex min-h-dvh flex-col">
      <a href="#main-content" className="skip-link">
        Saltar para o conteúdo principal
      </a>
      <SiteHeader />
      <div className="mx-auto flex w-full max-w-[1280px] flex-1 flex-col gap-6 px-5 py-8 sm:px-8 lg:flex-row lg:px-10">
        <nav aria-label="Navegação da área do prestador" className="lg:w-56 lg:shrink-0">
          <ul className="flex gap-1 overflow-x-auto lg:flex-col lg:overflow-visible">
            {PROVIDER_NAV.map((item) => (
              <li key={item.to}>
                <NavLink
                  to={item.to}
                  end={item.end}
                  className={({ isActive }) =>
                    cn(
                      'flex items-center gap-2.5 whitespace-nowrap rounded-md px-3 py-2.5 text-sm font-medium text-muted hover:bg-surface-2 hover:text-foreground',
                      isActive && 'bg-orange-500/12 text-orange-600 light:text-orange-600',
                    )
                  }
                >
                  <item.icon aria-hidden="true" className="size-4" strokeWidth={1.5} />
                  {item.label}
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>
        <main id="main-content" className="min-w-0 flex-1">
          <Outlet />
        </main>
      </div>
      <SiteFooter />
      <DevMockPanel />
    </div>
  );
}

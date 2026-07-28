import { useState } from 'react';
import { LayoutDashboard, LogOut, Menu, X } from 'lucide-react';
import { Link, NavLink } from 'react-router-dom';
import { Avatar } from '../ui/Avatar';
import { ThemeToggle } from '../ui/ThemeToggle';
import { useAuth } from '../../features/auth/useAuth';
import { cn } from '../../lib/cn';

const NAV_LINKS = [
  { to: '/como-funciona', label: 'Como funciona' },
  { to: '/categorias', label: 'Categorias' },
  { to: '/prestadores', label: 'Prestadores' },
  { to: '/precos', label: 'Preços' },
];

export function SiteHeader() {
  const { status, user, logout } = useAuth();
  const [open, setOpen] = useState(false);
  const isProvider = user?.roles.includes('PROVIDER');
  const dashboardHref = isProvider ? '/pro' : '/painel';

  return (
    <header className="sticky top-0 z-40 border-b border-line bg-background/85 backdrop-blur-md">
      <div className="mx-auto flex h-16 max-w-[1280px] items-center justify-between px-5 sm:px-8 lg:px-10">
        <Link to="/" className="flex items-center gap-2 font-display text-lg font-extrabold tracking-tight">
          <span className="text-gradient-energy">Servi</span>
          <span>Match</span>
        </Link>

        <nav aria-label="Navegação principal" className="hidden items-center gap-7 lg:flex">
          {NAV_LINKS.map((link) => (
            <NavLink
              key={link.to}
              to={link.to}
              className={({ isActive }) =>
                cn(
                  'text-sm font-medium text-muted transition-colors hover:text-foreground',
                  isActive && 'text-foreground',
                )
              }
            >
              {link.label}
            </NavLink>
          ))}
        </nav>

        <div className="hidden items-center gap-3 lg:flex">
          <ThemeToggle />
          {status === 'authenticated' && user ? (
            <>
              <Link
                to={dashboardHref}
                className="inline-flex items-center gap-2 rounded-full border border-line px-4 py-2 text-sm font-medium text-foreground hover:border-orange-500/40"
              >
                <LayoutDashboard aria-hidden="true" className="size-4" strokeWidth={1.5} />
                {isProvider ? 'Painel do prestador' : 'O meu painel'}
              </Link>
              <Avatar name={user.displayName} imageUrl={user.avatarUrl} size="sm" />
              <button
                type="button"
                onClick={() => void logout()}
                aria-label="Terminar sessão"
                className="inline-flex size-11 items-center justify-center rounded-full text-muted hover:bg-surface-2 hover:text-foreground"
              >
                <LogOut aria-hidden="true" className="size-5" strokeWidth={1.5} />
              </button>
            </>
          ) : (
            <>
              <Link to="/entrar" className="text-sm font-medium text-foreground hover:text-orange-500">
                Entrar
              </Link>
              <Link
                to="/pedidos/novo"
                className="inline-flex h-11 items-center justify-center rounded-full bg-gradient-to-r from-orange-600 to-orange-400 px-5 text-sm font-medium text-accent-fg shadow-[0_0_40px_-12px_var(--color-orange-500)] hover:brightness-110"
              >
                Publicar um pedido
              </Link>
            </>
          )}
        </div>

        <button
          type="button"
          className="inline-flex size-11 items-center justify-center rounded-full text-foreground lg:hidden"
          aria-label="Abrir menu"
          aria-expanded={open}
          onClick={() => setOpen(true)}
        >
          <Menu aria-hidden="true" className="size-6" strokeWidth={1.5} />
        </button>
      </div>

      {open ? (
        <div className="fixed inset-0 z-50 flex flex-col bg-background lg:hidden">
          <div className="flex h-16 items-center justify-between px-5">
            <span className="font-display text-lg font-extrabold">
              <span className="text-gradient-energy">Servi</span>Match
            </span>
            <button
              type="button"
              aria-label="Fechar menu"
              onClick={() => setOpen(false)}
              className="inline-flex size-11 items-center justify-center rounded-full text-foreground"
            >
              <X aria-hidden="true" className="size-6" strokeWidth={1.5} />
            </button>
          </div>
          <nav aria-label="Navegação principal" className="flex flex-1 flex-col gap-1 px-5 py-4">
            {NAV_LINKS.map((link) => (
              <NavLink
                key={link.to}
                to={link.to}
                onClick={() => setOpen(false)}
                className="rounded-md px-3 py-3.5 text-lg font-medium text-foreground hover:bg-surface-2"
              >
                {link.label}
              </NavLink>
            ))}
            <div className="hairline my-4" />
            {status === 'authenticated' && user ? (
              <>
                <Link
                  to={dashboardHref}
                  onClick={() => setOpen(false)}
                  className="rounded-md px-3 py-3.5 text-lg font-medium text-foreground hover:bg-surface-2"
                >
                  {isProvider ? 'Painel do prestador' : 'O meu painel'}
                </Link>
                <button
                  type="button"
                  onClick={() => {
                    setOpen(false);
                    void logout();
                  }}
                  className="rounded-md px-3 py-3.5 text-left text-lg font-medium text-orange-600"
                >
                  Terminar sessão
                </button>
              </>
            ) : (
              <>
                <Link
                  to="/entrar"
                  onClick={() => setOpen(false)}
                  className="rounded-md px-3 py-3.5 text-lg font-medium text-foreground hover:bg-surface-2"
                >
                  Entrar
                </Link>
                <Link
                  to="/pedidos/novo"
                  onClick={() => setOpen(false)}
                  className="mt-2 inline-flex h-12 items-center justify-center rounded-full bg-gradient-to-r from-orange-600 to-orange-400 text-base font-medium text-accent-fg"
                >
                  Publicar um pedido
                </Link>
              </>
            )}
          </nav>
          <div className="flex items-center justify-between border-t border-line px-5 py-4">
            <span className="text-caption text-muted">Tema</span>
            <ThemeToggle />
          </div>
        </div>
      ) : null}
    </header>
  );
}

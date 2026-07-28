import { Link } from 'react-router-dom';
import { ThemeToggle } from '../ui/ThemeToggle';

const COLUMNS: { title: string; links: { to: string; label: string }[] }[] = [
  {
    title: 'Produto',
    links: [
      { to: '/como-funciona', label: 'Como funciona' },
      { to: '/categorias', label: 'Categorias' },
      { to: '/prestadores', label: 'Encontrar prestadores' },
      { to: '/precos', label: 'Preços' },
    ],
  },
  {
    title: 'Empresa',
    links: [
      { to: '/sobre', label: 'Sobre nós' },
      { to: '/contactos', label: 'Contactos' },
      { to: '/faq', label: 'Perguntas frequentes' },
    ],
  },
  {
    title: 'Legal',
    links: [
      { to: '/termos', label: 'Termos de utilização' },
      { to: '/privacidade', label: 'Privacidade' },
    ],
  },
  {
    title: 'Para prestadores',
    links: [
      { to: '/precos', label: 'Planos e preços' },
      { to: '/entrar', label: 'Área do prestador' },
    ],
  },
];

export function SiteFooter() {
  return (
    <footer className="hairline mt-24 bg-surface-2">
      <div className="mx-auto max-w-[1280px] px-5 py-16 sm:px-8 lg:px-10">
        <div className="grid grid-cols-2 gap-8 sm:grid-cols-4">
          {COLUMNS.map((column) => (
            <div key={column.title}>
              <p className="eyebrow text-muted">{column.title}</p>
              <ul className="mt-4 flex flex-col gap-2.5">
                {column.links.map((link) => (
                  <li key={link.to}>
                    <Link to={link.to} className="text-body text-muted hover:text-foreground">
                      {link.label}
                    </Link>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>

        <div className="hairline mt-12 flex flex-col items-start justify-between gap-4 pt-6 sm:flex-row sm:items-center">
          <p className="font-display text-base font-extrabold">
            <span className="text-gradient-energy">Servi</span>Match
          </p>
          <p className="text-caption text-muted">© {new Date().getFullYear()} ServiMatch. Todos os direitos reservados.</p>
          <ThemeToggle />
        </div>
      </div>
    </footer>
  );
}

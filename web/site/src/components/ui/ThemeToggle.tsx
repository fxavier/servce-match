import { Moon, Sun } from 'lucide-react';
import { useTheme } from '../../hooks/useTheme';

export function ThemeToggle() {
  const { theme, toggleTheme } = useTheme();
  const isDark = theme === 'dark';

  return (
    <button
      type="button"
      onClick={toggleTheme}
      aria-label={isDark ? 'Ativar tema claro' : 'Ativar tema escuro'}
      aria-pressed={!isDark}
      className="inline-flex size-11 items-center justify-center rounded-full border border-line text-muted transition-colors hover:border-orange-500/40 hover:text-foreground"
    >
      {isDark ? <Sun aria-hidden="true" className="size-5" strokeWidth={1.5} /> : <Moon aria-hidden="true" className="size-5" strokeWidth={1.5} />}
    </button>
  );
}

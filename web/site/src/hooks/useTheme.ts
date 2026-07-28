import { useCallback, useEffect, useState } from 'react';

export type Theme = 'dark' | 'light';
const STORAGE_KEY = 'sm-theme';

function readInitialTheme(): Theme {
  if (typeof document === 'undefined') return 'dark';
  return document.documentElement.classList.contains('light') ? 'light' : 'dark';
}

/**
 * Espelha o estado aplicado pelo script inline em `index.html` (que evita o
 * flash de tema errado antes da hidratação). `localStorage` aqui é a única
 * exceção admitida a "nunca localStorage" — só preferência de tema, nunca
 * sessão/tokens (CLAUDE.md §4, §5.3 da especificação).
 */
export function useTheme(): { theme: Theme; toggleTheme: () => void; setTheme: (theme: Theme) => void } {
  const [theme, setThemeState] = useState<Theme>(readInitialTheme);

  const applyTheme = useCallback((next: Theme) => {
    document.documentElement.classList.toggle('light', next === 'light');
    document.documentElement.style.colorScheme = next;
    try {
      localStorage.setItem(STORAGE_KEY, next);
    } catch {
      // Modo privado ou quota excedida — a preferência de tema simplesmente
      // não persiste; não é um erro que valha a pena mostrar ao utilizador.
    }
    setThemeState(next);
  }, []);

  useEffect(() => {
    applyTheme(readInitialTheme());
    // eslint-disable-next-line react-hooks/exhaustive-deps -- só corre uma vez, para sincronizar com o script inline.
  }, []);

  const toggleTheme = useCallback(() => applyTheme(theme === 'dark' ? 'light' : 'dark'), [theme, applyTheme]);

  return { theme, toggleTheme, setTheme: applyTheme };
}

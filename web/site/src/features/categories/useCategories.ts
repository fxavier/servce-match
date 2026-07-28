import { useQuery } from '@tanstack/react-query';
import { services } from '../../services';

/**
 * Único ponto de leitura de categorias no cliente — sempre via
 * `services.categories.list()` (mock ou HTTP real, conforme
 * `VITE_USE_MOCKS`), nunca por import direto de fixtures fora de
 * `services/` (checklist §11).
 */
export function useCategories() {
  return useQuery({
    queryKey: ['categories', 'all'],
    queryFn: () => services.categories.list(),
    staleTime: 5 * 60_000,
  });
}

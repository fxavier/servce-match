import { useQuery } from '@tanstack/react-query';
import { services } from '../../services';

/**
 * Único ponto de leitura de categorias no cliente — sempre via
 * `services.categories.list()` (HTTP real contra o BFF), nunca por acesso
 * direto a `services/http/*` fora de `services/`.
 */
export function useCategories() {
  return useQuery({
    queryKey: ['categories', 'all'],
    queryFn: () => services.categories.list(),
    staleTime: 5 * 60_000,
  });
}

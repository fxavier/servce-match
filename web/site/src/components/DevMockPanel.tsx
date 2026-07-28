import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { FlaskConical } from 'lucide-react';
import { USE_MOCKS } from '../services';
import { mockDb } from '../services/mock/db';
import { useAuth } from '../features/auth/useAuth';

/**
 * Painel de dev (§7 — "Inclui um toggle de dev… para ambos os estados
 * poderem ser vistos"). Só existe em modo mock e só para prestadores — nunca
 * aparece em produção (`VITE_USE_MOCKS=false` remove este componente da
 * árvore, ver ProviderLayout).
 */
export function DevMockPanel() {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const [active, setActive] = useState(mockDb.providerSubscriptionActive);

  if (!USE_MOCKS || !user?.roles.includes('PROVIDER')) return null;

  function toggle() {
    const next = !active;
    mockDb.setProviderSubscriptionActive(next);
    setActive(next);
    void queryClient.invalidateQueries();
  }

  return (
    <div className="fixed bottom-4 right-4 z-30 flex items-center gap-3 rounded-full border border-line bg-surface px-4 py-2.5 shadow-card">
      <FlaskConical aria-hidden="true" className="size-4 text-orange-500" strokeWidth={1.5} />
      <label htmlFor="dev-mock-subscription-toggle" className="text-caption font-medium text-foreground">
        Simular subscrição ativa
      </label>
      <button
        id="dev-mock-subscription-toggle"
        type="button"
        role="switch"
        aria-checked={active}
        onClick={toggle}
        className={`relative h-6 w-11 rounded-full transition-colors ${active ? 'bg-orange-500' : 'bg-line'}`}
      >
        <span
          className={`absolute top-0.5 size-5 rounded-full bg-white transition-transform ${active ? 'translate-x-[22px]' : 'translate-x-0.5'}`}
        />
      </button>
    </div>
  );
}

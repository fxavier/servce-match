import { useEffect, useState } from 'react';
import { api } from '../api/client';
import type { components } from '../api/generated/schema';
import { EmptyState } from '../components/EmptyState';
import { LoadingState } from '../components/LoadingState';
import { ProblemAlert } from '../components/ProblemAlert';
import { formatDateTime } from '../lib/dates';
import { isProblemDetails, type ProblemDetails } from '../lib/problem';

type ServiceRequest = components['schemas']['ServiceRequest'];

function toProblem(error: unknown): ProblemDetails {
  if (isProblemDetails(error)) return error;
  return {
    type: 'https://errors.servimatch.pt/unknown',
    title: 'Não foi possível carregar a caixa de entrada.',
    status: 0,
  };
}

/**
 * Caixa de entrada do prestador. O servidor é quem decide se a subscrição
 * está ativa (403 `subscription-required`) — esta página só reage ao que
 * ele responde, nunca decide sozinha se o utilizador "deveria" ver isto
 * (CLAUDE.md §4).
 */
export function ProviderInboxPage() {
  const [items, setItems] = useState<ServiceRequest[] | undefined>(undefined);
  const [problem, setProblem] = useState<ProblemDetails | undefined>(undefined);

  useEffect(() => {
    let cancelled = false;
    api
      .GET('/v1/providers/me/requests')
      .then(({ data, error }) => {
        if (cancelled) return;
        if (error) {
          setProblem(toProblem(error));
          return;
        }
        setItems(data.items);
      })
      .catch(() => {
        if (!cancelled) setProblem(toProblem(undefined));
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <main>
      <h1>Caixa de entrada</h1>

      {problem ? (
        <ProblemAlert problem={problem} />
      ) : items === undefined ? (
        <LoadingState label="A carregar pedidos elegíveis…" />
      ) : items.length === 0 ? (
        <EmptyState>Sem pedidos elegíveis para ti neste momento.</EmptyState>
      ) : (
        <ul>
          {items.map((item) => (
            <li key={item.id}>
              <p>{item.title}</p>
              <p>{item.category?.name}</p>
              <p>Publicado {item.publishedAt ? formatDateTime(item.publishedAt) : '—'}</p>
            </li>
          ))}
        </ul>
      )}
    </main>
  );
}

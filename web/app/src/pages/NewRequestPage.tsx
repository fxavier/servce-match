import { useEffect, useId, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import type { components } from '../api/generated/schema';
import { LoadingState } from '../components/LoadingState';
import { ProblemAlert } from '../components/ProblemAlert';
import { isProblemDetails, type ProblemDetails } from '../lib/problem';

type Category = components['schemas']['Category'];
type UrgencyLevel = components['schemas']['UrgencyLevel'];

const URGENCY_OPTIONS: { value: UrgencyLevel; label: string }[] = [
  { value: 'LOW', label: 'Sem pressa' },
  { value: 'NORMAL', label: 'Normal' },
  { value: 'HIGH', label: 'Prioritário' },
  { value: 'URGENT', label: 'Urgente' },
];

function toProblem(error: unknown): ProblemDetails {
  if (isProblemDetails(error)) return error;
  return {
    type: 'https://errors.servimatch.pt/unknown',
    title: 'Não foi possível completar o pedido.',
    status: 0,
  };
}

export function NewRequestPage() {
  const navigate = useNavigate();
  const titleId = useId();
  const categoryId = useId();
  const descriptionId = useId();
  const cityId = useId();
  const postalCodeId = useId();
  const urgencyId = useId();

  const [categories, setCategories] = useState<Category[] | undefined>(undefined);
  const [categoriesError, setCategoriesError] = useState<ProblemDetails | undefined>(undefined);

  const [title, setTitle] = useState('');
  const [selectedCategoryId, setSelectedCategoryId] = useState('');
  const [description, setDescription] = useState('');
  const [city, setCity] = useState('');
  const [postalCode, setPostalCode] = useState('');
  const [urgency, setUrgency] = useState<UrgencyLevel | ''>('');

  const [draftId, setDraftId] = useState<string | undefined>(undefined);
  const [submitting, setSubmitting] = useState(false);
  const [problem, setProblem] = useState<ProblemDetails | undefined>(undefined);

  useEffect(() => {
    let cancelled = false;
    api
      .GET('/v1/categories')
      .then(({ data, error }) => {
        if (cancelled) return;
        if (error) {
          setCategoriesError(toProblem(error));
          return;
        }
        setCategories(data);
      })
      .catch(() => {
        if (!cancelled) setCategoriesError(toProblem(undefined));
      });
    return () => {
      cancelled = true;
    };
  }, []);

  async function publish(id: string) {
    const { error } = await api.POST('/v1/requests/{requestId}/publish', {
      params: { path: { requestId: id } },
    });
    if (error) {
      setProblem(toProblem(error));
      return;
    }
    navigate(`/requests/${id}`);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setProblem(undefined);
    setSubmitting(true);
    try {
      if (draftId) {
        // O pedido já foi criado (DRAFT) numa submissão anterior; só falta publicar.
        await publish(draftId);
        return;
      }

      const { data, error } = await api.POST('/v1/requests', {
        body: {
          categoryId: selectedCategoryId,
          title,
          description: description || undefined,
          address: {
            city: city || undefined,
            postalCode: postalCode || undefined,
            country: 'PT',
          },
          urgency: urgency || undefined,
        },
      });

      if (error) {
        setProblem(toProblem(error));
        return;
      }

      setDraftId(data.id);
      await publish(data.id);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main>
      <h1>Publicar um pedido</h1>

      {categoriesError ? <ProblemAlert problem={categoriesError} /> : null}
      {problem ? <ProblemAlert problem={problem} /> : null}

      {categories === undefined && !categoriesError ? (
        <LoadingState label="A carregar categorias…" />
      ) : (
        <form onSubmit={handleSubmit} noValidate>
          <div>
            <label htmlFor={categoryId}>Categoria</label>
            <select
              id={categoryId}
              value={selectedCategoryId}
              onChange={(event) => setSelectedCategoryId(event.target.value)}
              required
              disabled={submitting || Boolean(draftId)}
            >
              <option value="">Escolhe uma categoria</option>
              {categories?.map((category) => (
                <option key={category.id} value={category.id}>
                  {category.name}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label htmlFor={titleId}>Título do pedido</label>
            <input
              id={titleId}
              type="text"
              value={title}
              onChange={(event) => setTitle(event.target.value)}
              minLength={3}
              maxLength={140}
              required
              disabled={submitting || Boolean(draftId)}
            />
          </div>

          <div>
            <label htmlFor={descriptionId}>Descrição</label>
            <textarea
              id={descriptionId}
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              maxLength={4000}
              disabled={submitting || Boolean(draftId)}
            />
          </div>

          <div>
            <label htmlFor={cityId}>Cidade</label>
            <input
              id={cityId}
              type="text"
              value={city}
              onChange={(event) => setCity(event.target.value)}
              required
              disabled={submitting || Boolean(draftId)}
            />
          </div>

          <div>
            <label htmlFor={postalCodeId}>Código postal</label>
            <input
              id={postalCodeId}
              type="text"
              value={postalCode}
              onChange={(event) => setPostalCode(event.target.value)}
              placeholder="1000-001"
              disabled={submitting || Boolean(draftId)}
            />
          </div>

          <div>
            <label htmlFor={urgencyId}>Urgência</label>
            <select
              id={urgencyId}
              value={urgency}
              onChange={(event) => setUrgency(event.target.value as UrgencyLevel | '')}
              disabled={submitting || Boolean(draftId)}
            >
              <option value="">Não especificar</option>
              {URGENCY_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </div>

          <button type="submit" disabled={submitting}>
            {submitting ? 'A publicar…' : draftId ? 'Tentar publicar novamente' : 'Publicar pedido'}
          </button>
        </form>
      )}
    </main>
  );
}

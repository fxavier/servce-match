import { type FormEvent, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Field } from '../../components/ui/Field';
import { Input } from '../../components/ui/Input';
import { Textarea } from '../../components/ui/Textarea';
import { ErrorState } from '../../components/ui/ErrorState';
import { RatingStars } from '../../components/ui/RatingStars';
import { eurosToCents, formatMoney } from '../../lib/money';
import { fieldErrorsFrom, isSubscriptionRequired, throwProblem, toProblem } from '../../lib/problem';
import { services } from '../../services';
import type { Proposal } from '../../services/types';

export interface ProposalFormProps {
  requestId: string;
  providerName: string;
  providerRating: number;
  providerRatingCount: number;
  onSubscriptionRequired: () => void;
  onSuccess: (proposal: Proposal) => void;
}

export function ProposalForm({ requestId, providerName, providerRating, providerRatingCount, onSubscriptionRequired, onSuccess }: ProposalFormProps) {
  const queryClient = useQueryClient();
  const [priceInput, setPriceInput] = useState('');
  const [description, setDescription] = useState('');
  const [leadTimeDays, setLeadTimeDays] = useState('3');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const cents = eurosToCents(priceInput);

  const mutation = useMutation({
    mutationFn: async () => {
      if (cents === undefined) {
        throwProblem({
          title: 'Preço inválido.',
          status: 422,
          errors: [{ field: 'price', message: 'Introduza um preço válido, ex.: 75,00' }],
        });
      }
      return services.proposals.create(requestId, {
        price: { amountCents: cents, currency: 'EUR' },
        description,
        leadTimeDays: Number(leadTimeDays) || undefined,
      });
    },
    onSuccess: (proposal) => {
      void queryClient.invalidateQueries({ queryKey: ['provider-inbox'] });
      onSuccess(proposal);
    },
    onError: (error) => {
      const problem = toProblem(error);
      if (isSubscriptionRequired(problem)) {
        onSubscriptionRequired();
        return;
      }
      setFieldErrors(Object.fromEntries(fieldErrorsFrom(problem).map((entry) => [entry.field, entry.message])));
    },
  });

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFieldErrors({});
    mutation.mutate();
  }

  return (
    <div className="grid gap-8 lg:grid-cols-[1fr_320px]">
      <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-4">
        <Field id="proposal-price" label="Preço (EUR)" required error={fieldErrors.price} hint="Ex.: 75,00">
          <Input
            id="proposal-price"
            inputMode="decimal"
            value={priceInput}
            onChange={(event) => setPriceInput(event.target.value)}
            invalid={Boolean(fieldErrors.price)}
            placeholder="0,00"
          />
        </Field>
        <Field id="proposal-description" label="Descrição do orçamento" required error={fieldErrors.description}>
          <Textarea
            id="proposal-description"
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            invalid={Boolean(fieldErrors.description)}
            rows={5}
            maxLength={2000}
          />
        </Field>
        <Field id="proposal-lead-time" label="Prazo estimado (dias)" error={fieldErrors.leadTimeDays}>
          <Input
            id="proposal-lead-time"
            type="number"
            min={0}
            value={leadTimeDays}
            onChange={(event) => setLeadTimeDays(event.target.value)}
            invalid={Boolean(fieldErrors.leadTimeDays)}
          />
        </Field>

        {mutation.isError && !isSubscriptionRequired(toProblem(mutation.error)) ? (
          <ErrorState problem={toProblem(mutation.error)} />
        ) : null}

        <button
          type="submit"
          disabled={mutation.isPending}
          className="inline-flex h-11 items-center justify-center self-start rounded-full bg-gradient-to-r from-orange-600 to-orange-400 px-6 text-sm font-medium text-accent-fg disabled:opacity-60"
        >
          {mutation.isPending ? 'A enviar…' : 'Enviar orçamento'}
        </button>
      </form>

      <div>
        <p className="eyebrow text-muted">PRÉ-VISUALIZAÇÃO</p>
        <div className="surface-card mt-2 p-5">
          <p className="text-card-title font-display font-semibold text-foreground">{providerName}</p>
          <RatingStars value={providerRating} count={providerRatingCount} className="mt-1" />
          <p className="mt-3 font-display text-2xl font-bold tabular-nums text-foreground">
            {cents !== undefined ? formatMoney({ amountCents: cents, currency: 'EUR' }) : '— €'}
          </p>
          {leadTimeDays ? <p className="mt-1 text-caption text-muted">Prazo: {leadTimeDays} dia(s)</p> : null}
          {description ? <p className="mt-3 text-body text-muted">{description}</p> : null}
        </div>
      </div>
    </div>
  );
}

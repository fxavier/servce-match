import { useState } from 'react';
import { zodResolver } from '@hookform/resolvers/zod';
import { CheckCircle2, ShieldAlert } from 'lucide-react';
import { useForm } from 'react-hook-form';
import { useMutation } from '@tanstack/react-query';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { ErrorState } from '../../components/ui/ErrorState';
import { Field } from '../../components/ui/Field';
import { Input } from '../../components/ui/Input';
import { Textarea } from '../../components/ui/Textarea';
import { formatDateTime } from '../../lib/dates';
import { fieldErrorsFrom, toProblem } from '../../lib/problem';
import { services } from '../../services';
import type { ProviderApproval, ProviderApprovalDecision } from '../../services/types';
import { decisionSchema, type DecisionFormValues } from './adminApprovalSchemas';

interface DecisionOption {
  value: ProviderApprovalDecision;
  label: string;
  /**
   * Descreve o estado de origem que a transição pressupõe (PENDING →
   * APPROVED|REJECTED, APPROVED → SUSPENDED — CLAUDE.md, ONDA-C1 §1). É só
   * rótulo informativo: sem um `GET` que devolva o `approvalStatus` atual
   * do prestador, o cliente não tem como confirmar o estado de partida —
   * o servidor continua a ser a única autoridade e recusa com `409`
   * qualquer transição que não seja esta.
   */
  fromHint: string;
}

const DECISION_OPTIONS: DecisionOption[] = [
  { value: 'APPROVED', label: 'Aprovar', fromHint: 'de Pendente' },
  { value: 'REJECTED', label: 'Rejeitar', fromHint: 'de Pendente' },
  { value: 'SUSPENDED', label: 'Suspender', fromHint: 'de Aprovado' },
];

const STATUS_TONE: Record<ProviderApproval['approvalStatus'], 'success' | 'warning' | 'neutral'> = {
  PENDING: 'neutral',
  APPROVED: 'success',
  REJECTED: 'warning',
  SUSPENDED: 'warning',
};

/**
 * Consola de decisão administrativa sobre `approval_status` (defeito C1,
 * `docs/ESTADO-DO-SISTEMA.md`). `PATCH /v1/admin/providers/{id}/approval`
 * (`decideProviderApproval`, `openapi.yaml:800`) é a ÚNICA operação `Admin`
 * que o contrato expõe hoje.
 *
 * Lacuna de contrato conhecida (reportada, não contornada aqui): não existe
 * `GET` para listar prestadores `PENDING` nem para ver o detalhe de um
 * prestador que ainda não esteja publicamente visível — `GET
 * /v1/providers/{id}` usa o mesmo predicado de `GET /v1/search/providers` e
 * devolve `404` para quem não está `APPROVED` com subscrição ativa. Por
 * isso este ecrã não é um "worklist" com lista de pendentes: é uma consola
 * que decide sobre um `providerId` que o administrador já conhece (ex. por
 * um canal fora desta aplicação). Nada aqui fabrica uma lista nem um
 * detalhe que o servidor não devolve (CLAUDE.md — "sem camada de mocks",
 * M16).
 */
export function AdminApprovalConsole() {
  const [idempotencyKey, setIdempotencyKey] = useState(() => crypto.randomUUID());
  const [result, setResult] = useState<ProviderApproval | undefined>(undefined);

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    reset,
    formState: { errors, isSubmitting },
    setError,
  } = useForm<DecisionFormValues>({
    resolver: zodResolver(decisionSchema),
    defaultValues: { providerId: '', decision: 'APPROVED', reason: '' },
  });

  const decision = watch('decision');

  const mutation = useMutation({
    mutationFn: (values: DecisionFormValues) =>
      services.admin.decideProviderApproval(
        values.providerId,
        { decision: values.decision, reason: values.reason ? values.reason : undefined },
        idempotencyKey,
      ),
    onSuccess: (approval) => {
      setResult(approval);
      // Nova decisão = novo pedido lógico: gera uma Idempotency-Key nova
      // para não reaproveitar a de uma decisão já concluída. Uma repetição
      // (duplo clique, retry de rede) da MESMA decisão continua a usar a
      // chave corrente até aqui — é isso que a torna eficaz.
      setIdempotencyKey(crypto.randomUUID());
      reset({ providerId: '', decision: 'APPROVED', reason: '' });
    },
    onError: (error) => {
      const problem = toProblem(error);
      // 422 do servidor (ex. `reason` em falta) mapeia para o campo — a
      // validação do cliente já cobre o caso comum, isto é rede de
      // segurança para drift entre contrato e cliente.
      for (const entry of fieldErrorsFrom(problem)) {
        if (entry.field === 'reason' || entry.field === 'decision' || entry.field === 'providerId') {
          setError(entry.field, { message: entry.message });
        }
      }
    },
  });

  function onSubmit(values: DecisionFormValues) {
    setResult(undefined);
    mutation.mutate(values);
  }

  const problem = mutation.isError ? toProblem(mutation.error) : undefined;
  // Erros de campo (422) já aparecem junto do campo — não duplicar como
  // ErrorState genérico. 409 (transição inválida) e 404 (prestador
  // inexistente) continuam a aparecer como ErrorState.
  const showGenericError = problem && fieldErrorsFrom(problem).length === 0;

  return (
    <div className="grid gap-8 lg:grid-cols-[1fr_360px]">
      <form
        onSubmit={(event) => void handleSubmit(onSubmit)(event)}
        noValidate
        className="surface-card flex flex-col gap-4 p-6"
      >
        <Field
          id="admin-provider-id"
          label="Identificador do prestador"
          required
          error={errors.providerId?.message}
          hint="UUID do prestador (`providerId`) — obtido fora desta aplicação, ver nota acima."
        >
          <Input
            id="admin-provider-id"
            invalid={Boolean(errors.providerId)}
            placeholder="00000000-0000-0000-0000-000000000000"
            autoComplete="off"
            {...register('providerId')}
          />
        </Field>

        <div>
          <span className="text-sm font-medium text-foreground">Decisão</span>
          <div role="radiogroup" aria-label="Decisão" className="mt-1.5 flex flex-col gap-2 sm:flex-row">
            {DECISION_OPTIONS.map((option) => {
              const isSelected = decision === option.value;
              return (
                <button
                  key={option.value}
                  type="button"
                  role="radio"
                  aria-checked={isSelected}
                  aria-label={option.label}
                  onClick={() => setValue('decision', option.value, { shouldValidate: true })}
                  className={`flex-1 rounded-md border px-3.5 py-2.5 text-left text-sm transition-colors ${
                    isSelected ? 'border-orange-500 bg-orange-500/8' : 'border-line hover:border-orange-500/30'
                  }`}
                >
                  <span aria-hidden="true" className="block font-semibold text-foreground">
                    {option.label}
                  </span>
                  <span aria-hidden="true" className="block text-caption text-muted">
                    {option.fromHint}
                  </span>
                </button>
              );
            })}
          </div>
          <input type="hidden" {...register('decision')} />
        </div>

        <Field
          id="admin-reason"
          label="Motivo"
          required={decision !== 'APPROVED'}
          error={errors.reason?.message}
          hint={decision === 'APPROVED' ? 'Opcional para aprovar.' : 'Obrigatório — o servidor recusa sem motivo (422).'}
        >
          <Textarea
            id="admin-reason"
            invalid={Boolean(errors.reason)}
            rows={4}
            maxLength={2000}
            {...register('reason')}
          />
        </Field>

        {showGenericError && problem ? <ErrorState problem={problem} /> : null}

        <Button type="submit" disabled={isSubmitting || mutation.isPending} loading={isSubmitting || mutation.isPending} className="self-start">
          Registar decisão
        </Button>
      </form>

      <div>
        <p className="eyebrow text-muted">RESULTADO</p>
        {result ? (
          <div className="surface-card mt-2 flex flex-col gap-3 p-5">
            <div className="flex items-center gap-2">
              <CheckCircle2 aria-hidden="true" className="size-5 text-success" strokeWidth={1.5} />
              <p className="text-card-title font-display font-semibold text-foreground">Decisão registada</p>
            </div>
            <dl className="flex flex-col gap-2 text-caption">
              <div className="flex items-center justify-between gap-2">
                <dt className="text-muted">Estado</dt>
                <dd>
                  <Badge tone={STATUS_TONE[result.approvalStatus]}>{result.approvalStatus}</Badge>
                </dd>
              </div>
              <div className="flex items-center justify-between gap-2">
                <dt className="text-muted">Decidido por</dt>
                <dd className="font-mono text-foreground">{result.decidedBy}</dd>
              </div>
              <div className="flex items-center justify-between gap-2">
                <dt className="text-muted">Quando</dt>
                <dd className="text-foreground">{formatDateTime(result.decidedAt)}</dd>
              </div>
              {result.reason ? (
                <div>
                  <dt className="text-muted">Motivo</dt>
                  <dd className="mt-1 text-foreground">{result.reason}</dd>
                </div>
              ) : null}
            </dl>
          </div>
        ) : (
          <div className="surface-card mt-2 flex flex-col items-center gap-2 p-5 text-center">
            <ShieldAlert aria-hidden="true" className="size-6 text-muted" strokeWidth={1.5} />
            <p className="text-caption text-muted">A decisão aplicada aparece aqui.</p>
          </div>
        )}
      </div>
    </div>
  );
}

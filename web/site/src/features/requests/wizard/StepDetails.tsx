import { Field } from '../../../components/ui/Field';
import { Input } from '../../../components/ui/Input';
import { Textarea } from '../../../components/ui/Textarea';
import { Chip } from '../../../components/ui/Chip';
import type { UrgencyLevel } from '../../../services/types';

export interface StepDetailsValue {
  title: string;
  description: string;
  urgency: UrgencyLevel;
  availability: string;
}

export interface StepDetailsProps {
  value: StepDetailsValue;
  onChange: (patch: Partial<StepDetailsValue>) => void;
  errors: Partial<Record<keyof StepDetailsValue, string>>;
}

const URGENCY_OPTIONS: { value: UrgencyLevel; label: string }[] = [
  { value: 'LOW', label: 'Sem pressa' },
  { value: 'NORMAL', label: 'Normal' },
  { value: 'HIGH', label: 'Prioritário' },
  { value: 'URGENT', label: 'Urgente' },
];

export function StepDetails({ value, onChange, errors }: StepDetailsProps) {
  return (
    <div className="flex flex-col gap-5">
      <div>
        <h2 className="text-h2 font-display font-bold text-foreground">Descreva o pedido</h2>
        <p className="mt-2 text-body text-muted">Quanto mais detalhe, mais rigorosos são os orçamentos que recebe.</p>
      </div>

      <Field id="wizard-title" label="Título do pedido" required error={errors.title}>
        <Input
          id="wizard-title"
          value={value.title}
          onChange={(event) => onChange({ title: event.target.value })}
          invalid={Boolean(errors.title)}
          placeholder="Ex.: Fuga de água por baixo do lava-loiça"
          maxLength={140}
        />
      </Field>

      <Field id="wizard-description" label="Descrição" hint="Opcional, mas ajuda a receber orçamentos mais precisos." error={errors.description}>
        <Textarea
          id="wizard-description"
          value={value.description}
          onChange={(event) => onChange({ description: event.target.value })}
          invalid={Boolean(errors.description)}
          rows={5}
          maxLength={4000}
        />
      </Field>

      <div>
        <span className="text-sm font-medium text-foreground">Urgência</span>
        <div role="radiogroup" aria-label="Urgência" className="mt-2 flex flex-wrap gap-2">
          {URGENCY_OPTIONS.map((option) => (
            <Chip
              key={option.value}
              type="button"
              selected={value.urgency === option.value}
              onClick={() => onChange({ urgency: option.value })}
            >
              {option.label}
            </Chip>
          ))}
        </div>
      </div>

      <Field id="wizard-availability" label="Disponibilidade" hint="Ex.: dias úteis depois das 18h, fins de semana." error={errors.availability}>
        <Input
          id="wizard-availability"
          value={value.availability}
          onChange={(event) => onChange({ availability: event.target.value })}
          invalid={Boolean(errors.availability)}
        />
      </Field>
    </div>
  );
}

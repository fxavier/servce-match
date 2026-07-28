import { z } from 'zod';

export const stepCategorySchema = z.object({
  categoryId: z.string().min(1, 'Escolha uma categoria para continuar.'),
});

export const stepDetailsSchema = z.object({
  title: z
    .string()
    .min(3, 'O título tem de ter pelo menos 3 caracteres.')
    .max(140, 'O título não pode ter mais de 140 caracteres.'),
  description: z.string().max(4000, 'A descrição não pode ter mais de 4000 caracteres.').optional(),
  urgency: z.enum(['LOW', 'NORMAL', 'HIGH', 'URGENT']),
  availability: z.string().max(280, 'Demasiado longo — resuma a disponibilidade.').optional(),
});

export const stepAddressSchema = z.object({
  line1: z.string().min(3, 'Indique a morada (rua e número).'),
  postalCode: z
    .string()
    .regex(/^\d{4}-\d{3}$/, 'Formato esperado: 1000-001.'),
  city: z.string().min(1, 'Indique a localidade.'),
  regionCode: z.string().min(1, 'Escolha o concelho.'),
});

export const wizardDraftSchema = z.object({
  categoryId: z.string().default(''),
  title: z.string().default(''),
  description: z.string().default(''),
  urgency: z.enum(['LOW', 'NORMAL', 'HIGH', 'URGENT']).default('NORMAL'),
  availability: z.string().default(''),
  line1: z.string().default(''),
  postalCode: z.string().default(''),
  city: z.string().default(''),
  regionCode: z.string().default(''),
  lat: z.number().default(38.7223),
  lon: z.number().default(-9.1393),
  imageNames: z.array(z.string()).default([]),
  pendingPublish: z.boolean().default(false),
});

export type WizardDraft = z.infer<typeof wizardDraftSchema>;

export const EMPTY_DRAFT: WizardDraft = wizardDraftSchema.parse({});

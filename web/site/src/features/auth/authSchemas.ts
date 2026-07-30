import { z } from 'zod';

/**
 * Validação de conveniência no cliente (mesmo estilo de
 * `features/requests/wizardSchemas.ts`) — a autoridade é sempre o servidor
 * (BFF/Keycloak). Nunca se tenta aqui distinguir "email não existe" de
 * "password errada": isso é decisão do servidor (ADR-0012 D7.3).
 */
export const loginSchema = z.object({
  email: z.string().min(1, 'Indique o seu email.').email('Introduza um email válido.'),
  password: z.string().min(1, 'Indique a sua password.'),
});

export type LoginFormValues = z.infer<typeof loginSchema>;

/**
 * Espelha (só para feedback imediato) a política real do realm — `length(10)`,
 * `digits(1)`, `lowerCase(1)`, `upperCase(1)`, `specialChars(1)` — validada
 * de novo e com autoridade em `web/bff/src/passwordPolicy.ts`. Duplicar a
 * regra aqui é conveniência, não substitui essa validação: um pedido pode
 * sempre voltar do servidor com `weak-password` mesmo que este schema
 * passe (ex. password que contém o email).
 */
const passwordSchema = z
  .string()
  .min(10, 'A password tem de ter pelo menos 10 caracteres.')
  .regex(/[0-9]/, 'A password tem de incluir pelo menos um número.')
  .regex(/[a-z]/, 'A password tem de incluir pelo menos uma letra minúscula.')
  .regex(/[A-Z]/, 'A password tem de incluir pelo menos uma letra maiúscula.')
  .regex(/[^A-Za-z0-9]/, 'A password tem de incluir pelo menos um caráter especial.');

export const registerSchema = z
  .object({
    name: z.string().min(2, 'Indique o seu nome.').max(120, 'Nome demasiado longo.'),
    email: z.string().min(1, 'Indique o seu email.').email('Introduza um email válido.'),
    password: passwordSchema,
    confirmPassword: z.string().min(1, 'Confirme a password.'),
    role: z.enum(['CUSTOMER', 'PROVIDER']),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: 'As passwords não coincidem.',
    path: ['confirmPassword'],
  });

export type RegisterFormValues = z.infer<typeof registerSchema>;

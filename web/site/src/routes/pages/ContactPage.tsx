import { zodResolver } from '@hookform/resolvers/zod';
import { MessageCircle, Send } from 'lucide-react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { Seo } from '../../components/Seo';
import { Reveal } from '../../components/motion/Reveal';
import { Field } from '../../components/ui/Field';
import { Input } from '../../components/ui/Input';
import { Textarea } from '../../components/ui/Textarea';

const contactSchema = z.object({
  name: z.string().min(2, 'Indique o seu nome.'),
  email: z.string().email('Introduza um email válido.'),
  message: z.string().min(10, 'Escreva pelo menos 10 caracteres.'),
});

type ContactForm = z.infer<typeof contactSchema>;

export function ContactPage() {
  const [sent, setSent] = useState(false);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<ContactForm>({ resolver: zodResolver(contactSchema) });

  async function onSubmit() {
    await new Promise((resolve) => setTimeout(resolve, 500));
    setSent(true);
  }

  return (
    <div className="mx-auto grid max-w-[1280px] gap-12 px-5 py-[clamp(3rem,6vw,5rem)] sm:px-8 lg:grid-cols-2 lg:px-10">
      <Seo title="Contactos" description="Fale com a equipa do ServiMatch." canonicalPath="/contactos" />
      <Reveal>
        <p className="eyebrow text-signal-500">FALE CONNOSCO</p>
        <h1 className="mt-3 text-h1 font-display font-bold text-foreground">Contactos</h1>
        <p className="mt-3 max-w-md text-body text-muted">
          Dúvidas sobre um pedido, uma subscrição ou uma proposta de parceria — respondemos o mais rápido possível.
        </p>
        <a
          href="https://wa.me/351900000000"
          target="_blank"
          rel="noreferrer"
          className="mt-6 inline-flex h-11 items-center gap-2 rounded-full border border-whatsapp/30 bg-whatsapp/12 px-5 text-sm font-medium text-whatsapp-fg"
        >
          <MessageCircle aria-hidden="true" className="size-4" strokeWidth={1.5} />
          Falar por WhatsApp
        </a>
        <dl className="mt-8 flex flex-col gap-3 text-body text-muted">
          <div>
            <dt className="font-medium text-foreground">Email</dt>
            <dd>ola@servimatch.pt</dd>
          </div>
          <div>
            <dt className="font-medium text-foreground">Horário de suporte</dt>
            <dd>Dias úteis, 9h–19h</dd>
          </div>
        </dl>
      </Reveal>

      <Reveal delay={0.05}>
        {sent ? (
          <div className="surface-card p-6">
            <p className="text-card-title font-display font-semibold text-foreground">Mensagem enviada</p>
            <p className="mt-2 text-body text-muted">Obrigado pelo contacto — respondemos por email em breve.</p>
          </div>
        ) : (
          <form onSubmit={(event) => void handleSubmit(onSubmit)(event)} noValidate className="surface-card flex flex-col gap-4 p-6">
            <Field id="contact-name" label="Nome" required error={errors.name?.message}>
              <Input id="contact-name" invalid={Boolean(errors.name)} {...register('name')} />
            </Field>
            <Field id="contact-email" label="Email" required error={errors.email?.message}>
              <Input id="contact-email" type="email" invalid={Boolean(errors.email)} {...register('email')} />
            </Field>
            <Field id="contact-message" label="Mensagem" required error={errors.message?.message}>
              <Textarea id="contact-message" rows={5} invalid={Boolean(errors.message)} {...register('message')} />
            </Field>
            <button
              type="submit"
              disabled={isSubmitting}
              className="inline-flex h-11 items-center justify-center gap-1.5 self-start rounded-full bg-gradient-to-r from-orange-600 to-orange-400 px-5 text-sm font-medium text-accent-fg disabled:opacity-50"
            >
              {isSubmitting ? 'A enviar…' : 'Enviar mensagem'}
              <Send aria-hidden="true" className="size-4" strokeWidth={1.5} />
            </button>
          </form>
        )}
      </Reveal>
    </div>
  );
}

import { type FormEvent, useEffect, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Send } from 'lucide-react';
import { Link, useParams } from 'react-router-dom';
import { Seo } from '../../components/Seo';
import { ErrorState } from '../../components/ui/ErrorState';
import { SkeletonText } from '../../components/ui/Skeleton';
import { useAuth } from '../../features/auth/useAuth';
import { cn } from '../../lib/cn';
import { formatDateTime } from '../../lib/dates';
import { toProblem } from '../../lib/problem';
import { services } from '../../services';

export function ConversationThreadPage() {
  const { conversationId } = useParams<{ conversationId: string }>();
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const [draft, setDraft] = useState('');
  const [showTyping, setShowTyping] = useState(false);
  const bottomRef = useRef<HTMLDivElement>(null);

  const messagesQuery = useQuery({
    queryKey: ['messages', conversationId],
    queryFn: () => services.chat.listMessages(conversationId as string),
    enabled: Boolean(conversationId),
    refetchInterval: 2500,
  });

  const sendMutation = useMutation({
    mutationFn: (body: string) => services.chat.sendMessage(conversationId as string, { body }),
    onSuccess: () => {
      setDraft('');
      setShowTyping(true);
      setTimeout(() => setShowTyping(false), 2200);
      void queryClient.invalidateQueries({ queryKey: ['messages', conversationId] });
    },
  });

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ block: 'end' });
  }, [messagesQuery.data]);

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!draft.trim()) return;
    sendMutation.mutate(draft.trim());
  }

  if (!conversationId) return null;

  return (
    <div className="mx-auto flex h-[calc(100vh-8.5rem)] max-w-2xl flex-col px-5 sm:px-8">
      <Seo title="Conversa" description="Conversa com o prestador." />
      <div className="flex items-center gap-3 border-b border-line py-4">
        <Link to="/conversas" className="text-sm font-medium text-muted hover:text-foreground">
          ← Conversas
        </Link>
      </div>

      <div className="flex-1 overflow-y-auto py-4">
        {messagesQuery.error ? (
          <ErrorState problem={toProblem(messagesQuery.error)} onRetry={() => void messagesQuery.refetch()} />
        ) : messagesQuery.isLoading ? (
          <SkeletonText lines={5} />
        ) : (
          <ul className="flex flex-col gap-3">
            {messagesQuery.data?.items
              .slice()
              .reverse()
              .map((message) => {
                const mine = message.senderId === user?.id;
                return (
                  <li key={message.id} className={cn('flex flex-col', mine ? 'items-end' : 'items-start')}>
                    <div
                      className={cn(
                        'max-w-[80%] rounded-lg px-4 py-2.5 text-body',
                        mine ? 'bg-orange-500 text-accent-fg' : 'bg-surface-2 text-foreground',
                      )}
                    >
                      {message.body}
                    </div>
                    <p className="mt-1 font-mono text-eyebrow tracking-[0.08em] text-muted">
                      {formatDateTime(message.sentAt).toUpperCase()}
                      {mine ? (message.readAt ? ' · LIDA' : ' · ENVIADA') : ''}
                    </p>
                  </li>
                );
              })}
            {showTyping ? (
              <li className="flex items-center gap-1 rounded-lg bg-surface-2 px-4 py-2.5 text-muted" aria-live="polite">
                <span className="size-1.5 animate-bounce rounded-full bg-muted motion-reduce:animate-none" />
                <span className="size-1.5 animate-bounce rounded-full bg-muted [animation-delay:120ms] motion-reduce:animate-none" />
                <span className="size-1.5 animate-bounce rounded-full bg-muted [animation-delay:240ms] motion-reduce:animate-none" />
              </li>
            ) : null}
          </ul>
        )}
        <div ref={bottomRef} />
      </div>

      <form onSubmit={handleSubmit} className="flex items-center gap-2 border-t border-line py-4">
        <label htmlFor="message-input" className="sr-only">
          Escrever mensagem
        </label>
        <input
          id="message-input"
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          placeholder="Escreva uma mensagem…"
          className="h-11 flex-1 rounded-full border border-line bg-surface px-4 text-body text-foreground"
        />
        <button
          type="submit"
          disabled={!draft.trim() || sendMutation.isPending}
          aria-label="Enviar mensagem"
          className="inline-flex size-11 shrink-0 items-center justify-center rounded-full bg-gradient-to-r from-orange-600 to-orange-400 text-accent-fg disabled:opacity-50"
        >
          <Send aria-hidden="true" className="size-4" strokeWidth={1.5} />
        </button>
      </form>
    </div>
  );
}

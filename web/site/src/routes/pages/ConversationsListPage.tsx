import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { Seo } from '../../components/Seo';
import { Avatar } from '../../components/ui/Avatar';
import { EmptyState } from '../../components/ui/EmptyState';
import { ErrorState } from '../../components/ui/ErrorState';
import { SkeletonText } from '../../components/ui/Skeleton';
import { formatRelativeTime } from '../../lib/dates';
import { toProblem } from '../../lib/problem';
import { services } from '../../services';

export function ConversationsListPage() {
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['conversations'],
    queryFn: () => services.chat.listConversations(),
  });

  return (
    <div className="mx-auto max-w-2xl px-5 py-10 sm:px-8">
      <Seo title="Conversas" description="As suas conversas com prestadores." />
      <h1 className="text-h2 font-display font-bold text-foreground">Conversas</h1>

      {error ? (
        <div className="mt-6">
          <ErrorState problem={toProblem(error)} onRetry={() => void refetch()} />
        </div>
      ) : isLoading ? (
        <div className="mt-6">
          <SkeletonText lines={5} />
        </div>
      ) : !data || data.length === 0 ? (
        <div className="mt-6">
          <EmptyState title="Ainda sem conversas" description="As conversas com prestadores aparecem aqui depois de enviar ou aceitar uma proposta." />
        </div>
      ) : (
        <ul className="mt-6 flex flex-col divide-y divide-line border-y border-line">
          {data.map((conversation) => (
            <li key={conversation.id}>
              <Link to={`/conversas/${conversation.id}`} className="flex items-center gap-3 py-4 hover:bg-surface-2">
                <Avatar name={conversation.counterpartName} />
                <div className="min-w-0 flex-1">
                  <div className="flex items-baseline justify-between gap-2">
                    <p className="truncate text-sm font-medium text-foreground">{conversation.counterpartName}</p>
                    <p className="shrink-0 font-mono text-eyebrow tracking-[0.08em] text-muted">
                      {formatRelativeTime(conversation.lastMessageAt).toUpperCase()}
                    </p>
                  </div>
                  <p className="truncate text-caption text-muted">{conversation.lastMessagePreview}</p>
                </div>
                {conversation.unreadCount > 0 ? (
                  <span className="flex size-5 shrink-0 items-center justify-center rounded-full bg-orange-500 text-xs font-medium text-accent-fg">
                    {conversation.unreadCount}
                  </span>
                ) : null}
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

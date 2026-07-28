import { useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { CheckCircle2 } from 'lucide-react';
import { useNavigate, useParams } from 'react-router-dom';
import { Seo } from '../../components/Seo';
import { ErrorState } from '../../components/ui/ErrorState';
import { RatingStars } from '../../components/ui/RatingStars';
import { SkeletonText } from '../../components/ui/Skeleton';
import { Textarea } from '../../components/ui/Textarea';
import { toProblem } from '../../lib/problem';
import { services } from '../../services';

export function NewReviewPage() {
  const { bookingId } = useParams<{ bookingId: string }>();
  const navigate = useNavigate();
  const [rating, setRating] = useState(0);
  const [comment, setComment] = useState('');
  const [submitted, setSubmitted] = useState(false);

  const bookingQuery = useQuery({
    queryKey: ['booking', bookingId],
    queryFn: () => services.reviews.getReviewableBooking(bookingId as string),
    enabled: Boolean(bookingId),
  });

  const submitMutation = useMutation({
    mutationFn: () =>
      services.reviews.create({
        bookingId: bookingId as string,
        targetId: bookingQuery.data?.proposalId ?? '',
        rating,
        comment: comment || undefined,
      }),
    onSuccess: () => setSubmitted(true),
  });

  if (!bookingId) return null;

  if (bookingQuery.isLoading) {
    return (
      <div className="mx-auto max-w-xl px-5 py-16 sm:px-8">
        <SkeletonText lines={4} />
      </div>
    );
  }

  if (bookingQuery.error || !bookingQuery.data) {
    return (
      <div className="mx-auto max-w-xl px-5 py-16 sm:px-8">
        <ErrorState problem={toProblem(bookingQuery.error)} onRetry={() => void bookingQuery.refetch()} />
      </div>
    );
  }

  const booking = bookingQuery.data;

  if (!booking.canReview) {
    return (
      <div className="mx-auto max-w-xl px-5 py-16 text-center sm:px-8">
        <p className="text-body text-muted">Esta marcação ainda não está concluída — só é possível avaliar depois de "Concluído".</p>
      </div>
    );
  }

  if (submitted) {
    return (
      <div className="mx-auto flex max-w-xl flex-col items-center px-5 py-16 text-center sm:px-8">
        <CheckCircle2 aria-hidden="true" className="size-10 text-success" strokeWidth={1.5} />
        <h1 className="mt-4 text-h2 font-display font-bold text-foreground">Obrigado pela avaliação!</h1>
        <button
          type="button"
          onClick={() => void navigate('/pedidos')}
          className="mt-6 inline-flex h-11 items-center justify-center rounded-full bg-gradient-to-r from-orange-600 to-orange-400 px-5 text-sm font-medium text-accent-fg"
        >
          Voltar aos meus pedidos
        </button>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-xl px-5 py-16 sm:px-8">
      <Seo title="Avaliar prestador" description="Avalie o serviço concluído." />
      <p className="eyebrow text-signal-500">AVALIAÇÃO</p>
      <h1 className="mt-2 text-h2 font-display font-bold text-foreground">Como correu com {booking.counterpartName}?</h1>
      <p className="mt-1 text-caption text-muted">{booking.requestTitle}</p>

      <form
        onSubmit={(event) => {
          event.preventDefault();
          submitMutation.mutate();
        }}
        className="surface-card mt-6 flex flex-col gap-4 p-6"
      >
        <div>
          <span className="text-sm font-medium text-foreground">Avaliação</span>
          <RatingStars value={rating} interactive onChange={setRating} className="mt-2" />
        </div>
        <div>
          <label htmlFor="review-comment" className="text-sm font-medium text-foreground">
            Comentário (opcional)
          </label>
          <Textarea id="review-comment" value={comment} onChange={(event) => setComment(event.target.value)} rows={4} className="mt-2" />
        </div>
        {submitMutation.isError ? <ErrorState problem={toProblem(submitMutation.error)} /> : null}
        <button
          type="submit"
          disabled={rating === 0 || submitMutation.isPending}
          className="inline-flex h-11 items-center justify-center self-start rounded-full bg-gradient-to-r from-orange-600 to-orange-400 px-5 text-sm font-medium text-accent-fg disabled:opacity-50"
        >
          {submitMutation.isPending ? 'A enviar…' : 'Enviar avaliação'}
        </button>
      </form>
    </div>
  );
}

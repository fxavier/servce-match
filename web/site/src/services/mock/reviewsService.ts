import type { ReviewsService } from '../interfaces';
import type { Review } from '../types';
import { reviewsForProvider } from './fixtures/reviews';
import { withLatency } from './latency';
import { throwProblem } from './mockProblem';

let reviewCounter = 0;

export const reviewsServiceMock: ReviewsService = {
  listForProvider(providerId) {
    return withLatency(() => reviewsForProvider(providerId));
  },
  create(body) {
    return withLatency(() => {
      if (!body.rating || body.rating < 1 || body.rating > 5) {
        throwProblem({
          type: 'https://errors.servimatch.pt/validation',
          title: 'Dados inválidos.',
          status: 422,
          errors: [{ field: 'rating', message: 'A avaliação tem de ser entre 1 e 5 estrelas.' }],
        });
      }
      reviewCounter += 1;
      const review: Review = {
        id: `rv-new-${reviewCounter}`,
        bookingId: body.bookingId,
        authorId: 'c-0001',
        targetId: body.targetId,
        rating: body.rating,
        comment: body.comment ?? '',
        createdAt: new Date().toISOString(),
      };
      return review;
    });
  },
  getReviewableBooking(bookingId) {
    return withLatency(() => ({
      id: bookingId,
      proposalId: 'pr-r-0006-p-0003',
      requestTitle: 'Cortar relva e aparar sebe do quintal',
      counterpartName: 'Jardins da Serra',
      status: 'COMPLETED',
      scheduledStart: null,
      completedAt: new Date().toISOString(),
      canReview: true,
    }));
  },
};

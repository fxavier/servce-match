import { throwProblem } from '../../lib/problem';
import { api } from '../http';
import type { ReviewsService } from '../interfaces';

export const reviewsServiceHttp: ReviewsService = {
  async listForProvider(providerId) {
    const { data, error } = await api.GET('/v1/providers/{providerId}/reviews', {
      params: { path: { providerId }, query: { limit: 50 } },
    });
    if (error) throwProblem(error);
    return data.items;
  },
  async create(body) {
    const { data, error } = await api.POST('/v1/reviews', { body });
    if (error) throwProblem(error);
    return data;
  },
  async getReviewableBooking(bookingId) {
    const { data, error } = await api.GET('/v1/bookings/{bookingId}', { params: { path: { bookingId } } });
    if (error) throwProblem(error);
    return data;
  },
};

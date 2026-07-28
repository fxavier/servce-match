import { throwProblem } from '../../lib/problem';
import { api } from '../http';
import type { ReviewsService } from '../interfaces';
import { notImplementedInContract } from './notImplemented';

export const reviewsServiceHttp: ReviewsService = {
  listForProvider() {
    // Gap #8 — sem GET /v1/reviews?targetId= (listar avaliações de um prestador).
    return notImplementedInContract('lista de avaliações de um prestador');
  },
  async create(body) {
    const { data, error } = await api.POST('/v1/reviews', { body });
    if (error) throwProblem(error);
    return data;
  },
  getReviewableBooking() {
    // Gap #5 — sem GET /v1/bookings/{id}.
    return notImplementedInContract('detalhe da marcação a avaliar');
  },
};

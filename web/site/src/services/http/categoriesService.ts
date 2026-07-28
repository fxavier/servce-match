import { throwProblem } from '../../lib/problem';
import { api } from '../http';
import type { CategoriesService } from '../interfaces';

export const categoriesServiceHttp: CategoriesService = {
  async list(params) {
    const { data, error } = await api.GET('/v1/categories', {
      params: { query: params?.parentId ? { parentId: params.parentId } : undefined },
    });
    if (error) throwProblem(error);
    return data;
  },
};

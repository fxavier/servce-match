import type { CategoriesService } from '../interfaces';
import { CATEGORIES } from './fixtures/categories';
import { withLatency } from './latency';

export const categoriesServiceMock: CategoriesService = {
  list(params) {
    return withLatency(() =>
      params?.parentId ? CATEGORIES.filter((category) => category.parentId === params.parentId) : CATEGORIES,
    );
  },
};

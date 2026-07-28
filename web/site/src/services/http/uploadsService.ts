import { throwProblem } from '../../lib/problem';
import { api } from '../http';
import type { UploadsService } from '../interfaces';

export const uploadsServiceHttp: UploadsService = {
  async createUploadTarget(body) {
    const { data, error } = await api.POST('/v1/uploads', { body });
    if (error) throwProblem(error);
    return data;
  },
};

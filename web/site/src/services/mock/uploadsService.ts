import type { UploadsService } from '../interfaces';
import { withLatency } from './latency';

export const uploadsServiceMock: UploadsService = {
  createUploadTarget(body) {
    return withLatency(() => {
      const id = crypto.randomUUID();
      return {
        imageId: id,
        uploadUrl: `https://mock-uploads.servimatch.pt/${id}`,
        method: 'PUT' as const,
        headers: { 'Content-Type': body.contentType },
        expiresAt: new Date(Date.now() + 15 * 60 * 1000).toISOString(),
        maxSizeBytes: 10 * 1024 * 1024,
      };
    }, [100, 300]);
  },
};

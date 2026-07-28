import { type DragEvent, useCallback, useRef, useState } from 'react';
import { ImagePlus, X } from 'lucide-react';

export interface PendingPhoto {
  id: string;
  name: string;
  previewUrl: string;
  progress: number;
}

export interface StepPhotosProps {
  photos: PendingPhoto[];
  onAdd: (files: File[]) => void;
  onRemove: (id: string) => void;
}

export function StepPhotos({ photos, onAdd, onRemove }: StepPhotosProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragActive, setDragActive] = useState(false);

  const handleDrop = useCallback(
    (event: DragEvent<HTMLDivElement>) => {
      event.preventDefault();
      setDragActive(false);
      onAdd(Array.from(event.dataTransfer.files));
    },
    [onAdd],
  );

  return (
    <div className="flex flex-col gap-5">
      <div>
        <h2 className="text-h2 font-display font-bold text-foreground">Fotografias (opcional)</h2>
        <p className="mt-2 text-body text-muted">Uma ou duas fotos ajudam o prestador a orçamentar com mais precisão.</p>
      </div>

      <div
        onDragOver={(event) => {
          event.preventDefault();
          setDragActive(true);
        }}
        onDragLeave={() => setDragActive(false)}
        onDrop={handleDrop}
        className={`flex flex-col items-center justify-center gap-2 rounded-lg border-2 border-dashed p-10 text-center transition-colors ${
          dragActive ? 'border-orange-500 bg-orange-500/5' : 'border-line'
        }`}
      >
        <ImagePlus aria-hidden="true" className="size-8 text-muted" strokeWidth={1.5} />
        <p className="text-body text-muted">Arraste fotos para aqui ou</p>
        <button
          type="button"
          onClick={() => inputRef.current?.click()}
          className="text-sm font-medium text-orange-600 hover:underline"
        >
          escolha ficheiros
        </button>
        <input
          ref={inputRef}
          type="file"
          accept="image/jpeg,image/png,image/webp"
          multiple
          className="sr-only"
          onChange={(event) => {
            if (event.target.files) onAdd(Array.from(event.target.files));
            event.target.value = '';
          }}
        />
      </div>

      {photos.length > 0 ? (
        <ul className="grid grid-cols-2 gap-4 sm:grid-cols-3">
          {photos.map((photo) => (
            <li key={photo.id} className="relative overflow-hidden rounded-lg border border-line">
              <img
                src={photo.previewUrl}
                alt={photo.name}
                width={200}
                height={140}
                loading="lazy"
                className="h-28 w-full object-cover"
              />
              <button
                type="button"
                onClick={() => onRemove(photo.id)}
                aria-label={`Remover ${photo.name}`}
                className="absolute right-1.5 top-1.5 rounded-full bg-navy-950/70 p-1 text-white"
              >
                <X aria-hidden="true" className="size-3.5" strokeWidth={2} />
              </button>
              {photo.progress < 100 ? (
                <div className="absolute inset-x-0 bottom-0 h-1 bg-navy-950/40">
                  <div className="h-full bg-orange-500 transition-[width]" style={{ width: `${photo.progress}%` }} />
                </div>
              ) : null}
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  );
}

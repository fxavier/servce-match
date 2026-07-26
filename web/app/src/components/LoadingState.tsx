export function LoadingState({ label = 'A carregar…' }: { label?: string }) {
  return (
    <p role="status" aria-live="polite">
      {label}
    </p>
  );
}

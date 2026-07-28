import { AppProviders } from './app/AppProviders';
import { AppRouter } from './routes/router';

export function App() {
  return (
    <AppProviders>
      <AppRouter />
    </AppProviders>
  );
}

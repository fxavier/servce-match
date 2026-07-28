import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './App';
import './styles/global.css';

const rootElement = document.getElementById('root');
if (!rootElement) {
  throw new Error('#root não encontrado em index.html.');
}

const root = createRoot(rootElement);
root.render(
  <StrictMode>
    <App />
  </StrictMode>,
);

// Auditoria de acessibilidade em runtime, só em dev (§4 — dev dependency
// listada de propósito). Nunca corre em produção nem afeta o bundle final.
if (import.meta.env.DEV) {
  void Promise.all([import('@axe-core/react'), import('react'), import('react-dom')]).then(
    ([axe, React, ReactDOM]) => {
      void axe.default(React, ReactDOM, 1000);
    },
  );
}

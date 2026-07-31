# Prompts da Onda C1 — um ficheiro por agente

Extraídos de `docs/ONDA-C1.md` §3. Cada ficheiro é para colar tal e qual no Claude Code.

**Antes de arrancar:** instala as duas skills (ver `docs/ONDA-C1.md` §Skills).

| # | Agente | Ficheiro | Onda |
|---|---|---|---|
| A1 | `db-migrations` | [`a1-db-migrations.txt`](./a1-db-migrations.txt) | A — paralela |
| A2 | `backend-platform` | [`a2-backend-platform.txt`](./a2-backend-platform.txt) | A — paralela |
| A3 | `backend-domain-requests` | [`a3-backend-domain-requests.txt`](./a3-backend-domain-requests.txt) | A — paralela |
| A4 | `web-bff` | [`a4-web-bff.txt`](./a4-web-bff.txt) | A — paralela |
| A5 | `web-site` | [`a5-web-site.txt`](./a5-web-site.txt) | A — paralela |
| B1 | `backend-domain-providers` | [`b1-backend-domain-providers.txt`](./b1-backend-domain-providers.txt) | B — depois de A1+A2 |
| C1 | `qa-e2e` | [`c1-qa-e2e.txt`](./c1-qa-e2e.txt) | C — depois de B1 |
| C2 | `security-auditor` | [`c2-security-auditor.txt`](./c2-security-auditor.txt) | C — depois de B1 |

Os cinco de Onda A correm em simultâneo. B1 depende da migração (A1) e do `platform/audit` (A2). C1 e C2 correm em paralelo depois de B1.

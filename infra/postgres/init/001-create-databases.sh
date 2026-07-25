#!/usr/bin/env bash
# Cria bases de dados adicionais no arranque do container Postgres.
#
# A base de dados principal (definida por POSTGRES_DB) é criada
# automaticamente pela imagem oficial. Este script cria as restantes,
# listadas em POSTGRES_MULTIPLE_DATABASES (separadas por vírgula) — usamos
# isto para dar ao Keycloak a sua própria base de dados no mesmo servidor
# Postgres, sem misturar esquemas com o domínio da aplicação.
set -euo pipefail

function create_database() {
	local database=$1
	echo "[init] A criar base de dados '${database}' (se ainda não existir)…"
	psql -v ON_ERROR_STOP=1 --username "${POSTGRES_USER}" <<-EOSQL
		SELECT 'CREATE DATABASE "${database}"'
		WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '${database}')\gexec
	EOSQL
}

if [ -n "${POSTGRES_MULTIPLE_DATABASES:-}" ]; then
	echo "[init] Bases de dados adicionais pedidas: ${POSTGRES_MULTIPLE_DATABASES}"
	for db in $(echo "${POSTGRES_MULTIPLE_DATABASES}" | tr ',' ' '); do
		create_database "${db}"
	done
fi

#!/usr/bin/env sh
# Cria o bucket de uploads no MinIO local e garante que fica PRIVADO.
#
# Nada de política pública aqui: no ServiMatch os ficheiros nunca são
# servidos diretamente a partir do bucket. `POST /v1/uploads` emite um URL
# pré-assinado de utilização única e com expiração (§8.6 / §11.2 de
# docs/ARQUITETURA.md); é assim que qualquer cliente (web, mobile, admin)
# lê ou escreve um objeto. Correr este script duas vezes é seguro
# (idempotente): `mc mb --ignore-existing` não falha se o bucket já existir.
set -eu

MC_HOST_ALIAS="local"

echo "[minio-init] A configurar alias '${MC_HOST_ALIAS}' para ${MINIO_ENDPOINT}…"
mc alias set "${MC_HOST_ALIAS}" "${MINIO_ENDPOINT}" "${MINIO_ROOT_USER}" "${MINIO_ROOT_PASSWORD}"

echo "[minio-init] A garantir que o bucket '${MINIO_BUCKET_UPLOADS}' existe…"
mc mb --ignore-existing "${MC_HOST_ALIAS}/${MINIO_BUCKET_UPLOADS}"

echo "[minio-init] A garantir que o bucket é privado (sem acesso anónimo)…"
mc anonymous set none "${MC_HOST_ALIAS}/${MINIO_BUCKET_UPLOADS}"

echo "[minio-init] Pronto. Bucket disponível apenas via URLs pré-assinados."

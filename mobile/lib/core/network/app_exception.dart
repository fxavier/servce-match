import 'generated/models/models.dart';

/// Tipo de domínio para erros de rede/API — a UI ramifica por aqui, nunca
/// por mensagens de texto (skill flutter-feature-slice, secção "Rede").
sealed class AppException implements Exception {
  const AppException();
}

/// Sem ligação à internet (ou o pedido expirou por timeout de rede).
/// Todo o ecrã com I/O trata este estado explicitamente — mobile perde
/// rede; não é um caso extremo (CLAUDE.md §Qualidade).
class OfflineException extends AppException {
  const OfflineException();
}

/// O servidor respondeu com `application/problem+json` (RFC 9457). A UI
/// ramifica pelo [type] — nunca pela mensagem — para tratar casos como
/// `subscription-required` como estado de produto, não como alerta de
/// erro genérico.
class ServerProblemException extends AppException {
  const ServerProblemException(this.problem, {this.correlationId});

  final ProblemDetails problem;

  /// Extraído do cabeçalho de resposta `X-Correlation-Id` (ou gerado pelo
  /// cliente se o servidor não o devolver) — nunca do email/PII do
  /// utilizador (CLAUDE.md §4).
  final String? correlationId;

  String get type => problem.type ?? 'https://errors.servimatch.pt/unknown';

  bool get isSubscriptionRequired =>
      type == 'https://errors.servimatch.pt/subscription-required';
}

/// Resposta inesperada (não é `problem+json`, ou falhou o parse) — inclui
/// código de estado para diagnóstico.
class UnexpectedApiException extends AppException {
  const UnexpectedApiException(this.message, {this.statusCode});

  final String message;
  final int? statusCode;
}

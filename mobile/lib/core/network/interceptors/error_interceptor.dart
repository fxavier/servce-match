import 'package:dio/dio.dart';

import '../app_exception.dart';
import '../generated/models/models.dart';
import 'correlation_interceptor.dart';

/// Converte falhas de rede e `application/problem+json` (RFC 9457) num
/// [AppException] de domínio, anexado a `DioException.error`. A UI/
/// repositórios nunca ramificam por `DioException` nem por mensagens de
/// texto — só por [AppException] (ver `app_exception.dart`).
class ErrorInterceptor extends Interceptor {
  @override
  void onError(DioException err, ErrorInterceptorHandler handler) {
    handler.next(
      err.copyWith(error: _mapToAppException(err)),
    );
  }

  AppException _mapToAppException(DioException err) {
    switch (err.type) {
      case DioExceptionType.connectionError:
      case DioExceptionType.connectionTimeout:
      case DioExceptionType.receiveTimeout:
      case DioExceptionType.sendTimeout:
        return const OfflineException();
      default:
        break;
    }

    final response = err.response;
    if (response == null) {
      return UnexpectedApiException(err.message ?? 'Erro de rede desconhecido.');
    }

    final correlationId = response.headers.value(correlationHeader) ??
        response.requestOptions.headers[correlationHeader] as String?;

    final contentType = response.headers.value(Headers.contentTypeHeader) ?? '';
    final data = response.data;
    if (contentType.contains('problem+json') && data is Map<String, dynamic>) {
      try {
        return ServerProblemException(
          ProblemDetails.fromJson(data),
          correlationId: correlationId,
        );
      } catch (_) {
        // Corpo malformado: cai para o caso genérico abaixo em vez de
        // rebentar o parse — um cliente instalado há meses não pode
        // falhar por causa de uma resposta de erro inesperada.
      }
    }

    return UnexpectedApiException(
      err.message ?? 'Erro inesperado do servidor.',
      statusCode: response.statusCode,
    );
  }
}

import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/network/app_exception.dart';
import 'package:mobile/core/network/interceptors/correlation_interceptor.dart';
import 'package:mobile/core/network/interceptors/error_interceptor.dart';

/// Adaptador falso: em vez de bater na rede, devolve/lança exatamente o que
/// o teste pedir — permite exercitar `ErrorInterceptor` através do
/// pipeline real do Dio (sem tocar em membros `@protected`).
class _FakeAdapter implements HttpClientAdapter {
  _FakeAdapter.response({
    required this.statusCode,
    required this.body,
    required this.contentType,
    this.correlationId,
  }) : throwsConnectionError = false;

  _FakeAdapter.connectionError()
      : throwsConnectionError = true,
        statusCode = 0,
        body = '',
        contentType = '',
        correlationId = null;

  final bool throwsConnectionError;
  final int statusCode;
  final String body;
  final String contentType;
  final String? correlationId;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    if (throwsConnectionError) {
      throw DioException.connectionError(
        requestOptions: options,
        reason: 'Falha simulada de ligação.',
      );
    }
    return ResponseBody.fromString(
      body,
      statusCode,
      headers: {
        Headers.contentTypeHeader: [contentType],
        if (correlationId != null) correlationHeader: [correlationId!],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

Dio _dioWith(HttpClientAdapter adapter) {
  final dio = Dio(BaseOptions(baseUrl: 'http://api.test'))
    ..httpClientAdapter = adapter
    ..interceptors.add(ErrorInterceptor());
  return dio;
}

void main() {
  test(
    'problem+json (RFC 9457) é convertido em ServerProblemException com '
    'type e correlationId preservados (caminho principal)',
    () async {
      final dio = _dioWith(
        _FakeAdapter.response(
          statusCode: 403,
          contentType: 'application/problem+json',
          correlationId: 'corr-abc-123',
          body: jsonEncode({
            'type': 'https://errors.servimatch.pt/subscription-required',
            'title': 'Subscrição necessária',
            'status': 403,
            'detail': 'É preciso uma subscrição ativa.',
          }),
        ),
      );

      Object? caught;
      try {
        await dio.get('/v1/requests/x/proposals');
      } catch (e) {
        caught = e;
      }

      expect(caught, isA<DioException>());
      final appException = (caught as DioException).error;
      expect(appException, isA<ServerProblemException>());
      final problem = appException as ServerProblemException;
      expect(problem.type, 'https://errors.servimatch.pt/subscription-required');
      expect(problem.isSubscriptionRequired, isTrue);
      expect(problem.correlationId, 'corr-abc-123');
    },
  );

  test(
    'falha de ligação é convertida em OfflineException (caso de erro)',
    () async {
      final dio = _dioWith(_FakeAdapter.connectionError());

      Object? caught;
      try {
        await dio.get('/v1/app/version-status');
      } catch (e) {
        caught = e;
      }

      expect(caught, isA<DioException>());
      expect((caught as DioException).error, isA<OfflineException>());
    },
  );

  test(
    'corpo de erro que não é problem+json cai para UnexpectedApiException',
    () async {
      final dio = _dioWith(
        _FakeAdapter.response(
          statusCode: 500,
          contentType: 'text/html',
          body: '<html>Internal Server Error</html>',
        ),
      );

      Object? caught;
      try {
        await dio.get('/v1/categories');
      } catch (e) {
        caught = e;
      }

      expect(caught, isA<DioException>());
      final appException = (caught as DioException).error;
      expect(appException, isA<UnexpectedApiException>());
      expect((appException as UnexpectedApiException).statusCode, 500);
    },
  );
}

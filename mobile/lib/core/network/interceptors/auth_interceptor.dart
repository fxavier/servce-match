import 'package:dio/dio.dart';

/// Injeta o *access token* em cada pedido e, ao receber `401`, tenta
/// renovar **uma vez** antes de desistir — a serialização do refresh (um
/// só em curso, mesmo com pedidos concorrentes) é responsabilidade de
/// quem implementa [forceRefresh] (ver `AuthController.forceRefresh`).
///
/// Precisa de uma referência ao [Dio] que a instancia para poder repetir o
/// pedido original depois de renovar — atribuída via [attachDio] logo
/// após a construção do cliente (evita a dependência circular
/// Dio→interceptor→Dio).
class AuthInterceptor extends Interceptor {
  AuthInterceptor({
    required Future<String?> Function() getAccessToken,
    required Future<String?> Function() forceRefresh,
  })  : _getAccessToken = getAccessToken,
        _forceRefresh = forceRefresh;

  final Future<String?> Function() _getAccessToken;
  final Future<String?> Function() _forceRefresh;

  Dio? _dio;

  void attachDio(Dio dio) => _dio = dio;

  static const _retriedKey = 'servimatch.retriedAfter401';

  @override
  void onRequest(
    RequestOptions options,
    RequestInterceptorHandler handler,
  ) async {
    final token = await _getAccessToken();
    if (token != null) {
      options.headers['Authorization'] = 'Bearer $token';
    }
    handler.next(options);
  }

  @override
  void onError(DioException err, ErrorInterceptorHandler handler) async {
    final alreadyRetried = err.requestOptions.extra[_retriedKey] == true;
    final dio = _dio;
    if (err.response?.statusCode == 401 && !alreadyRetried && dio != null) {
      final newToken = await _forceRefresh();
      if (newToken != null) {
        final retryOptions = err.requestOptions
          ..headers['Authorization'] = 'Bearer $newToken'
          ..extra[_retriedKey] = true;
        try {
          final response = await dio.fetch(retryOptions);
          return handler.resolve(response);
        } on DioException catch (retryError) {
          return handler.next(retryError);
        }
      }
    }
    handler.next(err);
  }
}

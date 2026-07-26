import 'package:dio/dio.dart';
import 'package:uuid/uuid.dart';

const correlationHeader = 'X-Correlation-Id';

/// Propaga um `correlation_id` por pedido, para cruzar com os logs do
/// servidor sem nunca usar PII como chave de correlação (CLAUDE.md §4).
class CorrelationInterceptor extends Interceptor {
  CorrelationInterceptor({Uuid? uuid}) : _uuid = uuid ?? const Uuid();

  final Uuid _uuid;

  @override
  void onRequest(
    RequestOptions options,
    RequestInterceptorHandler handler,
  ) {
    options.headers.putIfAbsent(correlationHeader, () => _uuid.v4());
    handler.next(options);
  }
}

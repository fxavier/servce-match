import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart' show kDebugMode;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:pretty_dio_logger/pretty_dio_logger.dart';

import '../auth/auth_controller.dart';
import 'generated/servimatch_api.dart';
import 'interceptors/auth_interceptor.dart';
import 'interceptors/correlation_interceptor.dart';
import 'interceptors/error_interceptor.dart';

final dioProvider = Provider<Dio>((ref) {
  final env = ref.watch(appEnvironmentProvider);
  final dio = Dio(
    BaseOptions(
      baseUrl: env.apiBaseUrl,
      connectTimeout: const Duration(seconds: 10),
      receiveTimeout: const Duration(seconds: 15),
      // Necessário para que o transformer JSON por omissão do Dio
      // (`Transformer.defaultTransformRequest`) chame `jsonEncode` no
      // corpo do pedido — sem isto, cairia num `data.toString()` que não
      // é JSON válido. O cliente gerado (`servimatch_api.g.dart`) passa
      // os modelos `freezed` diretamente como `data:`, sem chamar
      // `.toJson()` explicitamente (limitação conhecida da combinação
      // retrofit_generator+freezed — ver README de `core/network/
      // generated`); `jsonEncode` volta a chamar `toJson()` de forma
      // dinâmica (comportamento por omissão de `dart:convert`), pelo que
      // a serialização fica correta desde que o `Content-Type` force
      // este caminho.
      contentType: Headers.jsonContentType,
    ),
  );

  final authInterceptor = AuthInterceptor(
    getAccessToken: () =>
        ref.read(authControllerProvider.notifier).ensureFreshAccessToken(),
    forceRefresh: () =>
        ref.read(authControllerProvider.notifier).forceRefresh(),
  )..attachDio(dio);

  dio.interceptors.addAll([
    CorrelationInterceptor(),
    authInterceptor,
    ErrorInterceptor(),
    if (kDebugMode)
      PrettyDioLogger(
        requestHeader: false,
        requestBody: false,
        responseBody: false,
        // Nunca logar corpo/headers em produção: podem conter PII/tokens
        // (CLAUDE.md §4). Mesmo em debug, cabeçalhos/corpo ficam fora por
        // omissão — ativa localmente só quando precisares de depurar.
      ),
  ]);

  return dio;
});

final serviMatchApiProvider = Provider<ServiMatchApi>(
  (ref) => ServiMatchApi(ref.watch(dioProvider)),
);

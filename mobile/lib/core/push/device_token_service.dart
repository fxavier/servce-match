import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../network/dio_provider.dart';
import '../network/generated/models/models.dart';

/// Ponto de entrada preparado para push (ADR-0008), **não** ligado ao FCM
/// nesta ronda — ver nota abaixo.
///
/// O que falta para a funcionalidade completa (fora do âmbito desta
/// ronda: "autenticar → criar pedido → ver propostas"):
/// - Dependência `firebase_messaging` e o projeto Firebase associado
///   (`google-services.json` / `GoogleService-Info.plist`), que exigem
///   credenciais reais — adicioná-las sem um projeto Firebase válido
///   deixaria o build nativo a falhar silenciosamente em CI.
/// - Obter o token FCM e chamar [register] depois do login; chamar
///   [unregister] no logout; voltar a chamar [register] quando o FCM
///   rodar o token (callback `onTokenRefresh`).
///
/// O que já está pronto: o contrato de rede (`POST/DELETE
/// /v1/device-tokens`) e este serviço, para que ligar o FCM no futuro
/// seja só preencher [register]/[unregister] a partir de
/// `FirebaseMessaging.instance.getToken()`.
class DeviceTokenService {
  DeviceTokenService(this._ref);

  final Ref _ref;

  Future<void> register(String token) {
    final platform = defaultTargetPlatform == TargetPlatform.iOS
        ? ApiPlatform.ios
        : ApiPlatform.android;
    return _ref.read(serviMatchApiProvider).registerDeviceToken(
          RegisterDeviceToken(token: token, platform: platform),
        );
  }

  Future<void> unregister(String token) {
    return _ref.read(serviMatchApiProvider).deleteDeviceToken(token);
  }
}

final deviceTokenServiceProvider = Provider<DeviceTokenService>(
  (ref) => DeviceTokenService(ref),
);

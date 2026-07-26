import 'dart:io' show Platform;

import 'package:flutter/foundation.dart' show kIsWeb;

/// Configuração de ambiente do cliente móvel.
///
/// Nada aqui é segredo: um *client* OAuth2 nativo é **público** por
/// definição (RFC 8252) — não leva `client_secret`. Tudo o resto é
/// endereço/configuração, não credencial.
///
/// Os valores por omissão apontam para o ambiente de desenvolvimento local
/// (`infra/keycloak/realm-servimatch.json`, client `servimatch-mobile`).
/// Em CI/produção, sobrepor via `--dart-define`.
class AppEnvironment {
  const AppEnvironment({
    required this.apiBaseUrl,
    required this.oidcIssuer,
    required this.oidcClientId,
    required this.oidcRedirectUri,
    required this.oidcScopes,
  });

  final String apiBaseUrl;
  final String oidcIssuer;
  final String oidcClientId;
  final String oidcRedirectUri;
  final List<String> oidcScopes;

  /// `10.0.2.2` é o *alias* do emulador Android para o `localhost` da
  /// máquina anfitriã; o simulador iOS partilha a rede do anfitrião e usa
  /// `localhost` diretamente. Num dispositivo físico nenhum dos dois
  /// funciona — é preciso passar `API_BASE_URL`/`OIDC_ISSUER` explícitos
  /// via `--dart-define` apontando para um endereço alcançável (ex. IP da
  /// máquina de desenvolvimento ou o ambiente de staging).
  static String _defaultLocalHost() {
    if (kIsWeb) return 'localhost';
    if (Platform.isAndroid) return '10.0.2.2';
    return 'localhost';
  }

  factory AppEnvironment.fromDartDefines() {
    final host = _defaultLocalHost();
    const apiBaseUrl = String.fromEnvironment('API_BASE_URL');
    const oidcIssuer = String.fromEnvironment('OIDC_ISSUER');
    const redirectUri = String.fromEnvironment('OIDC_REDIRECT_URI');

    return AppEnvironment(
      apiBaseUrl: apiBaseUrl.isNotEmpty ? apiBaseUrl : 'http://$host:8080',
      oidcIssuer: oidcIssuer.isNotEmpty
          ? oidcIssuer
          : 'http://$host:8081/realms/servimatch',
      oidcClientId: const String.fromEnvironment(
        'OIDC_CLIENT_ID',
        defaultValue: 'servimatch-mobile',
      ),
      // ADR-0009: App Links/Universal Links (https), não custom scheme.
      // Requer o domínio associado (assetlinks.json / apple-app-site-
      // association) hospedado pela equipa de infraestrutura/web — fora do
      // âmbito de escrita `mobile/**`. Ver README de `core/auth`.
      oidcRedirectUri: redirectUri.isNotEmpty
          ? redirectUri
          : 'https://app.servimatch.pt/oauth2redirect',
      oidcScopes: const ['openid', 'profile', 'email', 'offline_access'],
    );
  }
}

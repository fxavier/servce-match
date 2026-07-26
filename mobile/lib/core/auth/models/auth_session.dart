import 'app_user.dart';

/// Resultado de um login/refresh bem-sucedido.
///
/// **Nunca** é persistido como um todo: só [refreshToken] e [idToken] vão
/// para o *secure storage* (`token_storage.dart`); [accessToken] fica só
/// em memória, dentro do `AuthState`.
class AuthSession {
  const AuthSession({
    required this.user,
    required this.accessToken,
    required this.accessTokenExpiry,
    required this.refreshToken,
    required this.idToken,
  });

  final AppUser user;
  final String accessToken;
  final DateTime accessTokenExpiry;
  final String? refreshToken;
  final String? idToken;
}

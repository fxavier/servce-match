import 'package:freezed_annotation/freezed_annotation.dart';

import 'app_user.dart';

part 'auth_state.freezed.dart';

/// Estado de sessão da app.
///
/// `unknown` é o estado transitório enquanto se tenta restaurar a sessão a
/// partir do *refresh token* em *secure storage*, no arranque.
@freezed
sealed class AuthState with _$AuthState {
  const factory AuthState.unknown() = AuthStateUnknown;

  const factory AuthState.authenticated({
    required AppUser user,
    required String accessToken,
    required DateTime accessTokenExpiry,
  }) = AuthStateAuthenticated;

  const factory AuthState.unauthenticated({
    AuthUnauthenticatedReason? reason,
  }) = AuthStateUnauthenticated;
}

enum AuthUnauthenticatedReason {
  /// Nunca autenticou ou terminou sessão voluntariamente.
  loggedOut,

  /// O utilizador cancelou o fluxo no *system browser*.
  cancelledByUser,

  /// A sessão expirou (refresh falhou) e é preciso autenticar de novo.
  sessionExpired,

  /// Falha de rede/servidor ao tentar autenticar ou restaurar sessão.
  error,
}

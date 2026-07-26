import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../config/app_environment.dart';
import 'auth_exceptions.dart';
import 'auth_repository.dart';
import 'models/auth_session.dart';
import 'models/auth_state.dart';

/// Orquestra a sessão da app e serve de ponte para o interceptor HTTP
/// (`core/network`): garante sempre um *access token* fresco, com o
/// *refresh* serializado — pedidos concorrentes esperam pelo mesmo
/// `Future` em vez de disparar vários refreshes (ver skill
/// flutter-feature-slice, secção "Rede").
class AuthController extends Notifier<AuthState> {
  AuthRepository get _repo => ref.read(authRepositoryProvider);

  Completer<String?>? _refreshing;

  @override
  AuthState build() {
    // O arranque não pode bloquear a construção do widget tree: começa em
    // `unknown` e resolve-se assim que o secure storage responder.
    Future.microtask(restoreSession);
    return const AuthState.unknown();
  }

  Future<void> restoreSession() async {
    try {
      final session = await _repo.restoreSession();
      state = session == null
          ? const AuthState.unauthenticated(
              reason: AuthUnauthenticatedReason.loggedOut,
            )
          : _authenticated(session);
    } catch (_) {
      state = const AuthState.unauthenticated(
        reason: AuthUnauthenticatedReason.error,
      );
    }
  }

  Future<void> login() async {
    try {
      final session = await _repo.login();
      state = _authenticated(session);
    } on AuthCancelledException {
      state = const AuthState.unauthenticated(
        reason: AuthUnauthenticatedReason.cancelledByUser,
      );
    } on AuthFailedException {
      state = const AuthState.unauthenticated(
        reason: AuthUnauthenticatedReason.error,
      );
    }
  }

  Future<void> logout() async {
    await _repo.logout();
    state = const AuthState.unauthenticated(
      reason: AuthUnauthenticatedReason.loggedOut,
    );
  }

  /// Usado pelo interceptor de autenticação antes de cada pedido: devolve
  /// um *access token* válido, renovando-o se estiver perto de expirar.
  Future<String?> ensureFreshAccessToken() async {
    final current = state;
    if (current is AuthStateAuthenticated) {
      final expiresSoon = current.accessTokenExpiry
          .isBefore(DateTime.now().add(const Duration(seconds: 30)));
      if (!expiresSoon) return current.accessToken;
    } else if (current is! AuthStateUnknown) {
      return null;
    }
    return _refreshSingleFlight();
  }

  /// Usado pelo interceptor depois de um `401`: o *access token* em
  /// memória parecia válido pela data de expiração mas o servidor
  /// rejeitou-o na mesma (relógio dessincronizado, revogação). Tenta
  /// renovar **uma vez**; se falhar, termina a sessão.
  Future<String?> forceRefresh() => _refreshSingleFlight();

  Future<String?> _refreshSingleFlight() {
    final inFlight = _refreshing;
    if (inFlight != null) return inFlight.future;

    final completer = Completer<String?>();
    _refreshing = completer;
    _doRefresh().then(completer.complete, onError: completer.completeError);
    completer.future.whenComplete(() => _refreshing = null);
    return completer.future;
  }

  Future<String?> _doRefresh() async {
    try {
      final session = await _repo.restoreSession();
      if (session == null) {
        state = const AuthState.unauthenticated(
          reason: AuthUnauthenticatedReason.sessionExpired,
        );
        return null;
      }
      state = _authenticated(session);
      return session.accessToken;
    } catch (_) {
      state = const AuthState.unauthenticated(
        reason: AuthUnauthenticatedReason.sessionExpired,
      );
      return null;
    }
  }

  AuthState _authenticated(AuthSession session) => AuthState.authenticated(
        user: session.user,
        accessToken: session.accessToken,
        accessTokenExpiry: session.accessTokenExpiry,
      );
}

final appEnvironmentProvider = Provider<AppEnvironment>(
  (ref) => AppEnvironment.fromDartDefines(),
);

/// Implementação real por omissão; os testes sobrepõem este provider com
/// um `AuthRepository` de teste (`ProviderScope(overrides: [...])`).
final authRepositoryProvider = Provider<AuthRepository>(
  (ref) => AppAuthRepository(environment: ref.watch(appEnvironmentProvider)),
);

final authControllerProvider =
    NotifierProvider<AuthController, AuthState>(AuthController.new);

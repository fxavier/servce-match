import 'package:flutter/services.dart' show PlatformException;
import 'package:flutter_appauth/flutter_appauth.dart';
import 'package:jwt_decoder/jwt_decoder.dart';

import '../config/app_environment.dart';
import 'auth_exceptions.dart';
import 'models/app_user.dart';
import 'models/auth_session.dart';
import 'models/user_role.dart';
import 'token_storage.dart';

/// Fronteira de autenticação da app: RFC 8252 (Authorization Code + PKCE)
/// via *system browser*, com `flutter_appauth` (ADR-0009).
///
/// **Nunca** usa `WebView` embebido — `flutter_appauth` já garante isto ao
/// delegar em `ASWebAuthenticationSession`/Custom Tabs; não há aqui
/// nenhuma dependência de `webview_flutter`.
abstract class AuthRepository {
  Future<AuthSession> login();

  /// `null` se não houver sessão para restaurar (primeira utilização, ou
  /// logout anterior). Nunca lança por "sem sessão" — só por falha real.
  Future<AuthSession?> restoreSession();

  Future<AuthSession> refresh(String refreshToken);

  Future<void> logout();
}

class AppAuthRepository implements AuthRepository {
  AppAuthRepository({
    required AppEnvironment environment,
    FlutterAppAuth? appAuth,
    SecureTokenStorage? tokenStorage,
  })  : _env = environment,
        _appAuth = appAuth ?? const FlutterAppAuth(),
        _storage = tokenStorage ?? SecureTokenStorage();

  final AppEnvironment _env;
  final FlutterAppAuth _appAuth;
  final SecureTokenStorage _storage;

  @override
  Future<AuthSession> login() async {
    try {
      final response = await _appAuth.authorizeAndExchangeCode(
        AuthorizationTokenRequest(
          _env.oidcClientId,
          _env.oidcRedirectUri,
          issuer: _env.oidcIssuer,
          scopes: _env.oidcScopes,
          // Nunca webview embebido: o plugin usa sempre o browser do
          // sistema; isto só controla partilha de cookies com a sessão
          // "normal" do browser (SSO), não o mecanismo em si.
          preferEphemeralSession: false,
        ),
      );
      return _persist(response);
    } on FlutterAppAuthUserCancelledException {
      throw const AuthCancelledException();
    } on PlatformException catch (e) {
      throw AuthFailedException(e.message ?? e.code);
    }
  }

  @override
  Future<AuthSession?> restoreSession() async {
    final refreshToken = await _storage.readRefreshToken();
    if (refreshToken == null) return null;
    try {
      return await refresh(refreshToken);
    } on AuthFailedException {
      // Refresh token inválido/revogado/expirado: limpa e força novo login.
      await _storage.clear();
      return null;
    }
  }

  @override
  Future<AuthSession> refresh(String refreshToken) async {
    try {
      final response = await _appAuth.token(
        TokenRequest(
          _env.oidcClientId,
          _env.oidcRedirectUri,
          issuer: _env.oidcIssuer,
          refreshToken: refreshToken,
          scopes: _env.oidcScopes,
        ),
      );
      return _persist(response);
    } on PlatformException catch (e) {
      throw AuthFailedException(e.message ?? e.code);
    }
  }

  @override
  Future<void> logout() async {
    final idToken = await _storage.readIdToken();
    // Limpa localmente primeiro: mesmo que a revogação remota falhe (sem
    // rede), a sessão local termina — o utilizador não pode ficar "preso"
    // autenticado por causa de uma falha de rede no logout.
    await _storage.clear();
    try {
      await _appAuth.endSession(
        EndSessionRequest(
          idTokenHint: idToken,
          postLogoutRedirectUrl: idToken != null ? _env.oidcRedirectUri : null,
          issuer: _env.oidcIssuer,
        ),
      );
    } catch (_) {
      // Best-effort: o storage local já está limpo, que é o invariante que
      // importa para o dispositivo. A sessão no Keycloak expira sozinha.
    }
  }

  Future<AuthSession> _persist(TokenResponse response) async {
    final accessToken = response.accessToken;
    if (accessToken == null) {
      throw const AuthFailedException('Resposta sem access_token.');
    }
    await _storage.saveSession(
      refreshToken: response.refreshToken,
      idToken: response.idToken,
    );
    return AuthSession(
      user: _userFromAccessToken(accessToken),
      accessToken: accessToken,
      accessTokenExpiry: response.accessTokenExpirationDateTime ??
          JwtDecoder.getExpirationDate(accessToken),
      refreshToken: response.refreshToken,
      idToken: response.idToken,
    );
  }

  AppUser _userFromAccessToken(String accessToken) {
    final claims = JwtDecoder.decode(accessToken);
    final realmAccess = claims['realm_access'];
    final rolesClaim = realmAccess is Map ? realmAccess['roles'] : null;
    final roles = <UserRole>{
      if (rolesClaim is List)
        for (final r in rolesClaim)
          if (r is String && UserRole.fromWireValue(r) != null)
            UserRole.fromWireValue(r)!,
    };
    return AppUser(
      subject: claims['sub'] as String? ?? '',
      email: claims['email'] as String?,
      displayName:
          claims['name'] as String? ?? claims['preferred_username'] as String?,
      roles: roles,
    );
  }
}

import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Armazenamento do *refresh token* (e do *id token*, usado só como
/// `id_token_hint` no logout) em Keychain (iOS) / Keystore-
/// `EncryptedSharedPreferences` (Android), via `flutter_secure_storage`.
///
/// ADR-0009: nunca em `SharedPreferences` não cifradas nem em ficheiro. O
/// *access token* **não** passa por aqui — vive só em memória, dentro do
/// `AuthState` (ver `auth_controller.dart`).
class SecureTokenStorage {
  SecureTokenStorage({FlutterSecureStorage? storage})
      : _storage = storage ??
            const FlutterSecureStorage(
              aOptions: AndroidOptions(encryptedSharedPreferences: true),
            );

  final FlutterSecureStorage _storage;

  static const _refreshTokenKey = 'servimatch.refresh_token';
  static const _idTokenKey = 'servimatch.id_token';

  Future<void> saveSession({
    required String? refreshToken,
    required String? idToken,
  }) async {
    if (refreshToken != null) {
      await _storage.write(key: _refreshTokenKey, value: refreshToken);
    }
    if (idToken != null) {
      await _storage.write(key: _idTokenKey, value: idToken);
    }
  }

  Future<String?> readRefreshToken() => _storage.read(key: _refreshTokenKey);

  Future<String?> readIdToken() => _storage.read(key: _idTokenKey);

  Future<void> clear() async {
    await _storage.delete(key: _refreshTokenKey);
    await _storage.delete(key: _idTokenKey);
  }
}

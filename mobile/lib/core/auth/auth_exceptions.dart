/// O utilizador cancelou o fluxo no *system browser*
/// (`ASWebAuthenticationSession`/Custom Tabs).
class AuthCancelledException implements Exception {
  const AuthCancelledException();
}

/// Falhou a autenticação ou a renovação da sessão (rede, servidor, refresh
/// token revogado/expirado).
class AuthFailedException implements Exception {
  const AuthFailedException(this.message);

  final String message;

  @override
  String toString() => 'AuthFailedException: $message';
}

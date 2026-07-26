import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/auth/auth_controller.dart';
import '../../../core/auth/models/auth_state.dart';
import '../../../l10n/generated/app_localizations.dart';

/// Início de sessão via RFC 8252: `AuthController.login()` delega no
/// *system browser* (ASWebAuthenticationSession/Custom Tabs) através do
/// `flutter_appauth` — nunca um `WebView` embebido (ADR-0009).
class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key});

  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> {
  bool _isLoggingIn = false;

  Future<void> _login() async {
    setState(() => _isLoggingIn = true);
    await ref.read(authControllerProvider.notifier).login();
    if (mounted) setState(() => _isLoggingIn = false);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final authState = ref.watch(authControllerProvider);
    final reason = switch (authState) {
      AuthStateUnauthenticated(:final reason) => reason,
      _ => null,
    };

    final statusMessage = switch (reason) {
      AuthUnauthenticatedReason.cancelledByUser => l10n.authCancelled,
      AuthUnauthenticatedReason.sessionExpired => l10n.authSessionExpired,
      AuthUnauthenticatedReason.error => l10n.commonGenericErrorMessage,
      _ => null,
    };

    return Scaffold(
      body: SafeArea(
        child: Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const FlutterLogo(size: 64),
                const SizedBox(height: 24),
                Text(
                  l10n.authLoginTitle,
                  style: Theme.of(context).textTheme.headlineSmall,
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 8),
                Text(
                  l10n.authLoginSubtitle,
                  textAlign: TextAlign.center,
                  style: Theme.of(context).textTheme.bodyMedium,
                ),
                if (statusMessage != null) ...[
                  const SizedBox(height: 16),
                  Text(
                    statusMessage,
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      color: Theme.of(context).colorScheme.error,
                    ),
                  ),
                ],
                const SizedBox(height: 32),
                FilledButton(
                  key: const Key('loginButton'),
                  onPressed: _isLoggingIn ? null : _login,
                  child: _isLoggingIn
                      ? SizedBox(
                          width: 20,
                          height: 20,
                          child: CircularProgressIndicator(
                            strokeWidth: 2,
                            color: Theme.of(context).colorScheme.onPrimary,
                          ),
                        )
                      : Text(l10n.authLoginButton),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

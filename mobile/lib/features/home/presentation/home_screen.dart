import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/auth/auth_controller.dart';
import '../../../core/auth/models/auth_state.dart';
import '../../../l10n/generated/app_localizations.dart';
import '../../../shared/widgets/offline_banner.dart';

/// ADR-0008: app única, UX adaptada ao *role* (Cliente/Prestador) depois
/// do login. Nesta ronda: o Cliente arranca o fluxo "criar pedido → ver
/// propostas"; o Prestador vê um marcador de posição — a caixa de entrada
/// de pedidos elegíveis fica para a ronda seguinte.
class HomeScreen extends ConsumerWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final authState = ref.watch(authControllerProvider);
    final user = switch (authState) {
      AuthStateAuthenticated(:final user) => user,
      _ => null,
    };

    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.appTitle),
        actions: [
          IconButton(
            key: const Key('logoutButton'),
            icon: const Icon(Icons.logout),
            tooltip: l10n.authLogout,
            onPressed: () =>
                ref.read(authControllerProvider.notifier).logout(),
          ),
        ],
      ),
      body: Column(
        children: [
          const OfflineBanner(),
          Expanded(
            child: Center(
              child: Padding(
                padding: const EdgeInsets.all(24),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      l10n.homeCustomerGreeting(
                        user?.displayName ?? user?.email ?? '',
                      ),
                      style: Theme.of(context).textTheme.headlineSmall,
                      textAlign: TextAlign.center,
                    ),
                    const SizedBox(height: 24),
                    if (user == null || user.isCustomer)
                      FilledButton.icon(
                        key: const Key('createRequestButton'),
                        icon: const Icon(Icons.add_circle_outline),
                        label: Text(l10n.homeCreateRequestCta),
                        onPressed: () => context.push('/requests/new'),
                      )
                    else if (user.isProvider)
                      Column(
                        children: [
                          const Icon(Icons.inbox_outlined, size: 48),
                          const SizedBox(height: 12),
                          Text(
                            l10n.homeProviderComingSoonTitle,
                            style: Theme.of(context).textTheme.titleMedium,
                          ),
                          const SizedBox(height: 8),
                          Text(
                            l10n.homeProviderComingSoonMessage,
                            textAlign: TextAlign.center,
                          ),
                        ],
                      ),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

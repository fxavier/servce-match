import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:url_launcher/url_launcher.dart';

import '../network/generated/models/models.dart';
import '../../l10n/generated/app_localizations.dart';
import 'version_status_repository.dart';

final versionStatusProvider = FutureProvider<VersionStatus>(
  (ref) => ref.watch(versionStatusRepositoryProvider).check(),
);

/// Envolve a app: bloqueia com um ecrã sem saída (só o link para a loja)
/// quando o servidor devolve `UPDATE_REQUIRED` (ADR-0008, §11.4).
/// `UPDATE_RECOMMENDED` é um aviso dispensável; `OK` (ou falha ao
/// verificar) deixa passar.
class VersionGate extends ConsumerWidget {
  const VersionGate({required this.child, super.key});

  final Widget child;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final versionStatus = ref.watch(versionStatusProvider);
    return versionStatus.when(
      data: (status) {
        if (status.status == AppUpdateState.updateRequired) {
          return _UpdateRequiredScreen(status: status);
        }
        return child;
      },
      loading: () => const _SplashScreen(),
      // Falha ao consultar o endpoint não pode bloquear a app (ver
      // VersionStatusRepository.check, que já intercepta a maioria dos
      // casos) — mas se mesmo assim chegar aqui, falha aberto.
      error: (_, _) => child,
    );
  }
}

class _SplashScreen extends StatelessWidget {
  const _SplashScreen();

  @override
  Widget build(BuildContext context) {
    return const Scaffold(
      body: Center(child: CircularProgressIndicator()),
    );
  }
}

class _UpdateRequiredScreen extends StatelessWidget {
  const _UpdateRequiredScreen({required this.status});

  final VersionStatus status;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Icon(Icons.system_update, size: 56),
                const SizedBox(height: 16),
                Text(
                  l10n.versionUpdateRequiredTitle,
                  style: Theme.of(context).textTheme.headlineSmall,
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 8),
                Text(
                  status.message ?? l10n.versionUpdateRequiredMessage,
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 24),
                if (status.storeUrl != null)
                  FilledButton(
                    onPressed: () => launchUrl(
                      Uri.parse(status.storeUrl!),
                      mode: LaunchMode.externalApplication,
                    ),
                    child: Text(l10n.versionUpdateButton),
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

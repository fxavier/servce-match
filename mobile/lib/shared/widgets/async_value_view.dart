import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/network/app_exception.dart';
import '../../l10n/generated/app_localizations.dart';

/// Trata de forma uniforme os quatro estados de um ecrã com I/O —
/// carregamento, dados (com vazio opcional) e erro (incluindo offline) —
/// para não repetir a lógica em cada *feature* (skill flutter-feature-slice
/// e CLAUDE.md §Qualidade).
class AsyncValueView<T> extends StatelessWidget {
  const AsyncValueView({
    required this.value,
    required this.dataBuilder,
    this.onRetry,
    this.isEmpty,
    this.emptyBuilder,
    super.key,
  });

  final AsyncValue<T> value;
  final Widget Function(BuildContext context, T data) dataBuilder;
  final VoidCallback? onRetry;
  final bool Function(T data)? isEmpty;
  final WidgetBuilder? emptyBuilder;

  @override
  Widget build(BuildContext context) {
    return value.when(
      data: (data) {
        if (isEmpty != null && isEmpty!(data) && emptyBuilder != null) {
          return emptyBuilder!(context);
        }
        return dataBuilder(context, data);
      },
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (error, stackTrace) => _ErrorState(error: error, onRetry: onRetry),
    );
  }
}

class _ErrorState extends StatelessWidget {
  const _ErrorState({required this.error, this.onRetry});

  final Object error;
  final VoidCallback? onRetry;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final (icon, title, message) = switch (error) {
      OfflineException() => (
          Icons.wifi_off,
          l10n.commonOfflineTitle,
          l10n.commonOfflineMessage,
        ),
      ServerProblemException(:final problem) => (
          Icons.error_outline,
          problem.title,
          problem.detail ?? l10n.commonGenericErrorMessage,
        ),
      _ => (
          Icons.error_outline,
          l10n.commonGenericErrorTitle,
          l10n.commonGenericErrorMessage,
        ),
    };

    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: 48, color: Theme.of(context).colorScheme.error),
            const SizedBox(height: 12),
            Text(
              title,
              style: Theme.of(context).textTheme.titleMedium,
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 8),
            Text(
              message,
              style: Theme.of(context).textTheme.bodyMedium,
              textAlign: TextAlign.center,
            ),
            if (onRetry != null) ...[
              const SizedBox(height: 16),
              FilledButton(onPressed: onRetry, child: Text(l10n.commonRetry)),
            ],
          ],
        ),
      ),
    );
  }
}

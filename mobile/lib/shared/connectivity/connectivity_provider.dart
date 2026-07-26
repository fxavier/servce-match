import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Mobile perde rede — assumir sempre-ligado é um defeito, não um caso
/// extremo (CLAUDE.md §Qualidade). Todo o ecrã com I/O observa isto.
final connectivityStreamProvider = StreamProvider<List<ConnectivityResult>>(
  (ref) => Connectivity().onConnectivityChanged,
);

/// `true` até se saber melhor (evita mostrar um aviso de offline durante
/// o primeiro *frame*, antes do plugin responder).
final isOnlineProvider = Provider<bool>((ref) {
  final result = ref.watch(connectivityStreamProvider).valueOrNull;
  if (result == null) return true;
  return result.any((r) => r != ConnectivityResult.none);
});

import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:package_info_plus/package_info_plus.dart';

import '../network/dio_provider.dart';
import '../network/generated/models/models.dart';

/// `GET /v1/app/version-status` — force-update (ADR-0008, §11.4 da
/// arquitetura). Endpoint público, chamado no arranque antes de qualquer
/// outra chamada autenticada.
class VersionStatusRepository {
  VersionStatusRepository(this._ref);

  final Ref _ref;

  Future<VersionStatus> check() async {
    final info = await PackageInfo.fromPlatform();
    final platform = defaultTargetPlatform == TargetPlatform.iOS
        ? 'IOS'
        : 'ANDROID';
    try {
      return await _ref
          .read(serviMatchApiProvider)
          .getAppVersionStatus(platform, info.version);
    } catch (_) {
      // Um erro aqui (offline, servidor em baixo) não pode impedir o
      // arranque da app: trata-se como "OK" silencioso e o resto do
      // fluxo prossegue. O `UPDATE_REQUIRED` só bloqueia quando o
      // servidor efetivamente o disser.
      return const VersionStatus(status: AppUpdateState.ok);
    }
  }
}

final versionStatusRepositoryProvider = Provider<VersionStatusRepository>(
  (ref) => VersionStatusRepository(ref),
);

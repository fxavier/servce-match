import 'package:freezed_annotation/freezed_annotation.dart';

import 'user_role.dart';

part 'app_user.freezed.dart';

/// Identidade do utilizador autenticado, extraída localmente do *access
/// token* (JWT) só para fins de UX (saudação, navegação por *role*).
///
/// **Não** é fonte de autoridade: o backend valida o JWT de forma
/// independente em cada pedido (CLAUDE.md §4).
@freezed
class AppUser with _$AppUser {
  const factory AppUser({
    required String subject,
    String? email,
    String? displayName,
    @Default(<UserRole>{}) Set<UserRole> roles,
  }) = _AppUser;

  const AppUser._();

  bool get isProvider => roles.contains(UserRole.provider);

  bool get isCustomer => roles.contains(UserRole.customer);
}

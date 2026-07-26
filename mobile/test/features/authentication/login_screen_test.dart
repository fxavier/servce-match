import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/auth/auth_controller.dart';
import 'package:mobile/core/auth/auth_exceptions.dart';
import 'package:mobile/core/auth/auth_repository.dart';
import 'package:mobile/core/auth/models/app_user.dart';
import 'package:mobile/core/auth/models/auth_session.dart';
import 'package:mobile/core/auth/models/user_role.dart';
import 'package:mobile/features/authentication/presentation/login_screen.dart';
import 'package:mocktail/mocktail.dart';

import '../../widget_helpers/pump_app.dart';

class _MockAuthRepository extends Mock implements AuthRepository {}

void main() {
  late _MockAuthRepository repository;

  setUp(() {
    repository = _MockAuthRepository();
    // Nenhuma sessão para restaurar no arranque: fica em `unauthenticated`
    // e o ecrã de login é o que se testa.
    when(() => repository.restoreSession()).thenAnswer((_) async => null);
  });

  Future<void> pumpLogin(WidgetTester tester) async {
    await pumpApp(
      tester,
      const LoginScreen(),
      overrides: [authRepositoryProvider.overrideWithValue(repository)],
    );
    // Deixa o `Future.microtask(restoreSession)` do AuthController resolver.
    await tester.pump();
  }

  testWidgets('login com sucesso chama o repositório (caminho principal)',
      (tester) async {
    when(() => repository.login()).thenAnswer(
      (_) async => AuthSession(
        user: const AppUser(subject: 'sub-1', roles: {UserRole.customer}),
        accessToken: 'token',
        accessTokenExpiry: DateTime.now().add(const Duration(minutes: 5)),
        refreshToken: 'refresh',
        idToken: 'id',
      ),
    );

    await pumpLogin(tester);

    expect(find.byKey(const Key('loginButton')), findsOneWidget);
    await tester.tap(find.byKey(const Key('loginButton')));
    await tester.pump();
    await tester.pump();

    verify(() => repository.login()).called(1);
  });

  testWidgets(
    'cancelamento pelo utilizador mostra mensagem (caso de erro)',
    (tester) async {
      when(() => repository.login())
          .thenThrow(const AuthCancelledException());

      await pumpLogin(tester);

      await tester.tap(find.byKey(const Key('loginButton')));
      await tester.pump();
      await tester.pump();

      expect(find.text('Início de sessão cancelado.'), findsOneWidget);
    },
  );
}

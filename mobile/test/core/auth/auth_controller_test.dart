import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/auth/auth_controller.dart';
import 'package:mobile/core/auth/auth_exceptions.dart';
import 'package:mobile/core/auth/auth_repository.dart';
import 'package:mobile/core/auth/models/app_user.dart';
import 'package:mobile/core/auth/models/auth_session.dart';
import 'package:mobile/core/auth/models/auth_state.dart';
import 'package:mobile/core/auth/models/user_role.dart';
import 'package:mocktail/mocktail.dart';

class _MockAuthRepository extends Mock implements AuthRepository {}

AuthSession _session({DateTime? expiry}) => AuthSession(
      user: const AppUser(subject: 'sub-1', roles: {UserRole.customer}),
      accessToken: 'access-token-1',
      accessTokenExpiry: expiry ?? DateTime.now().add(const Duration(minutes: 5)),
      refreshToken: 'refresh-token-1',
      idToken: 'id-token-1',
    );

void main() {
  late _MockAuthRepository repository;
  late ProviderContainer container;

  setUp(() {
    repository = _MockAuthRepository();
    container = ProviderContainer(
      overrides: [authRepositoryProvider.overrideWithValue(repository)],
    );
    addTearDown(container.dispose);
  });

  Future<void> pumpMicrotasks() => Future<void>.delayed(Duration.zero);

  group('restoreSession (arranque)', () {
    test('sessão válida em secure storage -> authenticated', () async {
      when(() => repository.restoreSession())
          .thenAnswer((_) async => _session());

      // build() já dispara restoreSession via Future.microtask.
      container.read(authControllerProvider);
      await pumpMicrotasks();

      final state = container.read(authControllerProvider);
      expect(state, isA<AuthStateAuthenticated>());
      expect(
        (state as AuthStateAuthenticated).user.subject,
        'sub-1',
      );
    });

    test('sem refresh token guardado -> unauthenticated(loggedOut)', () async {
      when(() => repository.restoreSession()).thenAnswer((_) async => null);

      container.read(authControllerProvider);
      await pumpMicrotasks();

      final state = container.read(authControllerProvider);
      expect(state, isA<AuthStateUnauthenticated>());
      expect(
        (state as AuthStateUnauthenticated).reason,
        AuthUnauthenticatedReason.loggedOut,
      );
    });
  });

  group('login', () {
    test('sucesso -> authenticated (caminho principal)', () async {
      when(() => repository.restoreSession()).thenAnswer((_) async => null);
      when(() => repository.login()).thenAnswer((_) async => _session());

      final controller = container.read(authControllerProvider.notifier);
      await pumpMicrotasks();

      await controller.login();

      expect(
        container.read(authControllerProvider),
        isA<AuthStateAuthenticated>(),
      );
      verify(() => repository.login()).called(1);
    });

    test(
      'cancelado pelo utilizador no browser -> unauthenticated (caso de erro)',
      () async {
        when(() => repository.restoreSession()).thenAnswer((_) async => null);
        when(() => repository.login())
            .thenThrow(const AuthCancelledException());

        final controller = container.read(authControllerProvider.notifier);
        await pumpMicrotasks();

        await controller.login();

        final state = container.read(authControllerProvider);
        expect(state, isA<AuthStateUnauthenticated>());
        expect(
          (state as AuthStateUnauthenticated).reason,
          AuthUnauthenticatedReason.cancelledByUser,
        );
      },
    );
  });

  test('logout limpa a sessão e chama o repositório', () async {
    when(() => repository.restoreSession())
        .thenAnswer((_) async => _session());
    when(() => repository.logout()).thenAnswer((_) async {});

    final controller = container.read(authControllerProvider.notifier);
    await pumpMicrotasks();
    expect(
      container.read(authControllerProvider),
      isA<AuthStateAuthenticated>(),
    );

    await controller.logout();

    expect(
      container.read(authControllerProvider),
      isA<AuthStateUnauthenticated>(),
    );
    verify(() => repository.logout()).called(1);
  });

  group('ensureFreshAccessToken (usado pelo interceptor HTTP)', () {
    test('devolve o token em memória se não estiver perto de expirar',
        () async {
      when(() => repository.restoreSession())
          .thenAnswer((_) async => _session());

      final controller = container.read(authControllerProvider.notifier);
      await pumpMicrotasks();

      final token = await controller.ensureFreshAccessToken();

      expect(token, 'access-token-1');
      // Não deve ter chamado restoreSession outra vez (só o do arranque).
      verify(() => repository.restoreSession()).called(1);
    });

    test('renova quando o token está perto de expirar', () async {
      when(() => repository.restoreSession()).thenAnswer(
        (_) async => _session(
          expiry: DateTime.now().add(const Duration(seconds: 5)),
        ),
      );

      final controller = container.read(authControllerProvider.notifier);
      await pumpMicrotasks();

      final token = await controller.ensureFreshAccessToken();

      expect(token, 'access-token-1');
      // Uma vez no arranque + uma vez pelo refresh.
      verify(() => repository.restoreSession()).called(2);
    });

    test('sessão expirada ao renovar -> unauthenticated(sessionExpired)',
        () async {
      when(() => repository.restoreSession()).thenAnswer(
        (_) async => _session(
          expiry: DateTime.now().add(const Duration(seconds: 5)),
        ),
      );

      final controller = container.read(authControllerProvider.notifier);
      await pumpMicrotasks();

      // A partir daqui o refresh token deixou de ser válido.
      when(() => repository.restoreSession()).thenAnswer((_) async => null);

      final token = await controller.ensureFreshAccessToken();

      expect(token, isNull);
      final state = container.read(authControllerProvider);
      expect(state, isA<AuthStateUnauthenticated>());
      expect(
        (state as AuthStateUnauthenticated).reason,
        AuthUnauthenticatedReason.sessionExpired,
      );
    });
  });
}

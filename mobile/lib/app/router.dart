import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../core/auth/auth_controller.dart';
import '../core/auth/models/auth_state.dart';
import '../features/authentication/presentation/login_screen.dart';
import '../features/home/presentation/home_screen.dart';
import '../features/proposals/presentation/proposals_screen.dart';
import '../features/requests/presentation/create_request_screen.dart';

/// Deep link para o ecrã de propostas de um pedido — reutilizável por um
/// push de "nova proposta" quando o `core/push` estiver ligado ao FCM
/// (ADR-0008: "deep links via go_router").
String proposalsRoutePath(String requestId) => '/requests/$requestId/proposals';

final goRouterProvider = Provider<GoRouter>((ref) {
  final authState = ref.watch(authControllerProvider);

  return GoRouter(
    initialLocation: '/splash',
    redirect: (context, state) {
      final location = state.matchedLocation;
      switch (authState) {
        case AuthStateUnknown():
          return location == '/splash' ? null : '/splash';
        case AuthStateUnauthenticated():
          return location == '/login' ? null : '/login';
        case AuthStateAuthenticated():
          if (location == '/login' || location == '/splash') return '/home';
          return null;
      }
    },
    routes: [
      GoRoute(
        path: '/splash',
        builder: (context, state) => const _SplashPlaceholder(),
      ),
      GoRoute(
        path: '/login',
        builder: (context, state) => const LoginScreen(),
      ),
      GoRoute(
        path: '/home',
        builder: (context, state) => const HomeScreen(),
      ),
      GoRoute(
        path: '/requests/new',
        builder: (context, state) => const CreateRequestScreen(),
      ),
      GoRoute(
        path: '/requests/:requestId/proposals',
        builder: (context, state) => ProposalsScreen(
          requestId: state.pathParameters['requestId']!,
        ),
      ),
    ],
  );
});

/// Enquanto `AuthController` restaura a sessão a partir do secure storage.
/// A app já mostra este *splash* dentro do `VersionGate` (que tem o seu
/// próprio *loading*); este cobre o intervalo seguinte, mais curto.
class _SplashPlaceholder extends StatelessWidget {
  const _SplashPlaceholder();

  @override
  Widget build(BuildContext context) {
    return const Scaffold(body: Center(child: CircularProgressIndicator()));
  }
}

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/network/app_exception.dart';
import 'package:mobile/core/network/generated/models/models.dart';
import 'package:mobile/shared/widgets/async_value_view.dart';

import '../../widget_helpers/pump_app.dart';

void main() {
  testWidgets('carregamento mostra indicador de progresso', (tester) async {
    await pumpApp(
      tester,
      AsyncValueView<int>(
        value: const AsyncLoading<int>(),
        dataBuilder: (context, data) => Text('$data'),
      ),
    );

    expect(find.byType(CircularProgressIndicator), findsOneWidget);
  });

  testWidgets('dados mostram o conteúdo (caminho principal)', (tester) async {
    await pumpApp(
      tester,
      AsyncValueView<int>(
        value: const AsyncData<int>(42),
        dataBuilder: (context, data) => Text('valor: $data'),
      ),
    );

    expect(find.text('valor: 42'), findsOneWidget);
  });

  testWidgets('lista vazia usa o emptyBuilder em vez do dataBuilder',
      (tester) async {
    await pumpApp(
      tester,
      AsyncValueView<List<int>>(
        value: const AsyncData<List<int>>([]),
        isEmpty: (data) => data.isEmpty,
        emptyBuilder: (context) => const Text('sem itens'),
        dataBuilder: (context, data) => Text('${data.length} itens'),
      ),
    );

    expect(find.text('sem itens'), findsOneWidget);
    expect(find.text('0 itens'), findsNothing);
  });

  testWidgets(
    'offline mostra o estado dedicado e chama onRetry (caso de erro)',
    (tester) async {
      var retried = false;
      await pumpApp(
        tester,
        AsyncValueView<int>(
          value: AsyncError<int>(const OfflineException(), StackTrace.empty),
          onRetry: () => retried = true,
          dataBuilder: (context, data) => Text('$data'),
        ),
      );

      expect(find.byIcon(Icons.wifi_off), findsOneWidget);
      await tester.tap(find.byType(FilledButton));
      expect(retried, isTrue);
    },
  );

  testWidgets(
    'erro do servidor mostra o title/detail do ProblemDetails',
    (tester) async {
      final problem = ServerProblemException(
        const ProblemDetails(
          title: 'Subscrição necessária',
          status: 403,
          detail: 'É preciso ativar a subscrição.',
          type: 'https://errors.servimatch.pt/subscription-required',
        ),
      );

      await pumpApp(
        tester,
        AsyncValueView<int>(
          value: AsyncError<int>(problem, StackTrace.empty),
          dataBuilder: (context, data) => Text('$data'),
        ),
      );

      expect(find.text('Subscrição necessária'), findsOneWidget);
      expect(find.text('É preciso ativar a subscrição.'), findsOneWidget);
    },
  );
}

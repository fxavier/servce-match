import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/generated/models/models.dart';
import '../data/request_repository.dart';

class CreateRequestController
    extends AutoDisposeAsyncNotifier<ServiceRequest?> {
  @override
  FutureOr<ServiceRequest?> build() => null;

  Future<void> submit(CreateServiceRequest input) async {
    state = const AsyncValue.loading();
    state = await AsyncValue.guard(
      () => ref.read(requestRepositoryProvider).createAndPublish(input),
    );
  }
}

final createRequestControllerProvider = AutoDisposeAsyncNotifierProvider<
    CreateRequestController, ServiceRequest?>(CreateRequestController.new);

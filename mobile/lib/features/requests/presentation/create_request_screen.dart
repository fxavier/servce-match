import 'dart:async' show unawaited;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/network/app_exception.dart';
import '../../../core/network/generated/models/models.dart';
import '../../../l10n/generated/app_localizations.dart';
import '../../../shared/widgets/async_value_view.dart';
import '../../../shared/widgets/offline_banner.dart';
import '../data/request_repository.dart';
import 'create_request_controller.dart';

/// `POST /v1/requests` + `POST /v1/requests/{id}/publish` — primeiro passo
/// do fluxo mínimo desta ronda: autenticar → **criar pedido** → ver
/// propostas.
class CreateRequestScreen extends ConsumerStatefulWidget {
  const CreateRequestScreen({super.key});

  @override
  ConsumerState<CreateRequestScreen> createState() =>
      _CreateRequestScreenState();
}

class _CreateRequestScreenState extends ConsumerState<CreateRequestScreen> {
  final _formKey = GlobalKey<FormState>();
  final _titleController = TextEditingController();
  final _descriptionController = TextEditingController();
  final _cityController = TextEditingController();
  final _postalCodeController = TextEditingController();

  String? _categoryId;
  UrgencyLevel _urgency = UrgencyLevel.normal;

  @override
  void dispose() {
    _titleController.dispose();
    _descriptionController.dispose();
    _cityController.dispose();
    _postalCodeController.dispose();
    super.dispose();
  }

  void _submit() {
    final l10n = AppLocalizations.of(context);
    if (_categoryId == null || !_formKey.currentState!.validate()) return;

    final input = CreateServiceRequest(
      categoryId: _categoryId!,
      title: _titleController.text.trim(),
      description: _descriptionController.text.trim().isEmpty
          ? null
          : _descriptionController.text.trim(),
      address: Address(
        city: _cityController.text.trim(),
        postalCode: _postalCodeController.text.trim(),
        country: 'PT',
      ),
      urgency: _urgency,
    );

    unawaited(
      ref.read(createRequestControllerProvider.notifier).submit(input),
    );
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(l10n.requestFormSubmitting)));
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final categories = ref.watch(categoriesProvider);
    final submission = ref.watch(createRequestControllerProvider);

    ref.listen(createRequestControllerProvider, (previous, next) {
      next.whenOrNull(
        data: (created) {
          if (created == null) return;
          ScaffoldMessenger.of(context)
            ..hideCurrentSnackBar()
            ..showSnackBar(SnackBar(content: Text(l10n.requestFormSuccess)));
          context.pushReplacement('/requests/${created.id}/proposals');
        },
        error: (error, stackTrace) {
          final message = error is ServerProblemException
              ? (error.problem.detail ?? error.problem.title)
              : l10n.commonGenericErrorMessage;
          ScaffoldMessenger.of(context)
            ..hideCurrentSnackBar()
            ..showSnackBar(SnackBar(content: Text(message)));
        },
      );
    });

    final isSubmitting = submission.isLoading;

    return Scaffold(
      appBar: AppBar(title: Text(l10n.requestFormTitle)),
      body: Column(
        children: [
          const OfflineBanner(),
          Expanded(
            child: AsyncValueView<List<Category>>(
              value: categories,
              onRetry: () => ref.invalidate(categoriesProvider),
              dataBuilder: (context, items) => SingleChildScrollView(
                padding: const EdgeInsets.all(16),
                child: Form(
                  key: _formKey,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      DropdownButtonFormField<String>(
                        key: const Key('categoryField'),
                        value: _categoryId,
                        decoration: InputDecoration(
                          labelText: l10n.requestFormCategoryLabel,
                        ),
                        items: [
                          for (final category in items)
                            DropdownMenuItem(
                              value: category.id,
                              child: Text(category.name),
                            ),
                        ],
                        onChanged: (value) =>
                            setState(() => _categoryId = value),
                        validator: (value) =>
                            value == null ? l10n.requestFormValidationRequired : null,
                      ),
                      const SizedBox(height: 12),
                      TextFormField(
                        key: const Key('titleField'),
                        controller: _titleController,
                        decoration: InputDecoration(
                          labelText: l10n.requestFormTitleLabel,
                        ),
                        validator: (value) {
                          final length = value?.trim().length ?? 0;
                          if (length < 3 || length > 140) {
                            return l10n.requestFormValidationTitleLength;
                          }
                          return null;
                        },
                      ),
                      const SizedBox(height: 12),
                      TextFormField(
                        key: const Key('descriptionField'),
                        controller: _descriptionController,
                        decoration: InputDecoration(
                          labelText: l10n.requestFormDescriptionLabel,
                        ),
                        maxLines: 3,
                      ),
                      const SizedBox(height: 12),
                      TextFormField(
                        key: const Key('cityField'),
                        controller: _cityController,
                        decoration: InputDecoration(
                          labelText: l10n.requestFormCityLabel,
                        ),
                        validator: (value) => (value == null || value.trim().isEmpty)
                            ? l10n.requestFormValidationRequired
                            : null,
                      ),
                      const SizedBox(height: 12),
                      TextFormField(
                        key: const Key('postalCodeField'),
                        controller: _postalCodeController,
                        decoration: InputDecoration(
                          labelText: l10n.requestFormPostalCodeLabel,
                        ),
                        validator: (value) => (value == null || value.trim().isEmpty)
                            ? l10n.requestFormValidationRequired
                            : null,
                      ),
                      const SizedBox(height: 12),
                      DropdownButtonFormField<UrgencyLevel>(
                        key: const Key('urgencyField'),
                        value: _urgency,
                        decoration: InputDecoration(
                          labelText: l10n.requestFormUrgencyLabel,
                        ),
                        items: [
                          DropdownMenuItem(
                            value: UrgencyLevel.low,
                            child: Text(l10n.urgencyLow),
                          ),
                          DropdownMenuItem(
                            value: UrgencyLevel.normal,
                            child: Text(l10n.urgencyNormal),
                          ),
                          DropdownMenuItem(
                            value: UrgencyLevel.high,
                            child: Text(l10n.urgencyHigh),
                          ),
                          DropdownMenuItem(
                            value: UrgencyLevel.urgent,
                            child: Text(l10n.urgencyUrgent),
                          ),
                        ],
                        onChanged: (value) => setState(
                          () => _urgency = value ?? UrgencyLevel.normal,
                        ),
                      ),
                      const SizedBox(height: 24),
                      FilledButton(
                        key: const Key('submitRequestButton'),
                        onPressed: isSubmitting ? null : _submit,
                        child: isSubmitting
                            ? const SizedBox(
                                width: 20,
                                height: 20,
                                child: CircularProgressIndicator(strokeWidth: 2),
                              )
                            : Text(l10n.requestFormSubmit),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Portuguese (`pt`).
class AppLocalizationsPt extends AppLocalizations {
  AppLocalizationsPt([String locale = 'pt']) : super(locale);

  @override
  String get appTitle => 'ServiMatch';

  @override
  String get commonRetry => 'Tentar novamente';

  @override
  String get commonCancel => 'Cancelar';

  @override
  String get commonSave => 'Guardar';

  @override
  String get commonLoading => 'A carregar...';

  @override
  String get commonOfflineTitle => 'Sem ligação à internet';

  @override
  String get commonOfflineMessage => 'Verifica a tua ligação e tenta novamente.';

  @override
  String get commonGenericErrorTitle => 'Algo correu mal';

  @override
  String get commonGenericErrorMessage => 'Não foi possível concluir o pedido. Tenta novamente.';

  @override
  String get commonEmptyTitle => 'Nada para mostrar';

  @override
  String get commonUnknown => 'Desconhecido';

  @override
  String get authLoginTitle => 'Entrar no ServiMatch';

  @override
  String get authLoginSubtitle => 'Encontra prestadores de confiança perto de ti.';

  @override
  String get authLoginButton => 'Entrar';

  @override
  String get authLoggingIn => 'A abrir sessão...';

  @override
  String get authCancelled => 'Início de sessão cancelado.';

  @override
  String get authSessionExpired => 'A tua sessão expirou. Entra novamente.';

  @override
  String get authLogout => 'Terminar sessão';

  @override
  String get versionUpdateRequiredTitle => 'Atualização necessária';

  @override
  String get versionUpdateRequiredMessage => 'Esta versão da app já não é suportada. Atualiza para continuares a usar o ServiMatch.';

  @override
  String get versionUpdateButton => 'Atualizar agora';

  @override
  String get versionUpdateRecommendedMessage => 'Existe uma nova versão do ServiMatch disponível.';

  @override
  String homeCustomerGreeting(String name) {
    return 'Olá, $name';
  }

  @override
  String get homeCreateRequestCta => 'Criar um pedido';

  @override
  String get homeProviderComingSoonTitle => 'Área do prestador';

  @override
  String get homeProviderComingSoonMessage => 'A caixa de entrada de pedidos para prestadores chega numa próxima versão.';

  @override
  String get requestFormTitle => 'Novo pedido';

  @override
  String get requestFormCategoryLabel => 'Categoria';

  @override
  String get requestFormTitleLabel => 'Título';

  @override
  String get requestFormDescriptionLabel => 'Descrição';

  @override
  String get requestFormCityLabel => 'Cidade';

  @override
  String get requestFormPostalCodeLabel => 'Código postal';

  @override
  String get requestFormUrgencyLabel => 'Urgência';

  @override
  String get requestFormSubmit => 'Publicar pedido';

  @override
  String get requestFormSubmitting => 'A publicar...';

  @override
  String get requestFormSuccess => 'Pedido publicado com sucesso.';

  @override
  String get requestFormValidationRequired => 'Campo obrigatório.';

  @override
  String get requestFormValidationTitleLength => 'O título deve ter entre 3 e 140 carateres.';

  @override
  String get urgencyLow => 'Sem pressa';

  @override
  String get urgencyNormal => 'Normal';

  @override
  String get urgencyHigh => 'Alta';

  @override
  String get urgencyUrgent => 'Urgente';

  @override
  String get proposalsTitle => 'Propostas recebidas';

  @override
  String get proposalsEmptyMessage => 'Ainda não há propostas para este pedido.';

  @override
  String proposalsCountLabel(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count propostas',
      one: '1 proposta',
      zero: 'Sem propostas',
    );
    return '$_temp0';
  }

  @override
  String get proposalPriceLabel => 'Preço';

  @override
  String proposalLeadTimeLabel(int days) {
    return 'Prazo: $days dias';
  }

  @override
  String get subscriptionRequiredTitle => 'Subscrição necessária';

  @override
  String get subscriptionRequiredMessage => 'Precisas de uma subscrição ativa para usar esta funcionalidade.';
}

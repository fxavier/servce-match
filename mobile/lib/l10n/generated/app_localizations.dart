import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:intl/intl.dart' as intl;

import 'app_localizations_pt.dart';

// ignore_for_file: type=lint

/// Callers can lookup localized strings with an instance of AppLocalizations
/// returned by `AppLocalizations.of(context)`.
///
/// Applications need to include `AppLocalizations.delegate()` in their app's
/// `localizationDelegates` list, and the locales they support in the app's
/// `supportedLocales` list. For example:
///
/// ```dart
/// import 'generated/app_localizations.dart';
///
/// return MaterialApp(
///   localizationsDelegates: AppLocalizations.localizationsDelegates,
///   supportedLocales: AppLocalizations.supportedLocales,
///   home: MyApplicationHome(),
/// );
/// ```
///
/// ## Update pubspec.yaml
///
/// Please make sure to update your pubspec.yaml to include the following
/// packages:
///
/// ```yaml
/// dependencies:
///   # Internationalization support.
///   flutter_localizations:
///     sdk: flutter
///   intl: any # Use the pinned version from flutter_localizations
///
///   # Rest of dependencies
/// ```
///
/// ## iOS Applications
///
/// iOS applications define key application metadata, including supported
/// locales, in an Info.plist file that is built into the application bundle.
/// To configure the locales supported by your app, you’ll need to edit this
/// file.
///
/// First, open your project’s ios/Runner.xcworkspace Xcode workspace file.
/// Then, in the Project Navigator, open the Info.plist file under the Runner
/// project’s Runner folder.
///
/// Next, select the Information Property List item, select Add Item from the
/// Editor menu, then select Localizations from the pop-up menu.
///
/// Select and expand the newly-created Localizations item then, for each
/// locale your application supports, add a new item and select the locale
/// you wish to add from the pop-up menu in the Value field. This list should
/// be consistent with the languages listed in the AppLocalizations.supportedLocales
/// property.
abstract class AppLocalizations {
  AppLocalizations(String locale) : localeName = intl.Intl.canonicalizedLocale(locale.toString());

  final String localeName;

  static AppLocalizations of(BuildContext context) {
    return Localizations.of<AppLocalizations>(context, AppLocalizations)!;
  }

  static const LocalizationsDelegate<AppLocalizations> delegate = _AppLocalizationsDelegate();

  /// A list of this localizations delegate along with the default localizations
  /// delegates.
  ///
  /// Returns a list of localizations delegates containing this delegate along with
  /// GlobalMaterialLocalizations.delegate, GlobalCupertinoLocalizations.delegate,
  /// and GlobalWidgetsLocalizations.delegate.
  ///
  /// Additional delegates can be added by appending to this list in
  /// MaterialApp. This list does not have to be used at all if a custom list
  /// of delegates is preferred or required.
  static const List<LocalizationsDelegate<dynamic>> localizationsDelegates = <LocalizationsDelegate<dynamic>>[
    delegate,
    GlobalMaterialLocalizations.delegate,
    GlobalCupertinoLocalizations.delegate,
    GlobalWidgetsLocalizations.delegate,
  ];

  /// A list of this localizations delegate's supported locales.
  static const List<Locale> supportedLocales = <Locale>[
    Locale('pt')
  ];

  /// No description provided for @appTitle.
  ///
  /// In pt, this message translates to:
  /// **'ServiMatch'**
  String get appTitle;

  /// No description provided for @commonRetry.
  ///
  /// In pt, this message translates to:
  /// **'Tentar novamente'**
  String get commonRetry;

  /// No description provided for @commonCancel.
  ///
  /// In pt, this message translates to:
  /// **'Cancelar'**
  String get commonCancel;

  /// No description provided for @commonSave.
  ///
  /// In pt, this message translates to:
  /// **'Guardar'**
  String get commonSave;

  /// No description provided for @commonLoading.
  ///
  /// In pt, this message translates to:
  /// **'A carregar...'**
  String get commonLoading;

  /// No description provided for @commonOfflineTitle.
  ///
  /// In pt, this message translates to:
  /// **'Sem ligação à internet'**
  String get commonOfflineTitle;

  /// No description provided for @commonOfflineMessage.
  ///
  /// In pt, this message translates to:
  /// **'Verifica a tua ligação e tenta novamente.'**
  String get commonOfflineMessage;

  /// No description provided for @commonGenericErrorTitle.
  ///
  /// In pt, this message translates to:
  /// **'Algo correu mal'**
  String get commonGenericErrorTitle;

  /// No description provided for @commonGenericErrorMessage.
  ///
  /// In pt, this message translates to:
  /// **'Não foi possível concluir o pedido. Tenta novamente.'**
  String get commonGenericErrorMessage;

  /// No description provided for @commonEmptyTitle.
  ///
  /// In pt, this message translates to:
  /// **'Nada para mostrar'**
  String get commonEmptyTitle;

  /// No description provided for @commonUnknown.
  ///
  /// In pt, this message translates to:
  /// **'Desconhecido'**
  String get commonUnknown;

  /// No description provided for @authLoginTitle.
  ///
  /// In pt, this message translates to:
  /// **'Entrar no ServiMatch'**
  String get authLoginTitle;

  /// No description provided for @authLoginSubtitle.
  ///
  /// In pt, this message translates to:
  /// **'Encontra prestadores de confiança perto de ti.'**
  String get authLoginSubtitle;

  /// No description provided for @authLoginButton.
  ///
  /// In pt, this message translates to:
  /// **'Entrar'**
  String get authLoginButton;

  /// No description provided for @authLoggingIn.
  ///
  /// In pt, this message translates to:
  /// **'A abrir sessão...'**
  String get authLoggingIn;

  /// No description provided for @authCancelled.
  ///
  /// In pt, this message translates to:
  /// **'Início de sessão cancelado.'**
  String get authCancelled;

  /// No description provided for @authSessionExpired.
  ///
  /// In pt, this message translates to:
  /// **'A tua sessão expirou. Entra novamente.'**
  String get authSessionExpired;

  /// No description provided for @authLogout.
  ///
  /// In pt, this message translates to:
  /// **'Terminar sessão'**
  String get authLogout;

  /// No description provided for @versionUpdateRequiredTitle.
  ///
  /// In pt, this message translates to:
  /// **'Atualização necessária'**
  String get versionUpdateRequiredTitle;

  /// No description provided for @versionUpdateRequiredMessage.
  ///
  /// In pt, this message translates to:
  /// **'Esta versão da app já não é suportada. Atualiza para continuares a usar o ServiMatch.'**
  String get versionUpdateRequiredMessage;

  /// No description provided for @versionUpdateButton.
  ///
  /// In pt, this message translates to:
  /// **'Atualizar agora'**
  String get versionUpdateButton;

  /// No description provided for @versionUpdateRecommendedMessage.
  ///
  /// In pt, this message translates to:
  /// **'Existe uma nova versão do ServiMatch disponível.'**
  String get versionUpdateRecommendedMessage;

  /// No description provided for @homeCustomerGreeting.
  ///
  /// In pt, this message translates to:
  /// **'Olá, {name}'**
  String homeCustomerGreeting(String name);

  /// No description provided for @homeCreateRequestCta.
  ///
  /// In pt, this message translates to:
  /// **'Criar um pedido'**
  String get homeCreateRequestCta;

  /// No description provided for @homeProviderComingSoonTitle.
  ///
  /// In pt, this message translates to:
  /// **'Área do prestador'**
  String get homeProviderComingSoonTitle;

  /// No description provided for @homeProviderComingSoonMessage.
  ///
  /// In pt, this message translates to:
  /// **'A caixa de entrada de pedidos para prestadores chega numa próxima versão.'**
  String get homeProviderComingSoonMessage;

  /// No description provided for @requestFormTitle.
  ///
  /// In pt, this message translates to:
  /// **'Novo pedido'**
  String get requestFormTitle;

  /// No description provided for @requestFormCategoryLabel.
  ///
  /// In pt, this message translates to:
  /// **'Categoria'**
  String get requestFormCategoryLabel;

  /// No description provided for @requestFormTitleLabel.
  ///
  /// In pt, this message translates to:
  /// **'Título'**
  String get requestFormTitleLabel;

  /// No description provided for @requestFormDescriptionLabel.
  ///
  /// In pt, this message translates to:
  /// **'Descrição'**
  String get requestFormDescriptionLabel;

  /// No description provided for @requestFormCityLabel.
  ///
  /// In pt, this message translates to:
  /// **'Cidade'**
  String get requestFormCityLabel;

  /// No description provided for @requestFormPostalCodeLabel.
  ///
  /// In pt, this message translates to:
  /// **'Código postal'**
  String get requestFormPostalCodeLabel;

  /// No description provided for @requestFormUrgencyLabel.
  ///
  /// In pt, this message translates to:
  /// **'Urgência'**
  String get requestFormUrgencyLabel;

  /// No description provided for @requestFormSubmit.
  ///
  /// In pt, this message translates to:
  /// **'Publicar pedido'**
  String get requestFormSubmit;

  /// No description provided for @requestFormSubmitting.
  ///
  /// In pt, this message translates to:
  /// **'A publicar...'**
  String get requestFormSubmitting;

  /// No description provided for @requestFormSuccess.
  ///
  /// In pt, this message translates to:
  /// **'Pedido publicado com sucesso.'**
  String get requestFormSuccess;

  /// No description provided for @requestFormValidationRequired.
  ///
  /// In pt, this message translates to:
  /// **'Campo obrigatório.'**
  String get requestFormValidationRequired;

  /// No description provided for @requestFormValidationTitleLength.
  ///
  /// In pt, this message translates to:
  /// **'O título deve ter entre 3 e 140 carateres.'**
  String get requestFormValidationTitleLength;

  /// No description provided for @urgencyLow.
  ///
  /// In pt, this message translates to:
  /// **'Sem pressa'**
  String get urgencyLow;

  /// No description provided for @urgencyNormal.
  ///
  /// In pt, this message translates to:
  /// **'Normal'**
  String get urgencyNormal;

  /// No description provided for @urgencyHigh.
  ///
  /// In pt, this message translates to:
  /// **'Alta'**
  String get urgencyHigh;

  /// No description provided for @urgencyUrgent.
  ///
  /// In pt, this message translates to:
  /// **'Urgente'**
  String get urgencyUrgent;

  /// No description provided for @proposalsTitle.
  ///
  /// In pt, this message translates to:
  /// **'Propostas recebidas'**
  String get proposalsTitle;

  /// No description provided for @proposalsEmptyMessage.
  ///
  /// In pt, this message translates to:
  /// **'Ainda não há propostas para este pedido.'**
  String get proposalsEmptyMessage;

  /// No description provided for @proposalsCountLabel.
  ///
  /// In pt, this message translates to:
  /// **'{count, plural, =0{Sem propostas} one{1 proposta} other{{count} propostas}}'**
  String proposalsCountLabel(int count);

  /// No description provided for @proposalPriceLabel.
  ///
  /// In pt, this message translates to:
  /// **'Preço'**
  String get proposalPriceLabel;

  /// No description provided for @proposalLeadTimeLabel.
  ///
  /// In pt, this message translates to:
  /// **'Prazo: {days} dias'**
  String proposalLeadTimeLabel(int days);

  /// No description provided for @subscriptionRequiredTitle.
  ///
  /// In pt, this message translates to:
  /// **'Subscrição necessária'**
  String get subscriptionRequiredTitle;

  /// No description provided for @subscriptionRequiredMessage.
  ///
  /// In pt, this message translates to:
  /// **'Precisas de uma subscrição ativa para usar esta funcionalidade.'**
  String get subscriptionRequiredMessage;
}

class _AppLocalizationsDelegate extends LocalizationsDelegate<AppLocalizations> {
  const _AppLocalizationsDelegate();

  @override
  Future<AppLocalizations> load(Locale locale) {
    return SynchronousFuture<AppLocalizations>(lookupAppLocalizations(locale));
  }

  @override
  bool isSupported(Locale locale) => <String>['pt'].contains(locale.languageCode);

  @override
  bool shouldReload(_AppLocalizationsDelegate old) => false;
}

AppLocalizations lookupAppLocalizations(Locale locale) {


  // Lookup logic when only language code is specified.
  switch (locale.languageCode) {
    case 'pt': return AppLocalizationsPt();
  }

  throw FlutterError(
    'AppLocalizations.delegate failed to load unsupported locale "$locale". This is likely '
    'an issue with the localizations generation tool. Please file an issue '
    'on GitHub with a reproducible sample app and the gen-l10n configuration '
    'that was used.'
  );
}

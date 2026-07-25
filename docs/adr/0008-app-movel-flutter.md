# ADR-0008: Aplicação móvel Flutter (multi-cliente, app única, fast-follow)

- **Estado:** Aceite
- **Data:** 2026-07-24
- **Decisores:** Equipa ServiMatch
- **Relacionado:** ADR-0002, ADR-0009

## Contexto e Problema

O sistema passa a exigir uma **aplicação móvel** além do cliente web. É preciso decidir a tecnologia, o âmbito de plataformas, se serve um ou ambos os perfis (Cliente/Prestador) e como se sequencia face ao MVP web, sem duplicar backend nem contrato de API.

## Fatores de Decisão

- Um só código base para iOS e Android (custo de equipa pequena).
- Reutilização do backend/API já validado no web.
- Valor de mobile para o **Prestador** (notificações de novos pedidos, responder em movimento) e para o **Cliente** (criar pedidos, chat, acompanhar propostas).
- Time-to-market e risco de paralelizar demasiado.

## Opções Consideradas

**Tecnologia:** Flutter · React Native · nativo (Kotlin + Swift) · PWA.
**Âmbito de app:** app única adaptável por *role* · duas apps (Cliente / Prestador).
**Plataformas:** iOS + Android · uma primeiro.
**Timing:** no MVP em paralelo · *fast-follow* após web · mobile-first.

## Decisão

- **Flutter** (código base único Dart) para **iOS + Android**.
- **App única** que se adapta ao *role* (Cliente ou Prestador) após login.
- **Fast-follow**: o web é o MVP; a app arranca logo a seguir sobre a **mesma API REST**.
- Preparar **desde o MVP web** os pré-requisitos de backend que a app precisa e que são caros de retro-encaixar: **versionamento + force-update da API** (§11.4 da arquitetura) e a entidade **`DeviceToken`** (push multi-dispositivo).

## Racional

Flutter dá um código base único com boa performance e ecossistema maduro (OIDC, mapas OSM, FCM, STOMP), adequado a uma equipa pequena. Uma app única evita duplicar build/publicação/manutenção; a diferença Cliente/Prestador é sobretudo de UX e de *gating*, não justifica duas apps no arranque. *Fast-follow* reduz risco: a API é exercitada e estabilizada pelo web antes de a app depender dela.

## Consequências

**Positivas**
- Um artefacto de código para duas plataformas; reutilização total do backend.
- Mobile disponível para ambos os perfis, com forte valor para o Prestador.
- Menor risco por reutilizar API validada.

**Negativas / Custos**
- Novo pipeline de build/assinatura e **publicação nas lojas** (App Store/Play), com ciclos de revisão.
- Necessidade de **compatibilidade retroativa** de API e **force-update** (clientes não atualizam à força) — ADR relacionado / §11.4.
- Complexidade de UX ao servir dois perfis numa app (mitigada por navegação/guards por *role*).

## Alternativas rejeitadas

- **React Native:** viável, mas Flutter oferece maior consistência de UI e um único *toolchain*; a equipa não tem investimento prévio decisivo em RN.
- **Nativo (Kotlin+Swift):** duplica esforço sem ganho proporcional para este produto.
- **PWA apenas:** limitações de push/integração nativa e presença nas lojas.
- **Duas apps separadas:** duplica esforço de manutenção/publicação sem valor suficiente no MVP.
- **Mobile em paralelo no MVP / mobile-first:** maior custo e risco iniciais para uma equipa pequena.

## Ligações

- Flutter: https://docs.flutter.dev
- FlutterFire / firebase_messaging: https://firebase.flutter.dev/docs/messaging/overview
- flutter_map (OSM): https://pub.dev/packages/flutter_map

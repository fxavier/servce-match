import { Seo } from '../../components/Seo';
import { Reveal } from '../../components/motion/Reveal';

function LegalSection({ title, children }: { title: string; children: string }) {
  return (
    <section className="mt-8">
      <h2 className="text-h2 font-display font-bold text-foreground">{title}</h2>
      <p className="mt-3 text-body text-muted">{children}</p>
    </section>
  );
}

export function TermsPage() {
  return (
    <div className="mx-auto max-w-3xl px-5 py-[clamp(3rem,6vw,5rem)] sm:px-8">
      <Seo title="Termos de utilização" description="Termos de utilização do ServiMatch." canonicalPath="/termos" />
      <Reveal>
        <p className="eyebrow text-signal-500">LEGAL</p>
        <h1 className="mt-3 text-h1 font-display font-bold text-foreground">Termos de utilização</h1>
        <p className="mt-3 text-caption text-muted">Última atualização: janeiro de 2026 (documento de protótipo).</p>
      </Reveal>
      <LegalSection title="1. Objeto">
        O ServiMatch é um marketplace que liga clientes a prestadores de serviços locais em Portugal. A plataforma
        facilita o contacto e o acordo entre as partes; a execução do serviço é sempre um acordo direto entre cliente
        e prestador.
      </LegalSection>
      <LegalSection title="2. Contas e elegibilidade">
        Clientes usam a plataforma gratuitamente. Prestadores precisam de uma subscrição ativa para serem visíveis,
        entrarem no processo de matching e enviarem orçamentos.
      </LegalSection>
      <LegalSection title="3. Pagamentos">
        Não há fluxo de dinheiro entre cliente e prestador dentro da plataforma. O pagamento do serviço é combinado e
        liquidado diretamente entre as partes. A subscrição do prestador é o único pagamento processado pelo
        ServiMatch.
      </LegalSection>
      <LegalSection title="4. Responsabilidade">
        O ServiMatch não é parte no contrato de prestação de serviço entre cliente e prestador e não se
        responsabiliza pela execução, qualidade ou prazo do trabalho combinado fora da plataforma.
      </LegalSection>
    </div>
  );
}

export function PrivacyPage() {
  return (
    <div className="mx-auto max-w-3xl px-5 py-[clamp(3rem,6vw,5rem)] sm:px-8">
      <Seo title="Política de privacidade" description="Como o ServiMatch trata os seus dados pessoais." canonicalPath="/privacidade" />
      <Reveal>
        <p className="eyebrow text-signal-500">LEGAL</p>
        <h1 className="mt-3 text-h1 font-display font-bold text-foreground">Privacidade</h1>
        <p className="mt-3 text-caption text-muted">Última atualização: janeiro de 2026 (documento de protótipo).</p>
      </Reveal>
      <LegalSection title="1. Dados que recolhemos">
        Nome, email, morada aproximada e conteúdo dos pedidos/propostas que publica. Para prestadores, também dados
        de faturação da subscrição, tratados pelo gateway de pagamento — nunca guardamos dados de cartão.
      </LegalSection>
      <LegalSection title="2. Como usamos os dados">
        Para operar o matching, mostrar perfis públicos, processar subscrições e comunicar consigo sobre a sua conta.
        Não vendemos dados pessoais a terceiros.
      </LegalSection>
      <LegalSection title="3. Os seus direitos">
        Pode aceder, corrigir ou pedir a eliminação dos seus dados a qualquer momento através de ola@servimatch.pt,
        nos termos do RGPD.
      </LegalSection>
    </div>
  );
}

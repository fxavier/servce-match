import { Seo } from '../../components/Seo';
import { Reveal } from '../../components/motion/Reveal';

export function AboutPage() {
  return (
    <div className="mx-auto max-w-3xl px-5 py-[clamp(3rem,6vw,5rem)] sm:px-8">
      <Seo
        title="Sobre nós"
        description="O ServiMatch liga clientes a prestadores de serviços locais verificados em Portugal."
        canonicalPath="/sobre"
      />
      <Reveal>
        <p className="eyebrow text-signal-500">SOBRE</p>
        <h1 className="mt-3 text-h1 font-display font-bold text-foreground">Ligamos quem precisa a quem sabe fazer</h1>
        <div className="mt-6 flex flex-col gap-5 text-body-lg text-muted">
          <p>
            O ServiMatch nasceu de um problema comum: encontrar um profissional de confiança para um serviço em casa
            continua a depender de perguntar a vizinhos ou de ligar a três empresas sem resposta. Construímos uma
            plataforma onde o cliente descreve o problema uma vez e recebe orçamentos comparáveis de profissionais
            verificados da sua zona.
          </p>
          <p>
            O modelo é simples e transparente: o cliente nunca paga para usar a plataforma. Os prestadores pagam uma
            subscrição mensal para serem visíveis, entrarem no matching e poderem enviar orçamentos — sem
            comissões escondidas sobre o valor do serviço.
          </p>
          <p>
            Somos uma equipa pequena, focada em Portugal, a construir com cuidado cada detalhe: da verificação de
            perfis à forma como apresentamos um preço.
          </p>
        </div>
      </Reveal>
    </div>
  );
}

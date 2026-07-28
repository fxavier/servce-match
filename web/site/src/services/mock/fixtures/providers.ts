import type { ProviderProfile, ProviderZone } from '../../domainTypes';
import { CATEGORIES } from './categories';
import { REGIONS, regionByCode } from '../../../constants/regions';

function daysAgoIso(days: number): string {
  return new Date(Date.now() - days * 24 * 60 * 60 * 1000).toISOString();
}

function categoryNamesOf(ids: string[]): string[] {
  return ids
    .map((id) => CATEGORIES.find((category) => category.id === id)?.name)
    .filter((name): name is string => Boolean(name));
}

function zonesOf(codes: string[]): ProviderZone[] {
  return codes.map((code) => ({ regionCode: code, label: regionByCode(code).label }));
}

function distributionFor(ratingAvg: number, ratingCount: number): ProviderProfile['ratingDistribution'] {
  // Distribuição plausível centrada em ratingAvg — não uniforme, sem inventar precisão que não existe.
  const weights = ratingAvg >= 4.6 ? [0.72, 0.18, 0.06, 0.03, 0.01] : ratingAvg >= 4.2 ? [0.55, 0.28, 0.1, 0.05, 0.02] : [0.35, 0.32, 0.18, 0.1, 0.05];
  const [a, b, c, d, e] = weights.map((w) => Math.max(0, Math.round(w * ratingCount)));
  return { 5: a, 4: b, 3: c, 2: d, 1: e };
}

interface ProviderSeed {
  id: string;
  displayName: string;
  companyName: string;
  headline: string;
  bio: string;
  categoryIds: string[];
  regionCodes: string[];
  ratingAvg: number;
  ratingCount: number;
  verified: boolean;
  premiumBadge: boolean;
  memberSinceDays: number;
}

const SEEDS: ProviderSeed[] = [
  { id: 'p-0001', displayName: 'Canalizações Silva & Filhos', companyName: 'Canalizações Silva & Filhos, Lda.', headline: 'Fugas, desentupimentos e instalações — resposta em 2h', bio: 'Trinta anos a resolver problemas de água em Lisboa. Equipa própria, sem subcontratação, orçamento sempre por escrito antes de começar.', categoryIds: ['e659b54f-0c22-4374-ad36-52a1ab62cf74', '92384081-b132-46e0-a12e-9372699e3e45', 'a58ed5fb-33c2-4f8d-ab24-a3b52f9fbcbb'], regionCodes: ['PT-LIS', 'PT-OEI'], ratingAvg: 4.9, ratingCount: 214, verified: true, premiumBadge: true, memberSinceDays: 1200 },
  { id: 'p-0002', displayName: 'EletroPonto Instalações', companyName: 'EletroPonto Unipessoal, Lda.', headline: 'Certificação elétrica e quadros — trabalho limpo, prazos cumpridos', bio: 'Eletricista certificado com foco em segurança: nunca saímos de um serviço sem testar tudo três vezes.', categoryIds: ['4f77ae30-b3e5-4423-89aa-8ba68ce193ea', '873f073a-24e3-47cc-830f-fbe337385efc', '83fe9698-e0f2-4f41-a60f-4b437f61d1da'], regionCodes: ['PT-LIS'], ratingAvg: 4.7, ratingCount: 132, verified: true, premiumBadge: false, memberSinceDays: 860 },
  { id: 'p-0003', displayName: 'Jardins da Serra', companyName: 'Jardins da Serra, Lda.', headline: 'Manutenção de jardins e poda técnica em Sintra e Cascais', bio: 'Paisagismo e manutenção com plano anual — cuidamos do seu jardim como se fosse nosso.', categoryIds: ['b0d59340-a272-4745-9b79-5833108ea5c0', '7315ec2d-51f1-4eab-90f3-1a4eb2503c7f', '71ca3761-1257-4187-92cb-a0e1869411f8'], regionCodes: ['PT-SNT', 'PT-CSC'], ratingAvg: 4.8, ratingCount: 97, verified: true, premiumBadge: false, memberSinceDays: 640 },
  { id: 'p-0004', displayName: 'Pinturas Bettencourt', companyName: 'Bettencourt Pinturas Unipessoal, Lda.', headline: 'Pintura de interiores com acabamento premium', bio: 'Especialistas em interiores — preparação de superfície cuidada, tintas de baixo COV.', categoryIds: ['32396855-58ee-4099-affe-b9ba85b13fb8', '8611e7be-f390-4a57-b0bd-277d8b9673fb'], regionCodes: ['PT-LIS', 'PT-ALM'], ratingAvg: 4.6, ratingCount: 88, verified: true, premiumBadge: false, memberSinceDays: 500 },
  { id: 'p-0005', displayName: 'Marcenaria Nortenha', companyName: 'Marcenaria Nortenha, Lda.', headline: 'Mobiliário à medida — do projeto à instalação', bio: 'Carpintaria tradicional com desenho 3D antes de cortar madeira. Sem surpresas no final.', categoryIds: ['a9c4c6b7-c08f-46a8-b617-d04416c01033', '2e22d38e-9b87-47a4-87ec-c3cf009c3758'], regionCodes: ['PT-PRT', 'PT-VNG'], ratingAvg: 5.0, ratingCount: 61, verified: true, premiumBadge: true, memberSinceDays: 420 },
  { id: 'p-0006', displayName: 'ClimaTejo AVAC', companyName: 'ClimaTejo Climatização, Lda.', headline: 'Ar condicionado — venda, instalação e manutenção', bio: 'Parceiros de marcas certificadas (Daikin, Mitsubishi). Contratos de manutenção anual disponíveis.', categoryIds: ['82133679-da70-41ec-8c00-9220c37d88fa', '600d8b37-abc2-4d28-8e4c-23aecb441b9c', '72c591ec-abe1-4dc1-890c-a94b692ced98'], regionCodes: ['PT-LIS', 'PT-LOU'], ratingAvg: 4.5, ratingCount: 176, verified: true, premiumBadge: false, memberSinceDays: 980 },
  { id: 'p-0007', displayName: 'MudaJá Transportes', companyName: 'MudaJá Transportes, Lda.', headline: 'Mudanças residenciais e de escritório sem stress', bio: 'Equipa de 4 pessoas, camião próprio, seguro de transporte incluído em todos os orçamentos.', categoryIds: ['cc05b494-2f4d-4e42-a48e-d3ce50ee07df', '9f5e205c-81a8-4013-8471-8b8815b98bff', '87cac710-ff8f-4836-9193-0bc2407a0509'], regionCodes: ['PT-LIS', 'PT-OEI', 'PT-ALM'], ratingAvg: 4.4, ratingCount: 143, verified: true, premiumBadge: false, memberSinceDays: 750 },
  { id: 'p-0008', displayName: 'Remodela Cozinhas & Cia', companyName: 'Remodela Cozinhas & Cia, Lda.', headline: 'Remodelação de cozinhas chave-na-mão', bio: 'Projeto, obra e acabamentos — uma única equipa responsável do início ao fim.', categoryIds: ['b3a01c3d-e72b-494e-a945-f5651817bdd0', '4bb1a3e8-dfb8-4230-97d2-856e20cabee9'], regionCodes: ['PT-LIS'], ratingAvg: 4.7, ratingCount: 54, verified: true, premiumBadge: true, memberSinceDays: 310 },
  { id: 'p-0009', displayName: 'Banho & Obra Remodelações', companyName: 'Banho & Obra, Lda.', headline: 'Casas de banho remodeladas em 5 a 8 dias', bio: 'Impermeabilização, canalização e acabamentos coordenados — sem andar a contratar três empresas diferentes.', categoryIds: ['b3a01c3d-e72b-494e-a945-f5651817bdd0', '4fbdd2f4-dd6f-4e23-8ac6-6f367cd480b4'], regionCodes: ['PT-PRT', 'PT-MTS'], ratingAvg: 4.3, ratingCount: 39, verified: false, premiumBadge: false, memberSinceDays: 190 },
  { id: 'p-0010', displayName: 'Serralharia Atlântico', companyName: 'Serralharia Atlântico, Lda.', headline: 'Portões automáticos e serralharia em geral', bio: 'Fabrico e instalação de gradeamentos, portões e automatismos com garantia de 2 anos.', categoryIds: ['62f178a6-4c08-4a6a-aec7-9823c18e167e', 'ba823ff7-a8aa-4375-ae0e-676ef889df77'], regionCodes: ['PT-BRG'], ratingAvg: 4.6, ratingCount: 72, verified: true, premiumBadge: false, memberSinceDays: 560 },
  { id: 'p-0011', displayName: 'Limpa Já Serviços', companyName: 'Limpa Já Serviços de Limpeza, Lda.', headline: 'Limpeza doméstica regular ou pontual', bio: 'Equipa fixa por cliente — a mesma pessoa de confiança em cada visita, produtos ecológicos disponíveis.', categoryIds: ['6bf0759d-d189-438b-b9f5-889d679ffb3b', '81e62e26-978e-4e7c-9aae-9185cf6ea9f1'], regionCodes: ['PT-LIS', 'PT-OEI', 'PT-CSC'], ratingAvg: 4.5, ratingCount: 265, verified: true, premiumBadge: true, memberSinceDays: 1100 },
  { id: 'p-0012', displayName: 'Limpezas Pós-Obra Coimbra', companyName: 'Limpezas Coimbra Norte, Lda.', headline: 'Limpeza pós-obra e de escritórios', bio: 'Especialistas em remover pó e resíduos de obra — deixamos o espaço pronto a habitar.', categoryIds: ['6bf0759d-d189-438b-b9f5-889d679ffb3b', 'bb07e1be-6e9c-4569-958f-8c857b752bee', '73e865dd-5278-418a-aae8-e8d5172eb153'], regionCodes: ['PT-CBR'], ratingAvg: 4.2, ratingCount: 45, verified: false, premiumBadge: false, memberSinceDays: 220 },
  { id: 'p-0013', displayName: 'Iluminação Faro Sul', companyName: 'Faro Sul Elétrica, Lda.', headline: 'Iluminação decorativa e técnica', bio: 'Do LED técnico ao candeeiro de design — projeto de iluminação incluído nos orçamentos maiores.', categoryIds: ['4f77ae30-b3e5-4423-89aa-8ba68ce193ea', 'd39c680f-f090-442b-b986-67a34a5f50a1'], regionCodes: ['PT-FAR'], ratingAvg: 4.8, ratingCount: 58, verified: true, premiumBadge: false, memberSinceDays: 400 },
  { id: 'p-0014', displayName: 'Poda & Verde', companyName: 'Poda & Verde, Lda.', headline: 'Poda de árvores certificada, com seguro', bio: 'Trabalho em altura com todos os certificados de segurança em dia — árvores de grande porte é a nossa especialidade.', categoryIds: ['b0d59340-a272-4745-9b79-5833108ea5c0', '71ca3761-1257-4187-92cb-a0e1869411f8'], regionCodes: ['PT-BRG', 'PT-PRT'], ratingAvg: 4.9, ratingCount: 41, verified: true, premiumBadge: false, memberSinceDays: 300 },
  { id: 'p-0015', displayName: 'Pintura Exterior Douro', companyName: 'Douro Pinturas Exteriores, Lda.', headline: 'Fachadas e pintura exterior com andaime incluído', bio: 'Tratamento anti-humidade antes de pintar — a maior parte dos problemas de fachada não são só estéticos.', categoryIds: ['32396855-58ee-4099-affe-b9ba85b13fb8', '55adc9f2-f2fe-460f-9a1b-5a1f708ac84d'], regionCodes: ['PT-VNG', 'PT-PRT'], ratingAvg: 4.4, ratingCount: 63, verified: true, premiumBadge: false, memberSinceDays: 480 },
  { id: 'p-0016', displayName: 'Quadros & Cia Elétrica', companyName: 'Quadros & Cia, Lda.', headline: 'Certificação e atualização de quadros elétricos', bio: 'Regularização de instalações antigas para norma atual — inclui relatório técnico.', categoryIds: ['4f77ae30-b3e5-4423-89aa-8ba68ce193ea', '83fe9698-e0f2-4f41-a60f-4b437f61d1da'], regionCodes: ['PT-LIS', 'PT-LOU'], ratingAvg: 4.1, ratingCount: 28, verified: false, premiumBadge: false, memberSinceDays: 140 },
  { id: 'p-0017', displayName: 'Mudanças Express Norte', companyName: 'Mudanças Express Norte, Lda.', headline: 'Mudanças residenciais rápidas no Porto', bio: 'Orçamento em 24h, execução em janela de 3h — ideal para quem tem prazos apertados.', categoryIds: ['cc05b494-2f4d-4e42-a48e-d3ce50ee07df', '9f5e205c-81a8-4013-8471-8b8815b98bff'], regionCodes: ['PT-PRT', 'PT-MTS', 'PT-VNG'], ratingAvg: 4.3, ratingCount: 91, verified: true, premiumBadge: false, memberSinceDays: 610 },
  { id: 'p-0018', displayName: 'Carpintaria do Vale', companyName: 'Carpintaria do Vale, Lda.', headline: 'Roupeiros e mobiliário embutido à medida', bio: 'Projeto gratuito com visualização 3D antes de fechar orçamento.', categoryIds: ['a9c4c6b7-c08f-46a8-b617-d04416c01033', '2e22d38e-9b87-47a4-87ec-c3cf009c3758'], regionCodes: ['PT-CBR', 'PT-LIS'], ratingAvg: 4.6, ratingCount: 34, verified: false, premiumBadge: false, memberSinceDays: 260 },
  { id: 'p-0019', displayName: 'ArFresco Climatização', companyName: 'ArFresco Lda.', headline: 'AVAC residencial e comercial', bio: 'Manutenção preventiva com relatório fotográfico após cada visita.', categoryIds: ['82133679-da70-41ec-8c00-9220c37d88fa', '72c591ec-abe1-4dc1-890c-a94b692ced98'], regionCodes: ['PT-FAR', 'PT-CSC'], ratingAvg: 3.9, ratingCount: 22, verified: false, premiumBadge: false, memberSinceDays: 95 },
  { id: 'p-0020', displayName: 'Desentupimentos 24h Lisboa', companyName: 'D24 Serviços Urgentes, Lda.', headline: 'Desentupimentos e fugas — resposta 24 horas', bio: 'Equipa de prevenção sempre de prontidão. Diagnóstico com câmara antes de qualquer obra.', categoryIds: ['e659b54f-0c22-4374-ad36-52a1ab62cf74', '92384081-b132-46e0-a12e-9372699e3e45'], regionCodes: ['PT-LIS', 'PT-ALM'], ratingAvg: 4.7, ratingCount: 188, verified: true, premiumBadge: true, memberSinceDays: 900 },
  { id: 'p-0021', displayName: 'Sanitários Braga', companyName: 'Sanitários Braga Instalações, Lda.', headline: 'Instalação de sanitários e canalização de casas de banho', bio: 'Trabalho coordenado com o azulejador para não parar a obra a meio.', categoryIds: ['e659b54f-0c22-4374-ad36-52a1ab62cf74', '466aa059-72a8-4c63-896f-744f33f89b18'], regionCodes: ['PT-BRG'], ratingAvg: 4.0, ratingCount: 19, verified: false, premiumBadge: false, memberSinceDays: 75 },
  { id: 'p-0022', displayName: 'Escritórios Limpos', companyName: 'Escritórios Limpos Lda.', headline: 'Limpeza de escritórios com contrato mensal', bio: 'Serviço fora de horas para não interromper a operação do cliente.', categoryIds: ['6bf0759d-d189-438b-b9f5-889d679ffb3b', '73e865dd-5278-418a-aae8-e8d5172eb153'], regionCodes: ['PT-LIS', 'PT-OEI'], ratingAvg: 4.6, ratingCount: 112, verified: true, premiumBadge: false, memberSinceDays: 700 },
  { id: 'p-0023', displayName: 'Obras & Remodelações Sul', companyName: 'Obras & Remodelações Sul, Lda.', headline: 'Remodelações completas de apartamentos', bio: 'Gestão de obra chave-na-mão, com acompanhamento semanal ao cliente.', categoryIds: ['b3a01c3d-e72b-494e-a945-f5651817bdd0', '4bb1a3e8-dfb8-4230-97d2-856e20cabee9', '4fbdd2f4-dd6f-4e23-8ac6-6f367cd480b4'], regionCodes: ['PT-FAR'], ratingAvg: 4.5, ratingCount: 47, verified: true, premiumBadge: false, memberSinceDays: 380 },
  { id: 'p-0024', displayName: 'Portões Norte Automatismos', companyName: 'Portões Norte, Lda.', headline: 'Automatização de portões existentes', bio: 'Instalamos motores em portões já existentes — não é preciso trocar o portão todo.', categoryIds: ['62f178a6-4c08-4a6a-aec7-9823c18e167e', 'ba823ff7-a8aa-4375-ae0e-676ef889df77'], regionCodes: ['PT-VNG', 'PT-MTS'], ratingAvg: 4.2, ratingCount: 33, verified: true, premiumBadge: false, memberSinceDays: 230 },
];

function toProfile(seed: ProviderSeed): ProviderProfile {
  const primaryRegion = REGIONS.find((r) => r.code === seed.regionCodes[0]) ?? REGIONS[0];
  return {
    id: seed.id,
    displayName: seed.displayName,
    companyName: seed.companyName,
    headline: seed.headline,
    bio: seed.bio,
    ratingAvg: seed.ratingAvg,
    ratingCount: seed.ratingCount,
    verified: seed.verified,
    premiumBadge: seed.premiumBadge,
    avatarUrl: null,
    categoryNames: categoryNamesOf(seed.categoryIds),
    zones: zonesOf(seed.regionCodes),
    location: primaryRegion.location,
    ratingDistribution: distributionFor(seed.ratingAvg, seed.ratingCount),
    portfolioImageUrls: [],
    memberSince: daysAgoIso(seed.memberSinceDays),
  };
}

export const PROVIDERS: ProviderProfile[] = SEEDS.map(toProfile);

export const PROVIDER_CATEGORY_IDS: Record<string, string[]> = Object.fromEntries(
  SEEDS.map((seed) => [seed.id, seed.categoryIds]),
);

export function providerById(id: string): ProviderProfile | undefined {
  return PROVIDERS.find((provider) => provider.id === id);
}

import type { ReviewWithAuthor } from '../../domainTypes';
import { PROVIDERS } from './providers';

function daysAgoIso(days: number): string {
  return new Date(Date.now() - days * 24 * 60 * 60 * 1000).toISOString();
}

const AUTHOR_NAMES = [
  'Mariana Costa',
  'João Pereira',
  'Beatriz Santos',
  'Ricardo Almeida',
  'Inês Ferreira',
  'Tiago Rodrigues',
  'Carla Sousa',
  'Pedro Martins',
  'Sofia Carvalho',
  'André Gonçalves',
  'Cátia Lopes',
  'Nuno Ribeiro',
  'Patrícia Nunes',
  'Miguel Fernandes',
  'Ana Correia',
  'Rui Teixeira',
  'Filipa Marques',
  'Bruno Cardoso',
  'Teresa Baptista',
  'Hugo Vieira',
];

const COMMENTS_BY_RATING: Record<number, string[]> = {
  5: [
    'Excelente serviço, pontual e muito profissional. Recomendo sem qualquer dúvida.',
    'Trabalho impecável, exatamente como combinado. Voltarei a contratar.',
    'Rápidos, limpos e o preço final foi igual ao orçamento — sem surpresas.',
  ],
  4: [
    'Muito bom trabalho, só demorou um pouco mais do que o combinado.',
    'Ficou tudo bem feito, comunicação podia ter sido mais rápida.',
    'Bom profissional, recomendo com pequenas ressalvas de horário.',
  ],
  3: [
    'Serviço cumprido mas sem grande atenção ao detalhe no acabamento.',
    'Preço justo, mas atrasou-se duas horas sem avisar.',
  ],
};

interface ReviewSeed {
  providerId: string;
  rating: number;
  daysAgo: number;
  response?: string;
}

const SEEDS: ReviewSeed[] = [
  { providerId: 'p-0001', rating: 5, daysAgo: 3, response: 'Obrigado pela confiança! Ficamos ao dispor para o que precisar.' },
  { providerId: 'p-0001', rating: 5, daysAgo: 12 },
  { providerId: 'p-0001', rating: 4, daysAgo: 20 },
  { providerId: 'p-0002', rating: 5, daysAgo: 5, response: 'Obrigado, Ricardo! Foi um prazer.' },
  { providerId: 'p-0002', rating: 4, daysAgo: 18 },
  { providerId: 'p-0003', rating: 5, daysAgo: 7 },
  { providerId: 'p-0003', rating: 5, daysAgo: 25 },
  { providerId: 'p-0004', rating: 4, daysAgo: 9 },
  { providerId: 'p-0005', rating: 5, daysAgo: 2, response: 'Obrigado! Qualquer coisa estamos cá.' },
  { providerId: 'p-0006', rating: 4, daysAgo: 15 },
  { providerId: 'p-0006', rating: 3, daysAgo: 40 },
  { providerId: 'p-0007', rating: 4, daysAgo: 6 },
  { providerId: 'p-0008', rating: 5, daysAgo: 11 },
  { providerId: 'p-0009', rating: 3, daysAgo: 22 },
  { providerId: 'p-0010', rating: 4, daysAgo: 14 },
  { providerId: 'p-0011', rating: 5, daysAgo: 1, response: 'Obrigada pela confiança de longa data!' },
  { providerId: 'p-0011', rating: 5, daysAgo: 33 },
  { providerId: 'p-0013', rating: 5, daysAgo: 8 },
  { providerId: 'p-0014', rating: 5, daysAgo: 4 },
  { providerId: 'p-0020', rating: 5, daysAgo: 2, response: 'Sempre disponíveis. Obrigado pela avaliação!' },
];

export const REVIEWS: ReviewWithAuthor[] = SEEDS.map((seed, index) => {
  const provider = PROVIDERS.find((p) => p.id === seed.providerId);
  const comments = COMMENTS_BY_RATING[seed.rating] ?? COMMENTS_BY_RATING[4];
  return {
    id: `rv-${String(index + 1).padStart(4, '0')}`,
    authorName: AUTHOR_NAMES[index % AUTHOR_NAMES.length],
    authorAvatarSeed: AUTHOR_NAMES[index % AUTHOR_NAMES.length],
    targetId: provider?.id ?? seed.providerId,
    rating: seed.rating,
    comment: comments[index % comments.length],
    createdAt: daysAgoIso(seed.daysAgo),
    providerResponse: seed.response,
  } satisfies ReviewWithAuthor;
});

export function reviewsForProvider(providerId: string): ReviewWithAuthor[] {
  return REVIEWS.filter((review) => review.targetId === providerId);
}

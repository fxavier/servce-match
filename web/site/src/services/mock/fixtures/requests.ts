import type { Address, RequestStatus, ServiceRequest, UrgencyLevel } from '../../types';
import { categoryById } from './categories';
import { regionByCode } from '../../../constants/regions';

function hoursAgoIso(hours: number): string {
  return new Date(Date.now() - hours * 60 * 60 * 1000).toISOString();
}

function addressOf(line1: string, postalCode: string, regionCode: string): Address {
  const region = regionByCode(regionCode);
  return {
    line1,
    postalCode,
    city: region.label,
    regionCode: region.code,
    country: 'PT',
    location: region.location,
  };
}

interface RequestSeed {
  id: string;
  customerId: string;
  categoryId: string;
  title: string;
  description: string;
  address: Address;
  urgency: UrgencyLevel;
  availability: string;
  status: RequestStatus;
  proposalCount: number;
  createdHoursAgo: number;
  publishedHoursAgo: number | null;
}

const SEEDS: RequestSeed[] = [
  { id: 'r-0001', customerId: 'c-0001', categoryId: 'a58ed5fb-33c2-4f8d-ab24-a3b52f9fbcbb', title: 'Fuga de água por baixo do lava-loiça', description: 'Notei uma poça pequena todas as manhãs debaixo do lava-loiça da cozinha. Pode ser a torneira ou o sifão — preciso de alguém que venha ver esta semana.', address: addressOf('Rua das Amoreiras 45, 3º Dto', '1250-021', 'PT-LIS'), urgency: 'HIGH', availability: 'Dias úteis depois das 18h, fins de semana todo o dia', status: 'IN_NEGOTIATION', proposalCount: 4, createdHoursAgo: 30, publishedHoursAgo: 29 },
  { id: 'r-0002', customerId: 'c-0001', categoryId: '8611e7be-f390-4a57-b0bd-277d8b9673fb', title: 'Pintar T2 completo em Alvalade', description: 'Apartamento T2 (68 m²) precisa de pintura geral — sala, dois quartos, corredor e casa de banho. Cor branca, tinta já escolhida.', address: addressOf('Av. Rio de Janeiro 12', '1700-330', 'PT-LIS'), urgency: 'NORMAL', availability: 'Qualquer altura em outubro', status: 'PUBLISHED', proposalCount: 3, createdHoursAgo: 8, publishedHoursAgo: 7 },
  { id: 'r-0003', customerId: 'c-0002', categoryId: '600d8b37-abc2-4d28-8e4c-23aecb441b9c', title: 'Instalar ar condicionado no quarto', description: 'Quarto de casal (18 m²) sem ar condicionado. Já tenho o aparelho comprado (Daikin 12000 BTU), preciso só da instalação.', address: addressOf('Rua do Loureiro 8', '4050-115', 'PT-PRT'), urgency: 'NORMAL', availability: 'Fins de semana', status: 'CONFIRMED', proposalCount: 5, createdHoursAgo: 120, publishedHoursAgo: 118 },
  { id: 'r-0004', customerId: 'c-0002', categoryId: 'e659b54f-0c22-4374-ad36-52a1ab62cf74', title: 'Substituir torneira da banheira', description: 'A torneira da banheira não fecha bem e está a gastar água. Peço substituição por uma nova (posso comprar eu ou o prestador trazer).', address: addressOf('Rua de Cedofeita 210', '4050-179', 'PT-PRT'), urgency: 'LOW', availability: 'Sem pressa, qualquer dia', status: 'DRAFT', proposalCount: 0, createdHoursAgo: 2, publishedHoursAgo: null },
  { id: 'r-0005', customerId: 'c-0003', categoryId: '4bb1a3e8-dfb8-4230-97d2-856e20cabee9', title: 'Remodelar cozinha pequena (9 m²)', description: 'Cozinha antiga precisa de remodelação completa: móveis, canalização e eletricidade. Já tenho o projeto do arquiteto.', address: addressOf('Rua Nova de São Mamede 3', '2710-562', 'PT-SNT'), urgency: 'NORMAL', availability: 'A partir de novembro', status: 'PUBLISHED', proposalCount: 2, createdHoursAgo: 5, publishedHoursAgo: 4 },
  { id: 'r-0006', customerId: 'c-0003', categoryId: 'b0d59340-a272-4745-9b79-5833108ea5c0', title: 'Cortar relva e aparar sebe do quintal', description: 'Quintal de moradia (200 m²) com relva alta e sebe desalinhada. Serviço pontual, não é contrato.', address: addressOf('Rua das Camélias 22', '2765-193', 'PT-CSC'), urgency: 'LOW', availability: 'Qualquer dia útil de manhã', status: 'COMPLETED', proposalCount: 3, createdHoursAgo: 400, publishedHoursAgo: 398 },
  { id: 'r-0007', customerId: 'c-0004', categoryId: '92384081-b132-46e0-a12e-9372699e3e45', title: 'Desentupir cano da cozinha urgente', description: 'A água não escoa de todo no lava-loiça, já tentei desentupidor manual e não resultou. Preciso de alguém hoje ou amanhã.', address: addressOf('Rua de Santa Catarina 500', '4000-447', 'PT-PRT'), urgency: 'URGENT', availability: 'Hoje ou amanhã, qualquer hora', status: 'PUBLISHED', proposalCount: 6, createdHoursAgo: 3, publishedHoursAgo: 3 },
  { id: 'r-0008', customerId: 'c-0004', categoryId: '873f073a-24e3-47cc-830f-fbe337385efc', title: 'Instalar tomadas extra no escritório em casa', description: 'Preciso de mais 4 tomadas numa divisão que uso como escritório. Já tem quadro elétrico recente.', address: addressOf('Rua Central 88', '4700-320', 'PT-BRG'), urgency: 'NORMAL', availability: 'Tardes de semana', status: 'IN_PROGRESS', proposalCount: 3, createdHoursAgo: 200, publishedHoursAgo: 198 },
  { id: 'r-0009', customerId: 'c-0005', categoryId: '55adc9f2-f2fe-460f-9a1b-5a1f708ac84d', title: 'Pintar fachada de moradia (2 pisos)', description: 'Fachada com sinais de humidade e tinta a descascar. Precisa de tratamento antes de pintar.', address: addressOf('Rua do Farol 15', '8005-400', 'PT-FAR'), urgency: 'NORMAL', availability: 'Antes do inverno, urgência moderada', status: 'PUBLISHED', proposalCount: 2, createdHoursAgo: 12, publishedHoursAgo: 11 },
  { id: 'r-0010', customerId: 'c-0005', categoryId: '81e62e26-978e-4e7c-9aae-9185cf6ea9f1', title: 'Limpeza profunda antes de mudança', description: 'Apartamento T3 vazio, preciso de limpeza profunda antes de entregar as chaves ao senhorio.', address: addressOf('Rua da Prata 77', '1100-052', 'PT-LIS'), urgency: 'HIGH', availability: 'Esta semana, qualquer dia', status: 'CANCELLED', proposalCount: 1, createdHoursAgo: 90, publishedHoursAgo: 89 },
  { id: 'r-0011', customerId: 'c-0001', categoryId: '9f5e205c-81a8-4013-8471-8b8815b98bff', title: 'Mudança de T2 para T3 dentro da mesma zona', description: 'Mudança pequena, sem elevador no prédio de saída (2º andar), com elevador no de chegada.', address: addressOf('Rua Actor Taborda 9', '1900-284', 'PT-LIS'), urgency: 'NORMAL', availability: 'Fim de mês', status: 'PUBLISHED', proposalCount: 4, createdHoursAgo: 20, publishedHoursAgo: 19 },
  { id: 'r-0012', customerId: 'c-0002', categoryId: '2e22d38e-9b87-47a4-87ec-c3cf009c3758', title: 'Roupeiro à medida para quarto de casal', description: 'Parede de 3,2 m disponível para roupeiro embutido até ao teto. Gostava de portas de correr.', address: addressOf('Rua de Camões 60', '4000-142', 'PT-PRT'), urgency: 'LOW', availability: 'Sem pressa', status: 'IN_NEGOTIATION', proposalCount: 2, createdHoursAgo: 60, publishedHoursAgo: 58 },
  { id: 'r-0013', customerId: 'c-0003', categoryId: 'ba823ff7-a8aa-4375-ae0e-676ef889df77', title: 'Automatizar portão existente da garagem', description: 'Portão basculante manual, gostava de motorizar sem trocar o portão.', address: addressOf('Rua da Igreja 4', '4400-025', 'PT-VNG'), urgency: 'NORMAL', availability: 'Próximas duas semanas', status: 'PUBLISHED', proposalCount: 1, createdHoursAgo: 15, publishedHoursAgo: 14 },
  { id: 'r-0014', customerId: 'c-0004', categoryId: '600d8b37-abc2-4d28-8e4c-23aecb441b9c', title: 'Manutenção anual de dois ar condicionados', description: 'Dois splits instalados há 3 anos, nunca tiveram manutenção. Limpeza de filtros e verificação de gás.', address: addressOf('Rua Fernão de Magalhães 300', '4300-190', 'PT-PRT'), urgency: 'LOW', availability: 'Qualquer dia útil', status: 'COMPLETED', proposalCount: 3, createdHoursAgo: 700, publishedHoursAgo: 698 },
  { id: 'r-0015', customerId: 'c-0005', categoryId: '73e865dd-5278-418a-aae8-e8d5172eb153', title: 'Limpeza semanal de escritório pequeno', description: 'Escritório com 3 salas, cerca de 80 m². Procuro contrato de limpeza semanal, fora de horas.', address: addressOf('Av. da Boavista 1200', '4100-114', 'PT-PRT'), urgency: 'NORMAL', availability: 'Preferencialmente sextas à noite', status: 'PUBLISHED', proposalCount: 2, createdHoursAgo: 40, publishedHoursAgo: 39 },
  { id: 'r-0016', customerId: 'c-0001', categoryId: 'd39c680f-f090-442b-b986-67a34a5f50a1', title: 'Trocar iluminação da sala para LED', description: 'Sala com 3 pontos de luz antigos, quero mudar tudo para LED com regulação de intensidade.', address: addressOf('Rua Alexandre Herculano 30', '1250-008', 'PT-LIS'), urgency: 'LOW', availability: 'Sem pressa', status: 'DRAFT', proposalCount: 0, createdHoursAgo: 1, publishedHoursAgo: null },
  { id: 'r-0017', customerId: 'c-0002', categoryId: 'bb07e1be-6e9c-4569-958f-8c857b752bee', title: 'Limpeza pós-obra em apartamento remodelado', description: 'Acabou de terminar obra de remodelação, ficou muito pó e resíduos de tinta. Preciso de limpeza pesada antes de mudar mobília.', address: addressOf('Rua do Almada 150', '4050-035', 'PT-PRT'), urgency: 'HIGH', availability: 'Esta semana', status: 'PUBLISHED', proposalCount: 3, createdHoursAgo: 6, publishedHoursAgo: 5 },
  { id: 'r-0018', customerId: 'c-0003', categoryId: '4fbdd2f4-dd6f-4e23-8ac6-6f367cd480b4', title: 'Impermeabilizar e remodelar casa de banho pequena', description: 'Casa de banho de serviço (3 m²) com infiltração para o quarto ao lado. Precisa de obra completa.', address: addressOf('Rua Direita 18', '2710-501', 'PT-SNT'), urgency: 'HIGH', availability: 'O quanto antes', status: 'IN_PROGRESS', proposalCount: 2, createdHoursAgo: 168, publishedHoursAgo: 166 },
];

function toRequest(seed: RequestSeed): ServiceRequest {
  const category = categoryById(seed.categoryId);
  return {
    id: seed.id,
    customerId: seed.customerId,
    category,
    title: seed.title,
    description: seed.description,
    address: seed.address,
    urgency: seed.urgency,
    availability: seed.availability,
    status: seed.status,
    images: [],
    proposalCount: seed.proposalCount,
    createdAt: hoursAgoIso(seed.createdHoursAgo),
    publishedAt: seed.publishedHoursAgo === null ? null : hoursAgoIso(seed.publishedHoursAgo),
  };
}

export const REQUESTS: ServiceRequest[] = SEEDS.map(toRequest);

export function requestById(id: string): ServiceRequest | undefined {
  return REQUESTS.find((request) => request.id === id);
}

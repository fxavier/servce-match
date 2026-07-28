import type { Category } from '../../types';

/**
 * 31 categorias reais (10 de topo + 21 subcategorias), confirmadas contra a
 * base local a correr (`docker exec servimatch-postgres psql … SELECT * FROM
 * category`) — mesmos `id`, `slug`, `name` do seed do backend (prioridade
 * #5 do briefing: não inventar nomes nem preços).
 */
export const CATEGORIES: Category[] = [
  { id: 'e659b54f-0c22-4374-ad36-52a1ab62cf74', parentId: null, slug: 'canalizacao', name: 'Canalização', active: true },
  { id: 'a9c4c6b7-c08f-46a8-b617-d04416c01033', parentId: null, slug: 'carpintaria', name: 'Carpintaria e Marcenaria', active: true },
  { id: '82133679-da70-41ec-8c00-9220c37d88fa', parentId: null, slug: 'climatizacao', name: 'Climatização', active: true },
  { id: '4f77ae30-b3e5-4423-89aa-8ba68ce193ea', parentId: null, slug: 'eletricidade', name: 'Eletricidade', active: true },
  { id: 'b0d59340-a272-4745-9b79-5833108ea5c0', parentId: null, slug: 'jardinagem', name: 'Jardinagem', active: true },
  { id: '6bf0759d-d189-438b-b9f5-889d679ffb3b', parentId: null, slug: 'limpezas', name: 'Limpezas', active: true },
  { id: 'cc05b494-2f4d-4e42-a48e-d3ce50ee07df', parentId: null, slug: 'mudancas', name: 'Mudanças', active: true },
  { id: 'b3a01c3d-e72b-494e-a945-f5651817bdd0', parentId: null, slug: 'obras-remodelacoes', name: 'Obras e Remodelações', active: true },
  { id: '32396855-58ee-4099-affe-b9ba85b13fb8', parentId: null, slug: 'pinturas', name: 'Pinturas', active: true },
  { id: '62f178a6-4c08-4a6a-aec7-9823c18e167e', parentId: null, slug: 'serralharia', name: 'Serralharia', active: true },

  { id: '55adc9f2-f2fe-460f-9a1b-5a1f708ac84d', parentId: '32396855-58ee-4099-affe-b9ba85b13fb8', slug: 'pintura-exteriores', name: 'Pintura de Exteriores', active: true },
  { id: '8611e7be-f390-4a57-b0bd-277d8b9673fb', parentId: '32396855-58ee-4099-affe-b9ba85b13fb8', slug: 'pintura-interiores', name: 'Pintura de Interiores', active: true },
  { id: 'd39c680f-f090-442b-b986-67a34a5f50a1', parentId: '4f77ae30-b3e5-4423-89aa-8ba68ce193ea', slug: 'iluminacao', name: 'Iluminação', active: true },
  { id: '873f073a-24e3-47cc-830f-fbe337385efc', parentId: '4f77ae30-b3e5-4423-89aa-8ba68ce193ea', slug: 'instalacoes-electricas', name: 'Instalações Elétricas', active: true },
  { id: '83fe9698-e0f2-4f41-a60f-4b437f61d1da', parentId: '4f77ae30-b3e5-4423-89aa-8ba68ce193ea', slug: 'quadros-electricos', name: 'Quadros Elétricos', active: true },
  { id: 'ba823ff7-a8aa-4375-ae0e-676ef889df77', parentId: '62f178a6-4c08-4a6a-aec7-9823c18e167e', slug: 'portoes-automaticos', name: 'Portões Automáticos', active: true },
  { id: '73e865dd-5278-418a-aae8-e8d5172eb153', parentId: '6bf0759d-d189-438b-b9f5-889d679ffb3b', slug: 'limpeza-escritorios', name: 'Limpeza de Escritórios', active: true },
  { id: '81e62e26-978e-4e7c-9aae-9185cf6ea9f1', parentId: '6bf0759d-d189-438b-b9f5-889d679ffb3b', slug: 'limpeza-domestica', name: 'Limpeza Doméstica', active: true },
  { id: 'bb07e1be-6e9c-4569-958f-8c857b752bee', parentId: '6bf0759d-d189-438b-b9f5-889d679ffb3b', slug: 'limpeza-pos-obra', name: 'Limpeza Pós-Obra', active: true },
  { id: '600d8b37-abc2-4d28-8e4c-23aecb441b9c', parentId: '82133679-da70-41ec-8c00-9220c37d88fa', slug: 'instalacao-ar-condicionado', name: 'Instalação de Ar Condicionado', active: true },
  { id: '72c591ec-abe1-4dc1-890c-a94b692ced98', parentId: '82133679-da70-41ec-8c00-9220c37d88fa', slug: 'manutencao-avac', name: 'Manutenção de AVAC', active: true },
  { id: '2e22d38e-9b87-47a4-87ec-c3cf009c3758', parentId: 'a9c4c6b7-c08f-46a8-b617-d04416c01033', slug: 'mobiliario-medida', name: 'Mobiliário à Medida', active: true },
  { id: '7315ec2d-51f1-4eab-90f3-1a4eb2503c7f', parentId: 'b0d59340-a272-4745-9b79-5833108ea5c0', slug: 'manutencao-jardins', name: 'Manutenção de Jardins', active: true },
  { id: '71ca3761-1257-4187-92cb-a0e1869411f8', parentId: 'b0d59340-a272-4745-9b79-5833108ea5c0', slug: 'poda-arvores', name: 'Poda de Árvores', active: true },
  { id: '4fbdd2f4-dd6f-4e23-8ac6-6f367cd480b4', parentId: 'b3a01c3d-e72b-494e-a945-f5651817bdd0', slug: 'remodelacao-banheiros', name: 'Remodelação de Casas de Banho', active: true },
  { id: '4bb1a3e8-dfb8-4230-97d2-856e20cabee9', parentId: 'b3a01c3d-e72b-494e-a945-f5651817bdd0', slug: 'remodelacao-cozinhas', name: 'Remodelação de Cozinhas', active: true },
  { id: '87cac710-ff8f-4836-9193-0bc2407a0509', parentId: 'cc05b494-2f4d-4e42-a48e-d3ce50ee07df', slug: 'mudancas-escritorio', name: 'Mudanças de Escritório', active: true },
  { id: '9f5e205c-81a8-4013-8471-8b8815b98bff', parentId: 'cc05b494-2f4d-4e42-a48e-d3ce50ee07df', slug: 'mudancas-residenciais', name: 'Mudanças Residenciais', active: true },
  { id: '92384081-b132-46e0-a12e-9372699e3e45', parentId: 'e659b54f-0c22-4374-ad36-52a1ab62cf74', slug: 'desentupimentos', name: 'Desentupimentos', active: true },
  { id: '466aa059-72a8-4c63-896f-744f33f89b18', parentId: 'e659b54f-0c22-4374-ad36-52a1ab62cf74', slug: 'instalacao-sanitarios', name: 'Instalação de Sanitários', active: true },
  { id: 'a58ed5fb-33c2-4f8d-ab24-a3b52f9fbcbb', parentId: 'e659b54f-0c22-4374-ad36-52a1ab62cf74', slug: 'reparacao-fugas', name: 'Reparação de Fugas', active: true },
];

export const TOP_LEVEL_CATEGORIES = CATEGORIES.filter((category) => category.parentId === null);

export function categoryById(id: string): Category | undefined {
  return CATEGORIES.find((category) => category.id === id);
}

export function categoryBySlug(slug: string): Category | undefined {
  return CATEGORIES.find((category) => category.slug === slug);
}

export function subcategoriesOf(parentId: string): Category[] {
  return CATEGORIES.filter((category) => category.parentId === parentId);
}

/** Ícone lucide por slug de categoria de topo — mapeado uma vez, usado em toda a UI de marketing. */
export const CATEGORY_ICON_BY_SLUG: Record<string, string> = {
  canalizacao: 'Droplets',
  carpintaria: 'Hammer',
  climatizacao: 'Fan',
  eletricidade: 'Zap',
  jardinagem: 'Trees',
  limpezas: 'Sparkles',
  mudancas: 'Truck',
  'obras-remodelacoes': 'HardHat',
  pinturas: 'PaintRoller',
  serralharia: 'Wrench',
};

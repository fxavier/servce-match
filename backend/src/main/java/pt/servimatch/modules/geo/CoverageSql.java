package pt.servimatch.modules.geo;

/**
 * Fragmento SQL reutilizável do predicado de cobertura geográfica (ADR-0004):
 * uma linha de {@code provider_service_area} cobre um ponto/região quando
 * está em modo {@code RADIUS} e o ponto cai dentro do raio, ou em modo
 * {@code ADMIN_REGION} e a região coincide. Publicado aqui (não em
 * {@code matching} nem em {@code search}) para que os dois módulos que
 * precisam da mesma condição — {@code matching} (predicado central,
 * ADR-0004 §10.3) e {@code search} (filtro geográfico de
 * {@code GET /v1/search/providers}) — não a dupliquem cada um à sua maneira.
 *
 * <h2>Porque um pré-filtro por raio constante, não só o {@code ST_DWithin} exato</h2>
 * <p>{@code ST_DWithin(a.center, :point, a.radius_m)} sozinho <b>não</b> ativa
 * o índice GiST de {@code idx_provider_service_area_center} quando o raio é
 * uma <b>coluna</b> (varia por prestador) em vez de uma constante da
 * consulta: o reescritor de planeamento do PostGIS só consegue construir a
 * caixa de pesquisa ({@code center && _st_expand(:point, :distância)}) —
 * visível no plano como {@code Index Cond} — quando a distância é conhecida
 * no momento do planeamento. Com uma coluna por linha isso não é possível, e
 * o resultado observado é uma verificação exaustiva de todas as linhas do
 * índice (confirmado empiricamente com {@code EXPLAIN (ANALYZE, BUFFERS)}
 * sobre 20 000 prestadores: aparece só como {@code Filter}, nunca
 * {@code Index Cond}, ~6700 buffers e sem qualquer poda espacial — o "erro
 * clássico e silencioso" descrito na skill {@code postgis-geo-matching}, só
 * que desencadeado pelo raio variável em vez de um {@code ST_Distance} no
 * {@code WHERE}).
 *
 * <p>Correção aplicada: um segundo {@code ST_DWithin} com raio
 * <b>constante</b> ({@link #MAX_RADIUS_METERS}) como pré-filtro — esse sim
 * ativa o índice ({@code Index Cond} confirmado por EXPLAIN, ~2500 buffers,
 * mesmo resultado) — seguido do {@code ST_DWithin} exato com o raio real da
 * linha para a correção final. {@link #MAX_RADIUS_METERS} é, por isso, um
 * limite superior <b>assumido</b> para qualquer {@code radius_m}: um
 * prestador com um raio maior do que este ficaria incorretamente excluído.
 * <b>Pedido ao agente {@code db-migrations}:</b> adicionar
 * {@code CHECK (radius_m <= 100000)} a {@code provider_service_area}
 * tornaria esta suposição garantida pelo esquema em vez de apenas assumida
 * aqui — ver relatório da tarefa.
 *
 * <p>Os dois modos combinam num único {@code OR} sem produzir um plano mau
 * (confirmado por EXPLAIN: {@code BitmapOr} combina livremente o GiST de
 * {@code center} com o índice de {@code region_code}) — não há necessidade
 * de separar em {@code UNION ALL} (skill {@code postgis-geo-matching}: "só
 * com EXPLAIN que o justifique").
 *
 * <p><b>Uso:</b> embutir {@link #CANDIDATE_PROVIDER_IDS_CTE} como uma CTE
 * {@code WITH} antes de juntar com {@code provider_profile}/etc. O
 * qualificador {@code MATERIALIZED} não é opcional: garante que a CTE é
 * avaliada exatamente uma vez (visível no plano como um nó próprio,
 * {@code CTE Scan on candidates}) em vez de ser embutida ("inlined") na
 * consulta principal como um simples predicado correlacionado. Sem ele —
 * testado isoladamente, mantendo o pré-filtro de raio constante — o
 * PostgreSQL pode optar por reavaliar a mesma condição geográfica uma vez
 * por linha externa (confirmado por EXPLAIN com 20 000 prestadores: sem
 * pré-filtro nem {@code MATERIALIZED}, o nó de {@code provider_service_area}
 * aparece com {@code loops=17066} e ~1.5 s; com ambos, {@code loops=1} e
 * ~150 ms). Os dois mecanismos protegem coisas distintas — o pré-filtro dá
 * ao planeador uma estimativa de cardinalidade utilizável; o
 * {@code MATERIALIZED} garante que essa estimativa não é ignorada por uma
 * reavaliação por linha — por isso ambos ficam neste único fragmento
 * partilhado, e {@code MatchingEligibilityTest} verifica os dois sinais em
 * separado por {@code EXPLAIN} (não por tempo de execução, que varia com a
 * máquina): {@code Index Cond} com {@code _st_expand} para o pré-filtro,
 * {@code CTE Scan on candidates} para a materialização.
 *
 * <p>Parâmetros nomeados esperados pelo consumidor (todos com cast SQL
 * explícito no fragmento, para evitar o erro clássico do driver JDBC do
 * PostgreSQL "could not determine data type of parameter" quando o valor
 * ligado é {@code null}): {@code :lat}, {@code :lon} (Double, ambos nulos ou
 * ambos preenchidos — RADIUS), {@code :regionCode} (String, pode ser nulo —
 * ADMIN_REGION), {@code :maxRadiusM} (int constante, usar
 * {@link #MAX_RADIUS_METERS}). Quando nem ponto nem região são fornecidos, o
 * consumidor deve omitir esta CTE inteiramente (não filtrar geograficamente)
 * em vez de a invocar com todos os parâmetros a {@code null} — caso
 * contrário obtém-se um conjunto vazio, não "sem filtro" (confirmado por
 * EXPLAIN: com {@code :lat}/{@code :lon}/{@code :regionCode} todos nulos, os
 * dois ramos do OR anulam-se e a CTE fica sempre vazia).
 */
public final class CoverageSql {

    /** Limite superior assumido para {@code radius_m} — ver javadoc da classe. */
    public static final int MAX_RADIUS_METERS = 100_000;

    /**
     * CTE que resolve, para um ponto e/ou região dados, o conjunto de
     * {@code provider_id} cujo {@code provider_service_area} os cobre.
     * Não inclui subscrição, categoria, aprovação nem visibilidade — isso é
     * responsabilidade do predicado completo (ver {@code MatchingApi}), não
     * deste fragmento puramente geográfico.
     */
    public static final String CANDIDATE_PROVIDER_IDS_CTE = """
            candidates AS MATERIALIZED (
                SELECT provider_id
                FROM provider_service_area
                WHERE (
                        mode = 'RADIUS'
                        AND ST_DWithin(center, ST_SetSRID(ST_MakePoint(:lon::float8, :lat::float8), 4326)::geography, :maxRadiusM::int)
                        AND ST_DWithin(center, ST_SetSRID(ST_MakePoint(:lon::float8, :lat::float8), 4326)::geography, radius_m)
                      )
                   OR (mode = 'ADMIN_REGION' AND region_code = :regionCode::text)
            )""";

    private CoverageSql() {
    }
}

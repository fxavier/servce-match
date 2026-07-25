package pt.servimatch.platform.error;

/**
 * Catálogo central de {@code type} URIs para as respostas de erro RFC 9457
 * (Problem Details). Todos os {@code type} vivem sob
 * {@code https://errors.servimatch.pt/} e são estáveis: uma vez publicado,
 * um {@code type} não muda de significado (evolução aditiva, ver
 * {@code docs/adr/0008}).
 *
 * <p>Módulos de domínio que precisem de um {@code type} adicional pedem-no
 * ao agente {@code backend-platform} (dono de {@code platform/error/**}) em
 * vez de codificarem URIs ad-hoc — mantém o catálogo consistente e
 * documentável.
 */
public final class ProblemType {

    public static final String BASE_URI = "https://errors.servimatch.pt/";

    /** Corpo do pedido inválido: campos em falta, formato incorreto, etc. */
    public static final String VALIDATION = BASE_URI + "validation";

    /** Sem token, token expirado/inválido, ou assinatura não verificável. */
    public static final String UNAUTHENTICATED = BASE_URI + "unauthenticated";

    /** Autenticado, mas sem permissão (role ou ownership) para o recurso. */
    public static final String FORBIDDEN = BASE_URI + "forbidden";

    /** Recurso não encontrado ou não visível para o principal atual. */
    public static final String NOT_FOUND = BASE_URI + "not-found";

    /** Estado do recurso não permite a operação (ex.: transição inválida). */
    public static final String CONFLICT = BASE_URI + "conflict";

    /** {@code Idempotency-Key} reutilizada com um pedido diferente. */
    public static final String IDEMPOTENCY_KEY_CONFLICT = BASE_URI + "idempotency-key-conflict";

    /** Limite de pedidos excedido (Bucket4j). */
    public static final String RATE_LIMITED = BASE_URI + "rate-limited";

    /** Erro não esperado; nunca inclui detalhe interno no corpo. */
    public static final String INTERNAL = BASE_URI + "internal";

    private ProblemType() {
    }
}

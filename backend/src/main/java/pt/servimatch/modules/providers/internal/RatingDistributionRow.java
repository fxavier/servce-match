package pt.servimatch.modules.providers.internal;

/**
 * Contagem de avaliações por número de estrelas — resultado de uma única
 * consulta agregada ({@code count(*) FILTER (WHERE rating = n)}), nunca de
 * cinco consultas separadas. Ver {@code ProviderRepository#ratingDistribution}.
 */
record RatingDistributionRow(int fiveStars, int fourStars, int threeStars, int twoStars, int oneStar) {
}

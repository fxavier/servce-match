-- Torna garantida pelo esquema uma suposição hoje só documentada em código:
-- pt.servimatch.modules.geo.CoverageSql.MAX_RADIUS_METERS = 100_000. Esse
-- pré-filtro ST_DWithin com limite constante é o que ativa o índice GiST de
-- idx_provider_service_area_center (confirmado por EXPLAIN ANALYZE/BUFFERS,
-- ver javadoc da classe); qualquer radius_m acima do limite seria
-- silenciosamente excluído do matching sem este CHECK.
--
-- Tabela ainda sem volume de produção nesta fase do MVP — CHECK validado
-- imediatamente (sem NOT VALID) é suficiente e mais simples.
ALTER TABLE provider_service_area
    ADD CONSTRAINT chk_provider_service_area_radius_max
    CHECK (radius_m IS NULL OR radius_m <= 100000);

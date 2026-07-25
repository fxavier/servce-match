package pt.servimatch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

/**
 * Ponto de arranque do backend ServiMatch.
 *
 * <p>Modular monolith (ADR-0001) sobre Spring Boot 3.5.x / Spring Modulith
 * 1.4.x / Java 21 (ADR-0003). O backend é <b>apenas</b> um OAuth2 Resource
 * Server (ADR-0002/0009): não emite tokens, não faz hash de passwords, não
 * gere sessões de autenticação — isso é responsabilidade do Keycloak.
 *
 * <p>A deteção de módulos de aplicação usa a estratégia
 * {@code explicitly-annotated} (ver {@code application.yml}), porque os
 * módulos de domínio vivem em {@code pt.servimatch.modules.*} e não como
 * subpacotes diretos deste pacote. Cada pacote em {@code modules} está
 * anotado com {@code @ApplicationModule} no respetivo {@code package-info.java}.
 */
@Modulithic(systemName = "ServiMatch")
@SpringBootApplication
public class ServiMatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServiMatchApplication.class, args);
    }
}

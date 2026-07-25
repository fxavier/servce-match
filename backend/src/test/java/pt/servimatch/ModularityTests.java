package pt.servimatch;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Rede de segurança que torna o trabalho paralelo dos agentes de domínio
 * seguro (docs/AGENTES.md): falha o build assim que um módulo importar
 * internals de outro. Nunca desligar nem marcar como ignorado para
 * desbloquear alguém — se um módulo precisa de aceder a outro, o caminho é
 * pela API pública ou por evento de domínio, não pela relaxação deste
 * teste.
 */
class ModularityTests {

    private static final ApplicationModules MODULES = ApplicationModules.of(ServiMatchApplication.class);

    @Test
    void verifiesModularStructure() {
        MODULES.verify();
    }

    @Test
    void printsModuleStructureForDocumentation() {
        System.out.println(MODULES);
    }
}

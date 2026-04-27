package br.ufla.autotarget;

import org.junit.Test;
import static org.junit.Assert.*;

public class JogoTest {

    @Test
    public void testPosicaoCanhaoInvalidaLancaExcecao() {
        Jogo jogo = new Jogo(800, 600);

        try {
            // Tentando adicionar canhão fora dos limites (x = -10)
            jogo.adicionarCanhao(-10, 300);
            fail("Deveria ter lançado JogoException");
        } catch (JogoException e) {
            assertEquals("Posição do canhão fora dos limites da tela!", e.getMessage());
        }
    }

    @Test
    public void testDistanciaCalculadaCorretamenteNoProjetil() throws JogoException {
        Jogo jogo = new Jogo(800, 600);

        AlvoComum alvo = new AlvoComum(100, 100, 800, 600, jogo);
        jogo.adicionarAlvo(alvo); // adiciona o alvo para o teste

        // Criando projétil exatamente onde o alvo está, para forçar colisão (dx e dy
        // são irrelevantes aqui)
        Projetil projetil = new Projetil(100, 100, 1.0, 0.0, jogo);

        // Como o acesso é protegido, a lógica de colisão no run() marcará o alvo como
        // inativo
        // Podemos testar executando a rotina de colisão (acessada pelo run)
        projetil.start();

        try {
            Thread.sleep(50); // Dar tempo pra thread executar
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Se a distância for verificada e < raio, o projétil se desativa e o alvo
        // também
        assertFalse(projetil.isAtivo());
    }

    @Test
    public void testAdicaoDeCanhaoEPolimorfismoDeAlvo() throws JogoException {
        Jogo jogo = new Jogo(800, 600);

        jogo.adicionarCanhao(400, 300);
        assertEquals(1, jogo.getCanhoes().size());

        Alvo comum = new AlvoComum(10, 10, 800, 600, jogo);
        Alvo rapido = new AlvoRapido(20, 20, 800, 600, jogo);

        assertTrue(comum instanceof AlvoComum);
        assertTrue(rapido instanceof AlvoRapido);

        // Verificando polimorfismo nas velocidades base
        assertTrue(rapido.getVelocidade() > comum.getVelocidade());
    }
}

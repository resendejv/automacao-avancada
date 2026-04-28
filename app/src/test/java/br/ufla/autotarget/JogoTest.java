package br.ufla.autotarget;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Testes unitários do jogo AutoTarget.
 * Cobre pontos críticos: cálculo de distância, verificação de colisão,
 * validação de posição, polimorfismo de alvos, reconciliação de dados
 * e tratamento de exceções.
 */
public class JogoTest {

    private Jogo jogo;

    @Before
    public void setUp() {
        jogo = new Jogo(800, 600);
    }

    // ==================== TESTE 1: Exceção para posição inválida ====================

    @Test
    public void testPosicaoCanhaoInvalidaLancaExcecao() {
        try {
            // Tentando adicionar canhão fora dos limites (x = -10)
            jogo.adicionarCanhao(-10, 300);
            fail("Deveria ter lançado JogoException para x negativo");
        } catch (JogoException e) {
            assertEquals("Posição do canhão fora dos limites da tela!", e.getMessage());
        }

        try {
            // Tentando adicionar canhão fora dos limites (y > screenHeight)
            jogo.adicionarCanhao(400, 700);
            fail("Deveria ter lançado JogoException para y > screenHeight");
        } catch (JogoException e) {
            assertEquals("Posição do canhão fora dos limites da tela!", e.getMessage());
        }
    }

    // ==================== TESTE 2: Cálculo de distância ====================

    @Test
    public void testCalculoDistanciaEntreDosPontos() {
        // Distância entre (0,0) e (3,4) deve ser 5.0 (triângulo pitagórico)
        double dist = Canhao.calcularDistancia(0, 0, 3, 4);
        assertEquals(5.0, dist, 0.001);

        // Distância entre mesmos pontos deve ser 0
        double distZero = Canhao.calcularDistancia(100, 200, 100, 200);
        assertEquals(0.0, distZero, 0.001);

        // Distância diagonal
        double distDiag = Canhao.calcularDistancia(0, 0, 100, 100);
        assertEquals(Math.sqrt(20000), distDiag, 0.001);
    }

    // ==================== TESTE 3: Verificação de colisão ====================

    @Test
    public void testVerificacaoColisao() {
        // Projétil exatamente na posição do alvo -> colide (dist=0 < raio+5)
        assertTrue(Projetil.colide(100, 100, 100, 100, 20));

        // Projétil muito longe -> não colide
        assertFalse(Projetil.colide(0, 0, 500, 500, 20));

        // Projétil na borda do raio do alvo (raio=20, raioProjetil=5, dist=24.9)
        assertTrue(Projetil.colide(100, 100, 124, 100, 20)); // dist=24 < 25
        assertFalse(Projetil.colide(100, 100, 126, 100, 20)); // dist=26 > 25
    }

    // ==================== TESTE 4: Polimorfismo de Alvos ====================

    @Test
    public void testPolimorfismoAlvos() {
        Alvo comum = new AlvoComum(100, 100, 800, 600, jogo);
        Alvo rapido = new AlvoRapido(200, 200, 800, 600, jogo);

        // AlvoRapido é mais rápido que AlvoComum
        assertTrue(rapido.getVelocidade() > comum.getVelocidade());

        // AlvoRapido tem raio menor que AlvoComum (mais difícil de acertar)
        assertTrue(rapido.getRaio() < comum.getRaio());

        // Polimorfismo no movimento
        double xAntes = comum.getX();
        comum.mover();
        assertNotEquals(xAntes, comum.getX(), 0.0001);
    }

    // ==================== TESTE 5: Reconciliação de Dados ====================

    @Test
    public void testReconciliacaoValores() {
        // Média ponderada: 70% estimado + 30% sensor
        double resultado = DataReconciliation.reconciliarValor(100.0, 110.0, 0.7);
        assertEquals(103.0, resultado, 0.001); // 100*0.7 + 110*0.3 = 70 + 33 = 103

        // Peso total no estimado
        double resultado2 = DataReconciliation.reconciliarValor(50.0, 200.0, 1.0);
        assertEquals(50.0, resultado2, 0.001);

        // Peso total no sensor
        double resultado3 = DataReconciliation.reconciliarValor(50.0, 200.0, 0.0);
        assertEquals(200.0, resultado3, 0.001);

        // Peso igual
        double resultado4 = DataReconciliation.reconciliarValor(100.0, 200.0, 0.5);
        assertEquals(150.0, resultado4, 0.001);
    }

    // ==================== TESTE 6: Adição de canhão e campo ====================

    @Test
    public void testAdicaoDeCanhaoComCampo() throws JogoException {
        // Canhão no campo esquerdo (x < 400)
        jogo.adicionarCanhao(100, 300);
        assertEquals(1, jogo.getCanhoesPorCampo(0).size());
        assertEquals(0, jogo.getCanhoesPorCampo(1).size());

        // Canhão no campo direito (x > 400)
        jogo.adicionarCanhao(600, 300);
        assertEquals(1, jogo.getCanhoesPorCampo(0).size());
        assertEquals(1, jogo.getCanhoesPorCampo(1).size());

        // Total
        assertEquals(2, jogo.getCanhoes().size());
    }

    // ==================== TESTE 7: Registrar abates por campo ====================

    @Test
    public void testRegistrarAbatesPorCampo() {
        assertEquals(0, jogo.getAbatesEsquerda());
        assertEquals(0, jogo.getAbatesDireita());

        jogo.registrarAbate(0); // Esquerda
        jogo.registrarAbate(0);
        jogo.registrarAbate(1); // Direita

        assertEquals(2, jogo.getAbatesEsquerda());
        assertEquals(1, jogo.getAbatesDireita());
        assertEquals(3, jogo.getAbates()); // Total

        // Vencedor
        assertEquals("ESQUERDA", jogo.getVencedor());
    }

    // ==================== TESTE 8: JogoException com causa ====================

    @Test
    public void testJogoExceptionComCausa() {
        RuntimeException causa = new ArithmeticException("Divisão por zero");
        JogoException exception = new JogoException("Erro no cálculo de ângulo", causa);

        assertEquals("Erro no cálculo de ângulo", exception.getMessage());
        assertEquals(causa, exception.getCause());
        assertTrue(exception.getCause() instanceof ArithmeticException);
    }
}

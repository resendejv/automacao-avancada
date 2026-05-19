package br.ufla.autotarget;
import java.util.Random;

/**
 * Alvo rápido — velocidade maior, raio menor.
 * Mais difícil de acertar que o AlvoComum.
 */
public class AlvoRapido extends Alvo {
    private final Random random;

    public AlvoRapido(double x, double y, int screenWidth, int screenHeight, Jogo jogo) {
        super(x, y, 15.0, 7.0, screenWidth, screenHeight, jogo);
        this.random = new Random();
    }

    @Override
    public void mover() {
        // Ocasionalmente muda de direção de forma errática antes de mover
        if (random.nextDouble() < 0.05) {
            double angle = random.nextDouble() * 2 * Math.PI;
            dx = Math.cos(angle);
            dy = Math.sin(angle);
        }
        
        // Executa movimento linear e bounce padrão
        super.mover();
    }
}

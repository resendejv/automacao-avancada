package br.ufla.autotarget;

import java.util.Random;
import java.util.concurrent.Semaphore;

/**
 * Classe abstrata que representa um alvo no jogo.
 * Cada alvo roda em sua própria thread, movendo-se continuamente pelo canvas.
 * Utiliza semáforo para região crítica de colisão.
 */
public abstract class Alvo extends EntidadeMovel {
    protected double raio;
    protected double dx;
    protected double dy;

    // Semáforo para controlar acesso de projéteis a este alvo (Região Crítica)
    public final Semaphore semaforoColisao = new Semaphore(1);

    // Campo que identifica o lado: 0 = esquerdo, 1 = direito
    protected int campo;

    public Alvo(double x, double y, double raio, double velocidade, int screenWidth, int screenHeight, Jogo jogo) {
        super(x, y, velocidade, screenWidth, screenHeight, jogo);
        this.raio = raio;

        Random rand = new Random();
        double angle = rand.nextDouble() * 2 * Math.PI;
        this.dx = Math.cos(angle);
        this.dy = Math.sin(angle);

        // Determina o campo baseado na posição x inicial
        this.campo = (x < screenWidth / 2.0) ? 0 : 1;
    }

    public double getRaio() { return raio; }
    public int getCampo() { return campo; }

    public void setCampo(int campo) { this.campo = campo; }

    /**
     * Loop principal da thread do alvo.
     * Move o alvo a cada ~30ms enquanto estiver ativo.
     */
    @Override
    public void run() {
        if (!ativo) return;
        do {
            try {
                mover();

                // Atualiza campo baseado na posição atual
                int novoCampo = (x < screenWidth / 2.0) ? 0 : 1;
                if (novoCampo != campo) {
                    campo = novoCampo; // Cruzou a linha divisória
                }

                Thread.sleep(30); // ~33 FPS para movimento do alvo
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // Tratamento geral de exceções no alvo
                // android.util.Log.e("Alvo", "Erro no loop do alvo: " + e.getMessage());
            }
        } while (ativo);
    }

    @Override
    public abstract void mover();
}

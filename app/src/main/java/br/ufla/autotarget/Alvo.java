package br.ufla.autotarget;

import java.util.Random;
import java.util.concurrent.Semaphore;

/**
 * Classe abstrata que representa um alvo no jogo.
 * Opera via Runnable em Pool de Threads (AV4).
 */
public abstract class Alvo extends EntidadeMovel {
    protected double raio;
    protected double dx;
    protected double dy;
    private final Random randomSensor = new Random();

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
        this.ativo = true;
    }

    public double getRaio() { return raio; }
    public int getCampo() { return campo; }

    public void setCampo(int campo) { this.campo = campo; }

    /**
     * Simula a leitura de um sensor virtual com ruído gaussiano (AV2).
     */
    public double[] lerSensor() {
        double desvioPadrao = 0.05;
        double xNoisy = x + (randomSensor.nextGaussian() * x * desvioPadrao);
        double yNoisy = y + (randomSensor.nextGaussian() * y * desvioPadrao);
        double vx = dx * velocidade;
        double vy = dy * velocidade;
        double vxNoisy = vx + (randomSensor.nextGaussian() * Math.abs(vx) * desvioPadrao);
        double vyNoisy = vy + (randomSensor.nextGaussian() * Math.abs(vy) * desvioPadrao);
        return new double[]{xNoisy, yNoisy, vxNoisy, vyNoisy};
    }

    /**
     * Ciclo de execução do alvo (AV4).
     */
    @Override
    public void run() {
        if (!ativo) return;
        try {
            mover();

            // AV2: Transferência Atômica de Pertencimento de Campo
            int novoCampo = (x < screenWidth / 2.0) ? 0 : 1;
            if (novoCampo != campo) {
                campo = novoCampo;
            }
        } catch (Exception e) {
            ativo = false;
        }
    }

    /**
     * Movimento padrão linear com bounce nas bordas.
     */
    @Override
    public void mover() {
        x += dx * velocidade;
        y += dy * velocidade;

        // Bounce nas bordas
        if (x - raio < 0 || x + raio > screenWidth) {
            dx = -dx;
            x = Math.max(raio, Math.min(x, screenWidth - raio));
        }
        if (y - raio < 0 || y + raio > screenHeight) {
            dy = -dy;
            y = Math.max(raio, Math.min(y, screenHeight - raio));
        }
    }

    /**
     * Retorna a cor principal do alvo para renderização.
     */
    public abstract int getCor();

    /**
     * Retorna a cor da borda do alvo para renderização.
     */
    public abstract int getCorBorda();

    /**
     * Retorna a intensidade do brilho (glow) do alvo (0-255).
     */
    public abstract int getGlowAlpha();
}

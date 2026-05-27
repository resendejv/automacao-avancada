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
    }

    public double getRaio() { return raio; }
    public int getCampo() { return campo; }

    public void setCampo(int campo) { this.campo = campo; }

    /**
     * Simula a leitura de um sensor virtual com ruído gaussiano (AV2).
     * @return Array com [x_ruidoso, y_ruidoso, vx_ruidoso, vy_ruidoso]
     */
    public double[] lerSensor() {
        double desvioPadrao = 0.05; // 5% de ruído conforme especificação AV2
        
        double xNoisy = x + (randomSensor.nextGaussian() * x * desvioPadrao);
        double yNoisy = y + (randomSensor.nextGaussian() * y * desvioPadrao);
        
        double vx = dx * velocidade;
        double vy = dy * velocidade;
        
        double vxNoisy = vx + (randomSensor.nextGaussian() * Math.abs(vx) * desvioPadrao);
        double vyNoisy = vy + (randomSensor.nextGaussian() * Math.abs(vy) * desvioPadrao);
        
        return new double[]{xNoisy, yNoisy, vxNoisy, vyNoisy};
    }

    /**
     * Loop principal da thread do alvo.
     * Move o alvo a cada ~30ms enquanto estiver ativo.
     */
    @Override
    public void run() {
        while (ativo) {
            try {
                mover();

                // AV2: Transferência Atômica de Pertencimento de Campo
                // O alvo detecta se cruzou a linha divisória e atualiza seu estado.
                // Como usamos CopyOnWriteArrayList e volatile, a visibilidade é imediata para os canhões.
                int novoCampo = (x < screenWidth / 2.0) ? 0 : 1;
                if (novoCampo != campo) {
                    campo = novoCampo; // Cruzou a linha divisória (Transição Atômica)
                }

                Thread.sleep(30); // ~33 FPS para movimento do alvo
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // Em caso de erro inesperado, logamos e encerramos a thread para evitar loop de erros
                break;
            }
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

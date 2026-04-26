package br.ufla.autotarget;

import java.util.Random;
import java.util.concurrent.Semaphore;

public abstract class Alvo extends Thread {
    protected double x;
    protected double y;
    protected double raio;
    protected double velocidade;
    protected boolean ativo;
    protected double dx;
    protected double dy;
    protected int screenWidth;
    protected int screenHeight;
    protected Jogo jogo;
    
    // Semáforo para controlar acesso de projéteis a este alvo (Região Crítica)
    public Semaphore semaforoColisao = new Semaphore(1);

    public Alvo(double x, double y, double raio, double velocidade, int screenWidth, int screenHeight, Jogo jogo) {
        this.x = x;
        this.y = y;
        this.raio = raio;
        this.velocidade = velocidade;
        this.ativo = true;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.jogo = jogo;

        Random rand = new Random();
        double angle = rand.nextDouble() * 2 * Math.PI;
        this.dx = Math.cos(angle);
        this.dy = Math.sin(angle);
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public double getRaio() { return raio; }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }

    // Método polimórfico
    public abstract void mover();

    @Override
    public void run() {
        while (ativo) {
            mover();
            // Verifica colisões se necessário (ou deixa o projétil verificar)
            
            try {
                Thread.sleep(30); // ~33 fps
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}

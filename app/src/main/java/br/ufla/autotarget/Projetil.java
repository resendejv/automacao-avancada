package br.ufla.autotarget;

import java.util.List;

public class Projetil extends Thread {
    private double x;
    private double y;
    private double dx;
    private double dy;
    private double velocidade = 10.0;
    private boolean ativo = true;
    private Jogo jogo;

    public Projetil(double startX, double startY, double dx, double dy, Jogo jogo) {
        this.x = startX;
        this.y = startY;
        this.dx = dx;
        this.dy = dy;
        this.jogo = jogo;
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }

    @Override
    public void run() {
        while (ativo) {
            // Mover o projétil
            x += dx * velocidade;
            y += dy * velocidade;

            // Verificar se saiu da tela
            if (x < 0 || x > jogo.getScreenWidth() || y < 0 || y > jogo.getScreenHeight()) {
                ativo = false;
                break;
            }

            // Verificar colisão
            verificarColisao();

            try {
                Thread.sleep(20); // ~50 fps para o projétil
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        jogo.removerProjetil(this);
    }

    private void verificarColisao() {
        List<Alvo> alvos = jogo.getAlvos(); // Pode retornar uma cópia ou precisar de lock
        
        for (Alvo alvo : alvos) {
            if (!alvo.isAtivo()) continue;
            
            // Região Crítica: Apenas um projétil verifica colisão com este alvo por vez
            try {
                alvo.semaforoColisao.acquire();
                
                // Double check após adquirir o lock
                if (!alvo.isAtivo()) {
                    alvo.semaforoColisao.release();
                    continue;
                }

                double dist = Math.hypot(this.x - alvo.getX(), this.y - alvo.getY());
                if (dist < alvo.getRaio() + 5.0) { // 5.0 é o raio aproximado do projétil
                    alvo.setAtivo(false); // Abatido
                    this.ativo = false;   // Destruído no impacto
                    alvo.semaforoColisao.release();
                    jogo.registrarAbate();
                    break;
                }
                
                alvo.semaforoColisao.release();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}

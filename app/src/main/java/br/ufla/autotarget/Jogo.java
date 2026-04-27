package br.ufla.autotarget;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Jogo {
    private List<Alvo> alvos;
    private List<Canhao> canhoes;
    private List<Projetil> projeteis;

    private int screenWidth;
    private int screenHeight;
    private int abates = 0;
    private boolean rodando = false;

    public Jogo(int screenWidth, int screenHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.alvos = new ArrayList<>();
        this.canhoes = new ArrayList<>();
        this.projeteis = new ArrayList<>();
    }

    public int getScreenWidth() { return screenWidth; }
    public int getScreenHeight() { return screenHeight; }

    public synchronized void registrarAbate() {
        abates++;
    }

    public synchronized int getAbates() {
        return abates;
    }

    public synchronized void adicionarCanhao(double x, double y) throws JogoException {
        if (x < 0 || x > screenWidth || y < 0 || y > screenHeight) {
            throw new JogoException("Posição do canhão fora dos limites da tela!");
        }
        Canhao canhao = new Canhao(x, y, this);
        canhoes.add(canhao);
        if (rodando) {
            canhao.start();
        }
    }

    public synchronized void adicionarProjetil(Projetil p) {
        projeteis.add(p);
    }

    public synchronized void adicionarAlvo(Alvo a) {
        alvos.add(a);
    }

    public synchronized void removerProjetil(Projetil p) {
        projeteis.remove(p);
    }

    // Retorna cópia para iteração segura
    public synchronized List<Alvo> getAlvos() {
        return new ArrayList<>(alvos);
    }

    public synchronized List<Canhao> getCanhoes() {
        return new ArrayList<>(canhoes);
    }

    public synchronized List<Projetil> getProjeteis() {
        return new ArrayList<>(projeteis);
    }

    public synchronized void iniciarJogo() {
        if (rodando) return;
        rodando = true;

        // Limpar alvos antigos
        alvos.clear();
        projeteis.clear();

        // Criar alguns alvos iniciais
        Random rand = new Random();
        for (int i = 0; i < 5; i++) {
            Alvo alvo = (rand.nextBoolean()) ? 
                new AlvoComum(rand.nextInt(screenWidth), rand.nextInt(screenHeight), screenWidth, screenHeight, this) :
                new AlvoRapido(rand.nextInt(screenWidth), rand.nextInt(screenHeight), screenWidth, screenHeight, this);
            alvos.add(alvo);
            alvo.start();
        }

        // Iniciar canhões já adicionados (recriando se foram terminados)
        List<Canhao> canhoesAtivos = new ArrayList<>();
        for (Canhao c : canhoes) {
            if (c.getState() == Thread.State.TERMINATED) {
                Canhao novoCanhao = new Canhao(c.getX(), c.getY(), this);
                canhoesAtivos.add(novoCanhao);
                novoCanhao.start();
            } else if (!c.isAlive()) {
                c.start();
                canhoesAtivos.add(c);
            } else {
                canhoesAtivos.add(c);
            }
        }
        canhoes = canhoesAtivos;

        // Thread para remover alvos destruídos e criar novos
        new Thread(() -> {
            while (rodando) {
                synchronized (this) {
                    alvos.removeIf(a -> !a.isAtivo());
                    // Repor alvos se houver menos que 5
                    while (alvos.size() < 5) {
                        Alvo alvo = (rand.nextBoolean()) ? 
                            new AlvoComum(rand.nextInt(screenWidth), rand.nextInt(screenHeight), screenWidth, screenHeight, this) :
                            new AlvoRapido(rand.nextInt(screenWidth), rand.nextInt(screenHeight), screenWidth, screenHeight, this);
                        alvos.add(alvo);
                        alvo.start();
                    }
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }).start();
    }

    public synchronized void pararJogo() {
        rodando = false;
        for (Alvo a : alvos) a.setAtivo(false);
        for (Canhao c : canhoes) c.setAtivo(false);
        for (Projetil p : projeteis) p.setAtivo(false);
    }
}

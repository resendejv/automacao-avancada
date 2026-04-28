package br.ufla.autotarget;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Classe principal do jogo AutoTarget.
 * Gerencia as listas de alvos, canhões e projéteis.
 * Controla o loop principal da UI, timer, energia e estado do jogo.
 *
 * A tela é dividida em dois campos (esquerdo=0, direito=1).
 * Cada campo tem seus próprios canhões e disputa para abater alvos.
 */
public class Jogo {
    private static final String TAG = "Jogo";

    // Listas compartilhadas (região crítica)
    private final List<Alvo> alvos;
    private final List<Canhao> canhoes;
    private final List<Projetil> projeteis;

    // Dimensões da tela
    private final int screenWidth;
    private final int screenHeight;

    // Placar por campo
    private int abatesEsquerda = 0;
    private int abatesDireita = 0;

    // Energia por campo (consumida por canhão ativo por segundo)
    private double energiaEsquerda = 100.0;
    private double energiaDireita = 100.0;
    private static final double ENERGIA_MAXIMA = 100.0;
    private static final double ENERGIA_POR_CANHAO = 0.8; // por segundo
    private static final double ENERGIA_REGEN = 1.0;      // regeneração por segundo

    // Estado do jogo
    private volatile boolean rodando = false;
    private volatile boolean encerrado = false;

    // Timer
    private long tempoInicio;
    private static final int DURACAO_JOGO_SEGUNDOS = 60;

    // Thread de reconciliação
    private DataReconciliation reconciliacao;

    // Listener para eventos do jogo
    private JogoListener listener;

    // Número de alvos a manter ativos por campo
    private static final int ALVOS_POR_CAMPO = 4;

    public Jogo(int screenWidth, int screenHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.alvos = new ArrayList<>();
        this.canhoes = new ArrayList<>();
        this.projeteis = new ArrayList<>();
    }

    // ==================== Getters ====================

    public int getScreenWidth() { return screenWidth; }
    public int getScreenHeight() { return screenHeight; }

    public synchronized int getAbatesEsquerda() { return abatesEsquerda; }
    public synchronized int getAbatesDireita() { return abatesDireita; }

    public synchronized double getEnergiaEsquerda() { return energiaEsquerda; }
    public synchronized double getEnergiaDireita() { return energiaDireita; }

    public boolean isRodando() { return rodando; }
    public boolean isEncerrado() { return encerrado; }

    /**
     * Verifica se o campo possui energia suficiente para atirar.
     */
    public synchronized boolean temEnergia(int campo) {
        return (campo == 0) ? energiaEsquerda > 0 : energiaDireita > 0;
    }

    /**
     * Retorna o tempo restante em segundos.
     */
    public int getTempoRestante() {
        if (!rodando) return DURACAO_JOGO_SEGUNDOS;
        long elapsed = (System.currentTimeMillis() - tempoInicio) / 1000;
        return (int) Math.max(0, DURACAO_JOGO_SEGUNDOS - elapsed);
    }

    /**
     * Retorna o vencedor da partida.
     */
    public String getVencedor() {
        if (abatesEsquerda > abatesDireita) return "ESQUERDA";
        if (abatesDireita > abatesEsquerda) return "DIREITA";
        return "EMPATE";
    }

    public void setListener(JogoListener listener) {
        this.listener = listener;
    }

    // ==================== Registrar Abate ====================

    /**
     * Registra um abate no campo especificado.
     * @param campo 0 = esquerdo, 1 = direito
     */
    public synchronized void registrarAbate(int campo) {
        switch (campo) {
            case 0:
                abatesEsquerda++;
                break;
            case 1:
                abatesDireita++;
                break;
            default:
                Log.w(TAG, "Campo inválido para registrar abate: " + campo);
                return;
        }
        Log.d(TAG, "Abate registrado! Esq: " + abatesEsquerda + " Dir: " + abatesDireita);
    }

    public synchronized int getAbates() {
        return abatesEsquerda + abatesDireita;
    }

    // ==================== Gerenciamento de Entidades ====================

    public synchronized void adicionarCanhao(double x, double y) throws JogoException {
        int campo = (x < screenWidth / 2.0) ? 0 : 1;

        // Validação de posição
        if (x < 0 || x > screenWidth || y < 0 || y > screenHeight) {
            throw new JogoException("Posição do canhão fora dos limites da tela!");
        }

        // Validação de limite máximo
        int canhoesNoCampo = getCanhoesPorCampo(campo).size();
        if (canhoesNoCampo >= Canhao.MAX_CANHOES) {
            throw new JogoException("Limite máximo de " + Canhao.MAX_CANHOES +
                    " canhões atingido no campo " + (campo == 0 ? "esquerdo" : "direito") + "!");
        }

        // Verificar energia mínima para instalação
        double energiaCampo = (campo == 0) ? energiaEsquerda : energiaDireita;
        if (energiaCampo < 10.0) {
            throw new JogoException("Energia insuficiente para adicionar canhão!");
        }

        Canhao canhao = new Canhao(x, y, this, campo);
        canhoes.add(canhao);

        // Atualiza penalidade em todos os canhões do campo
        int totalCanhoesNoCampo = canhoesNoCampo + 1;
        for (Canhao c : canhoes) {
            if (c.getCampo() == campo) {
                c.atualizarPenalidade(totalCanhoesNoCampo);
            }
        }

        if (rodando) {
            canhao.start();
        }

        Log.d(TAG, "Canhão adicionado no campo " + (campo == 0 ? "ESQ" : "DIR") +
                " em (" + x + ", " + y + "). Total no campo: " + totalCanhoesNoCampo);
    }

    public synchronized void adicionarProjetil(Projetil p) {
        projeteis.add(p);
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

    /**
     * Retorna canhões de um campo específico.
     */
    public synchronized List<Canhao> getCanhoesPorCampo(int campo) {
        List<Canhao> resultado = new ArrayList<>();
        for (Canhao c : canhoes) {
            if (c.getCampo() == campo) resultado.add(c);
        }
        return resultado;
    }

    // ==================== Ciclo do Jogo ====================

    /**
     * Inicia o jogo: estado RODANDO.
     * Cria alvos iniciais, inicia canhões e threads periódicas.
     */
    public synchronized void iniciarJogo() {
        if (rodando) return;
        rodando = true;
        encerrado = false;
        abatesEsquerda = 0;
        abatesDireita = 0;
        energiaEsquerda = ENERGIA_MAXIMA;
        energiaDireita = ENERGIA_MAXIMA;
        tempoInicio = System.currentTimeMillis();

        // Limpar entidades antigas
        for (Alvo a : alvos) a.setAtivo(false);
        for (Projetil p : projeteis) p.setAtivo(false);
        for (Canhao c : canhoes) c.setAtivo(false);
        
        alvos.clear();
        projeteis.clear();
        canhoes.clear(); // REMOVE CANHÕES DO ÚLTIMO JOGO

        Random rand = new Random();

        // Criar alvos iniciais para ambos os campos
        criarAlvosIniciais(rand, 0); // Campo esquerdo
        criarAlvosIniciais(rand, 1); // Campo direito

        // Thread para gerenciar alvos (remover destruídos, criar novos)
        new Thread(() -> {
            Random r = new Random();
            while (rodando) {
                synchronized (this) {
                    alvos.removeIf(a -> !a.isAtivo());

                    // Repor alvos por campo
                    int alvosEsq = 0, alvosDir = 0;
                    for (Alvo a : alvos) {
                        if (a.getCampo() == 0) alvosEsq++;
                        else alvosDir++;
                    }
                    while (alvosEsq < ALVOS_POR_CAMPO) {
                        criarAlvoNoCampo(r, 0);
                        alvosEsq++;
                    }
                    while (alvosDir < ALVOS_POR_CAMPO) {
                        criarAlvoNoCampo(r, 1);
                        alvosDir++;
                    }
                }
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "Thread-GerenciadorAlvos").start();

        // Thread de energia
        new Thread(() -> {
            while (rodando) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                atualizarEnergia();
            }
        }, "Thread-Energia").start();

        // Thread de timer
        new Thread(() -> {
            while (rodando) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                if (getTempoRestante() <= 0) {
                    encerrarJogo();
                    break;
                }
            }
        }, "Thread-Timer").start();

        // Iniciar reconciliação de dados (a cada 10s)
        reconciliacao = new DataReconciliation(this);
        reconciliacao.start();

        Log.d(TAG, "Jogo iniciado! Duração: " + DURACAO_JOGO_SEGUNDOS + "s");
    }

    /**
     * Cria alvos iniciais para um campo.
     */
    private void criarAlvosIniciais(Random rand, int campo) {
        int xMin = (campo == 0) ? 30 : screenWidth / 2 + 30;
        int xMax = (campo == 0) ? screenWidth / 2 - 30 : screenWidth - 30;

        for (int i = 0; i < ALVOS_POR_CAMPO; i++) {
            int ax = xMin + rand.nextInt(Math.max(1, xMax - xMin));
            int ay = 30 + rand.nextInt(Math.max(1, screenHeight - 60));
            Alvo alvo = criarAlvoAleatorio(rand, ax, ay);
            alvo.setCampo(campo);
            alvos.add(alvo);
            alvo.start();
        }
    }

    /**
     * Cria um alvo em um campo específico.
     */
    private void criarAlvoNoCampo(Random rand, int campo) {
        int xMin = (campo == 0) ? 30 : screenWidth / 2 + 30;
        int xMax = (campo == 0) ? screenWidth / 2 - 30 : screenWidth - 30;

        int ax = xMin + rand.nextInt(Math.max(1, xMax - xMin));
        int ay = 30 + rand.nextInt(Math.max(1, screenHeight - 60));
        Alvo alvo = criarAlvoAleatorio(rand, ax, ay);
        alvo.setCampo(campo);
        alvos.add(alvo);
        alvo.start();
    }

    private Alvo criarAlvoAleatorio(Random rand, int x, int y) {
        if (rand.nextBoolean()) {
            return new AlvoComum(x, y, screenWidth, screenHeight, this);
        } else {
            return new AlvoRapido(x, y, screenWidth, screenHeight, this);
        }
    }

    /**
     * Atualiza energia de ambos os campos.
     * Canhões consomem energia, e há regeneração passiva.
     */
    private synchronized void atualizarEnergia() {
        int canhoesEsq = getCanhoesPorCampo(0).size();
        int canhoesDir = getCanhoesPorCampo(1).size();

        energiaEsquerda -= canhoesEsq * ENERGIA_POR_CANHAO;
        energiaEsquerda += ENERGIA_REGEN;
        energiaEsquerda = Math.max(0, Math.min(ENERGIA_MAXIMA, energiaEsquerda));

        energiaDireita -= canhoesDir * ENERGIA_POR_CANHAO;
        energiaDireita += ENERGIA_REGEN;
        energiaDireita = Math.max(0, Math.min(ENERGIA_MAXIMA, energiaDireita));
    }

    /**
     * Encerra o jogo: mostra resultados e agenda reinício.
     */
    public void encerrarJogo() {
        if (encerrado) return;
        encerrado = true;
        
        String vencedor = getVencedor();
        int abatesE = abatesEsquerda;
        int abatesD = abatesDireita;

        pararJogoInterno(false); // Para as threads mas mantém o estado 'encerrado'

        Log.d(TAG, "=== JOGO ENCERRADO ===");
        
        // Notifica listener (UI)
        if (listener != null) {
            listener.onJogoEncerrado(vencedor, abatesE, abatesD);
        }

        // AGENDAR REINÍCIO AUTOMÁTICO APÓS 3 SEGUNDOS
        new Thread(() -> {
            try {
                Thread.sleep(3000);
                if (encerrado) { // Se ainda estiver no estado encerrado (não clicou em Parar)
                    iniciarJogo();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "Thread-AutoRestart").start();
    }

    /**
     * Para todas as threads do jogo. Chamado manualmente pelo usuário.
     */
    public synchronized void pararJogo() {
        pararJogoInterno(true);
    }

    private synchronized void pararJogoInterno(boolean resetEncerrado) {
        rodando = false;
        if (resetEncerrado) {
            encerrado = false;
        }

        for (Alvo a : alvos) a.setAtivo(false);
        for (Canhao c : canhoes) c.setAtivo(false);
        for (Projetil p : projeteis) p.setAtivo(false);

        if (reconciliacao != null) {
            reconciliacao.setAtivo(false);
            reconciliacao.interrupt();
        }
    }

    // ==================== Listener Interface ====================

    /**
     * Interface para comunicação de eventos do jogo com a UI.
     */
    public interface JogoListener {
        void onJogoEncerrado(String vencedor, int abatesEsquerda, int abatesDireita);
    }
}

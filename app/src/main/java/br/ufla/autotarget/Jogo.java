package br.ufla.autotarget;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Map;
import java.util.Deque;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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

    // Listas thread-safe para alta performance de leitura na UI
    // AV2: Alvos permanecem em uma lista global para movimento, 
    // mas o pertencimento ao campo é controlado internamente no Alvo.
    private final List<Alvo> alvos = new CopyOnWriteArrayList<>();
    private final List<Canhao> canhoes = new CopyOnWriteArrayList<>();
    private final List<Projetil> projeteis = new CopyOnWriteArrayList<>();

    // Dimensões da tela
    private final int screenWidth;
    private final int screenHeight;

    // Placar atômico para evitar contenção
    private final AtomicInteger abatesEsquerda = new AtomicInteger(0);
    private final AtomicInteger abatesDireita = new AtomicInteger(0);

    // Energia por campo com acesso atômico
    private final AtomicReference<Double> energiaEsquerda = new AtomicReference<>(100.0);
    private final AtomicReference<Double> energiaDireita = new AtomicReference<>(100.0);
    private static final double ENERGIA_MAXIMA = 100.0;
    private static final double ENERGIA_POR_CANHAO = 1; // por segundo
    private static final double ENERGIA_REGEN = 1.0;      // regeneração por segundo

    // Estado do jogo
    private volatile boolean rodando = false;
    private volatile boolean encerrado = false;

    // Timer
    private long tempoInicio;
    private static final int DURACAO_JOGO_SEGUNDOS = 60;

    // Threads de controle
    private Thread threadGerenciadorAlvos;
    private Thread threadEnergia;
    private Thread threadTimer;
    private Thread threadAutoRestart;
    private Thread threadColetaDados;
    private DataReconciliation reconciliacao;

    // Buffer de leituras por alvo (AV2)
    // Map<TargetID, Deque de últimas 10 leituras>
    private final Map<Integer, Deque<LeituraSensor>> bufferLeituras = new ConcurrentHashMap<>();

    // Listener para eventos do jogo
    private JogoListener listener;

    // Número de alvos a manter ativos por campo
    private static final int ALVOS_POR_CAMPO = 4;

    // Locks de granularidade fina
    private final Object lifecycleLock = new Object();

    public Jogo(int screenWidth, int screenHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
    }

    // ==================== Getters ====================

    public int getScreenWidth() { return screenWidth; }
    public int getScreenHeight() { return screenHeight; }

    public int getAbatesEsquerda() { return abatesEsquerda.get(); }
    public int getAbatesDireita() { return abatesDireita.get(); }

    public double getEnergiaEsquerda() { return energiaEsquerda.get(); }
    public double getEnergiaDireita() { return energiaDireita.get(); }

    public boolean isRodando() { return rodando; }
    public boolean isEncerrado() { return encerrado; }

    /**
     * Verifica se o campo possui energia suficiente para atirar.
     */
    public boolean temEnergia(int campo) {
        return (campo == 0) ? energiaEsquerda.get() > 0 : energiaDireita.get() > 0;
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
        int e = abatesEsquerda.get();
        int d = abatesDireita.get();
        if (e > d) return "ESQUERDA";
        if (d > e) return "DIREITA";
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
    public void registrarAbate(int campo) {
        if (campo == 0) {
            abatesEsquerda.incrementAndGet();
        } else if (campo == 1) {
            abatesDireita.incrementAndGet();
        }
    }

    public int getAbates() {
        return abatesEsquerda.get() + abatesDireita.get();
    }

    // ==================== Gerenciamento de Entidades ====================

    public void adicionarCanhao(double x, double y) throws JogoException {
        int campo = (x < screenWidth / 2.0) ? 0 : 1;

        // Validação de posição
        if (x < 0 || x > screenWidth || y < 0 || y > screenHeight) {
            throw new JogoException("Posição do canhão fora dos limites da tela!");
        }

        // Validação de limite máximo e energia (operação atômica simplificada via listas thread-safe)
        List<Canhao> canhoesNoCampo = getCanhoesPorCampo(campo);
        if (canhoesNoCampo.size() >= Canhao.MAX_CANHOES) {
            throw new JogoException("Limite máximo de " + Canhao.MAX_CANHOES +
                    " canhões atingido no campo " + (campo == 0 ? "esquerdo" : "direito") + "!");
        }

        double energiaCampo = (campo == 0) ? energiaEsquerda.get() : energiaDireita.get();
        if (energiaCampo < 10.0) {
            throw new JogoException("Energia insuficiente para adicionar canhão!");
        }

        Canhao canhao = new Canhao(x, y, this, campo);
        canhoes.add(canhao);

        // Atualiza penalidade em todos os canhões do campo
        int totalCanhoesNoCampo = canhoesNoCampo.size() + 1;
        for (Canhao c : canhoes) {
            if (c.getCampo() == campo) {
                c.atualizarPenalidade(totalCanhoesNoCampo);
            }
        }

        // Inicia a thread fora de qualquer bloco de sincronização complexo se necessário, 
        // mas como as listas são CopyOnWrite e não há lock 'this' global aqui, o risco é menor.
        // Contudo, mover para o final do método é mais limpo.
        if (rodando) {
            canhao.start();
        }
    }

    public void adicionarProjetil(Projetil p) {
        projeteis.add(p);
    }

    public void removerProjetil(Projetil p) {
        projeteis.remove(p);
    }

    /**
     * Remove o canhão mais antigo de um campo (AV2 - Otimização Autônoma).
     */
    public void removerCanhaoMaisAntigo(int campo) {
        for (Canhao c : canhoes) {
            if (c.getCampo() == campo) {
                c.setAtivo(false);
                c.interrupt();
                canhoes.remove(c);
                break;
            }
        }
        
        // Atualiza penalidades dos restantes
        List<Canhao> restantes = getCanhoesPorCampo(campo);
        for (Canhao c : restantes) {
            c.atualizarPenalidade(restantes.size());
        }
    }

    // Retorna a própria lista (CopyOnWriteArrayList permite iteração segura)
    public List<Alvo> getAlvos() {
        return alvos;
    }

    public List<Canhao> getCanhoes() {
        return canhoes;
    }

    public List<Projetil> getProjeteis() {
        return projeteis;
    }

    /**
     * Retorna canhões de um campo específico.
     */
    public List<Canhao> getCanhoesPorCampo(int campo) {
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
    public void iniciarJogo() {
        synchronized (lifecycleLock) {
            if (rodando) return;
            rodando = true;
            encerrado = false;
            abatesEsquerda.set(0);
            abatesDireita.set(0);
            energiaEsquerda.set(ENERGIA_MAXIMA);
            energiaDireita.set(ENERGIA_MAXIMA);
            tempoInicio = System.currentTimeMillis();

            // Limpar entidades antigas
            for (Alvo a : alvos) {
                a.setAtivo(false);
                a.interrupt();
            }
            for (Projetil p : projeteis) {
                p.setAtivo(false);
                p.interrupt();
            }
            for (Canhao c : canhoes) {
                c.setAtivo(false);
                c.interrupt();
            }

            alvos.clear();
            projeteis.clear();
            canhoes.clear(); // REMOVE CANHÕES DO ÚLTIMO JOGO

            Random rand = new Random();

            // Criar alvos iniciais para ambos os campos
            criarAlvosIniciais(rand, 0); // Campo esquerdo
            criarAlvosIniciais(rand, 1); // Campo direito

            // Thread para gerenciar alvos (remover destruídos, criar novos)
            threadGerenciadorAlvos = new Thread(() -> {
                Random r = new Random();
                while (rodando) {
                    alvos.removeIf(a -> !a.isAtivo());

                    // AV2: Repor alvos garantindo distribuição equilibrada por campo
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

                    try {
                        Thread.sleep(1500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }, "Thread-GerenciadorAlvos");
            threadGerenciadorAlvos.setDaemon(true);
            threadGerenciadorAlvos.start();

            // Thread de energia
            threadEnergia = new Thread(() -> {
                while (rodando) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    atualizarEnergia();
                }
            }, "Thread-Energia");
            threadEnergia.setDaemon(true);
            threadEnergia.start();

            // Thread de timer
            threadTimer = new Thread(() -> {
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
            }, "Thread-Timer");
            threadTimer.setDaemon(true);
            threadTimer.start();

            // Iniciar reconciliação de dados (a cada 10s)
            reconciliacao = new DataReconciliation(this);
            reconciliacao.setDaemon(true);
            reconciliacao.start();

            // Thread de Coleta de Dados (Sensores Virtuais - AV2)
            threadColetaDados = new Thread(() -> {
                while (rodando) {
                    try {
                        Thread.sleep(1000); // Coleta a cada 1 segundo
                        coletarLeiturasSensores();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }, "Thread-ColetaDados");
            threadColetaDados.setDaemon(true);
            threadColetaDados.start();

            Log.d(TAG, "Jogo iniciado! Duração: " + DURACAO_JOGO_SEGUNDOS + "s");
        }
    }

    /**
     * Coleta leituras ruidosas de todos os alvos ativos (AV2).
     */
    private void coletarLeiturasSensores() {
        for (Alvo alvo : alvos) {
            if (!alvo.isAtivo()) continue;
            
            // Cada alvo gera sua própria leitura ruidosa (gaussiana)
            LeituraSensor leitura = new LeituraSensor(alvo.lerSensor());
            
            // Armazena no buffer (mantendo apenas as últimas 10 para análise estatística)
            int alvoId = alvo.hashCode();
            bufferLeituras.computeIfAbsent(alvoId, k -> new ArrayDeque<>());
            Deque<LeituraSensor> history = bufferLeituras.get(alvoId);
            
            if (history != null) {
                synchronized (history) {
                    history.addLast(leitura);
                    if (history.size() > 10) {
                        history.removeFirst();
                    }
                }
            }
        }
        // Limpa buffers de alvos que não existem mais
        bufferLeituras.keySet().removeIf(id -> {
            for (Alvo a : alvos) if (a.hashCode() == id) return false;
            return true;
        });
    }

    /**
     * Retorna estatísticas (média e variância) para um alvo específico (Excelente AV2).
     */
    public double[][] getEstatisticasAlvo(Alvo alvo) {
        Deque<LeituraSensor> history = bufferLeituras.get(alvo.hashCode());
        if (history == null || history.size() < 2) return null;

        synchronized (history) {
            int n = history.size();
            double sumX = 0, sumY = 0, sumSqX = 0, sumSqY = 0;
            
            for (LeituraSensor l : history) {
                sumX += l.x;
                sumY += l.y;
                sumSqX += l.x * l.x;
                sumSqY += l.y * l.y;
            }
            
            double mediaX = sumX / n;
            double mediaY = sumY / n;
            double varianciaX = (sumSqX / n) - (mediaX * mediaX);
            double varianciaY = (sumSqY / n) - (mediaY * mediaY);
            
            return new double[][]{{mediaX, mediaY}, {varianciaX, varianciaY}};
        }
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

    private void atualizarEnergia() {
        int canhoesEsq = getCanhoesPorCampo(0).size();
        int canhoesDir = getCanhoesPorCampo(1).size();

        energiaEsquerda.updateAndGet(e -> Math.max(0, Math.min(ENERGIA_MAXIMA, e - canhoesEsq * ENERGIA_POR_CANHAO + ENERGIA_REGEN)));
        energiaDireita.updateAndGet(e -> Math.max(0, Math.min(ENERGIA_MAXIMA, e - canhoesDir * ENERGIA_POR_CANHAO + ENERGIA_REGEN)));
    }

    /**
     * Encerra o jogo: mostra resultados e agenda reinício.
     */
    public void encerrarJogo() {
        if (encerrado) return;
        encerrado = true;
        
        String vencedor = getVencedor();
        int abatesE = abatesEsquerda.get();
        int abatesD = abatesDireita.get();

        pararJogoInterno(false); // Para as threads mas mantém o estado 'encerrado'

        Log.d(TAG, "=== JOGO ENCERRADO ===");
        
        // Notifica listener (UI)
        if (listener != null) {
            listener.onJogoEncerrado(vencedor, abatesE, abatesD);
        }

        // AGENDAR REINÍCIO AUTOMÁTICO APÓS 3 SEGUNDOS
        threadAutoRestart = new Thread(() -> {
            try {
                Thread.sleep(3000);
                if (encerrado) { // Se ainda estiver no estado encerrado (não clicou em Parar)
                    iniciarJogo();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "Thread-AutoRestart");
        threadAutoRestart.setDaemon(true);
        threadAutoRestart.start();
    }

    /**
     * Para todas as threads do jogo. Chamado manualmente pelo usuário.
     */
    public void pararJogo() {
        synchronized (lifecycleLock) {
            pararJogoInterno(true);
        }
    }

    private void pararJogoInterno(boolean resetEncerrado) {
        rodando = false;
        if (resetEncerrado) {
            encerrado = false;
        }

        // Interrompe threads de controle para encerramento rápido
        if (threadGerenciadorAlvos != null) threadGerenciadorAlvos.interrupt();
        if (threadEnergia != null) threadEnergia.interrupt();
        if (threadTimer != null) threadTimer.interrupt();
        if (threadAutoRestart != null) threadAutoRestart.interrupt();
        if (threadColetaDados != null) threadColetaDados.interrupt();

        for (Alvo a : alvos) {
            a.setAtivo(false);
            a.interrupt();
        }
        for (Canhao c : canhoes) {
            c.setAtivo(false);
            c.interrupt();
        }
        for (Projetil p : projeteis) {
            p.setAtivo(false);
            p.interrupt();
        }

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

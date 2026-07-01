package br.ufla.autotarget;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
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
    private Thread threadCPS;
    private DataReconciliation reconciliacao;

    // Buffer de leituras por alvo (AV2)
    // Map<TargetID, Deque de últimas 10 leituras>
    private final Map<Integer, Deque<LeituraSensor>> bufferLeituras = new ConcurrentHashMap<>();

    // Listener para eventos do jogo
    private JogoListener listener;

    // Repositório para persistência (AV3)
    private final GameRepository repository = new GameRepository();

    // Número de alvos a manter ativos por campo
    private static final int ALVOS_POR_CAMPO = 4;

    // Configuração de Benchmark AV4
    public static boolean USE_THREAD_POOL = true; // Alternar para false para motor antigo

    // Locks de granularidade fina
    private final Object lifecycleLock = new Object();

    // Métricas AV4
    private long totalCycleTime = 0;
    private int cycleCount = 0;
    private long lastMetricsLogTime = 0;
    private int forcarAlvos = -1; // -1 significa modo normal, >0 ativa o Benchmark

    // Pool de Threads Global (AV4 - Melhoria Arquitetural)
    private ScheduledExecutorService gamePool;
    
    // Pool de Objetos para Projéteis (Object Pooling AV4)
    private final List<Projetil> poolProjeteis = new ArrayList<>();
    private static final int MAX_POOL_PROJETEIS = 50;

    public Jogo(int screenWidth, int screenHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        
        // Inicializa o pool de projéteis
        for (int i = 0; i < MAX_POOL_PROJETEIS; i++) {
            poolProjeteis.add(new Projetil());
        }
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

    public void setForcarAlvosBenchmark(int quantidade) {
        this.forcarAlvos = quantidade;
    }

    /**
     * Registra o tempo de processamento de um ciclo (AV4).
     */
    public void registrarTempoCiclo(long nanos) {
        totalCycleTime += nanos;
        cycleCount++;

        long agora = System.currentTimeMillis();
        if (agora - lastMetricsLogTime >= 5000) { // Log a cada 5 segundos
            if (cycleCount > 0) {
                double avgTimeMs = (totalCycleTime / (double) cycleCount) / 1_000_000.0;
                
                // Cálculo aproximado de Uso de CPU (%)
                // Baseado no tempo de processamento vs intervalo de frame (30ms)
                double cpuUsage = (avgTimeMs / 30.0) * 100.0;
                
                Log.i("AV4_METRICS", String.format(java.util.Locale.US,
                        "[Modo: %s] | [Núcleos: %d] | [Alvos: %d] | [Tempo Médio do Ciclo: %.4f ms] | [Uso de CPU: %.2f %%]",
                        USE_THREAD_POOL ? "Pool" : "Legado",
                        ConcurrencyManager.getCurrentCores(),
                        alvos.size(),
                        avgTimeMs,
                        Math.min(100.0, cpuUsage)));
            }
            // Reset para o próximo intervalo
            totalCycleTime = 0;
            cycleCount = 0;
            lastMetricsLogTime = agora;
        }
    }
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
        // ... (lógica anterior de validação)
        int campo = (x < screenWidth / 2.0) ? 0 : 1;

        if (x < 0 || x > screenWidth || y < 0 || y > screenHeight) {
            throw new JogoException("Posição do canhão fora dos limites da tela!");
        }

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

        int totalCanhoesNoCampo = canhoesNoCampo.size() + 1;
        for (Canhao c : canhoes) {
            if (c.getCampo() == campo) {
                c.atualizarPenalidade(totalCanhoesNoCampo);
            }
        }

        if (rodando) {
            // AV4: Modo Híbrido (Pool vs Threads Soltas)
            if (USE_THREAD_POOL && gamePool != null) {
                gamePool.scheduleWithFixedDelay(canhao, 0, 30, TimeUnit.MILLISECONDS);
            } else {
                // Motor antigo para comparação (AV4 Benchmark)
                new Thread(() -> {
                    while (canhao.isAtivo() && rodando) {
                        canhao.run();
                        try { Thread.sleep(30); } catch (InterruptedException ignored) {}
                    }
                }, "Thread-Legacy-Canhao").start();
            }
        }
    }

    /**
     * Obtém um projétil do pool (Object Pooling AV4).
     */
    public void dispararProjetil(double startX, double startY, double dx, double dy, int campo) {
        synchronized (poolProjeteis) {
            for (Projetil p : poolProjeteis) {
                if (!p.isAtivo()) {
                    p.init(startX, startY, dx, dy, this, campo);
                    projeteis.add(p);
                    
                    if (USE_THREAD_POOL && gamePool != null) {
                        gamePool.scheduleWithFixedDelay(p, 0, 20, TimeUnit.MILLISECONDS);
                    } else {
                        // Motor antigo (AV4 Benchmark)
                        new Thread(() -> {
                            while (p.isAtivo() && rodando) {
                                p.run();
                                try { Thread.sleep(20); } catch (InterruptedException ignored) {}
                            }
                        }, "Thread-Legacy-Projetil").start();
                    }
                    return;
                }
            }
        }
    }

    public void adicionarProjetil(Projetil p) {
        // Método mantido para compatibilidade, mas preferir dispararProjetil
        if (!projeteis.contains(p)) projeteis.add(p);
    }

    public void removerProjetil(Projetil p) {
        p.setAtivo(false);
        projeteis.remove(p);
    }

    /**
     * Remove o canhão mais antigo de um campo (AV2 - Otimização Autônoma).
     */
    public void removerCanhaoMaisAntigo(int campo) {
        for (Canhao c : canhoes) {
            if (c.getCampo() == campo) {
                c.setAtivo(false);
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
            
            // Inicializa pool apenas se configurado
            if (USE_THREAD_POOL) {
                gamePool = new ScheduledThreadPoolExecutor(16);
            }
            
            abatesEsquerda.set(0);
            abatesDireita.set(0);
            energiaEsquerda.set(ENERGIA_MAXIMA);
            energiaDireita.set(ENERGIA_MAXIMA);
            tempoInicio = System.currentTimeMillis();

            // Limpar entidades antigas
            for (Alvo a : alvos) {
                a.setAtivo(false);
            }
            for (Projetil p : projeteis) {
                p.setAtivo(false);
            }
            for (Canhao c : canhoes) {
                c.setAtivo(false);
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
                    try {
                        alvos.removeIf(a -> !a.isAtivo());

                        int alvosEsq = 0, alvosDir = 0;
                        for (Alvo a : alvos) {
                            if (a.getCampo() == 0) alvosEsq++;
                            else alvosDir++;
                        }

                        int metaAlvos = (forcarAlvos > 0) ? (forcarAlvos / 2) : ALVOS_POR_CAMPO;

                        while (rodando && alvosEsq < metaAlvos) {
                            criarAlvoNoCampo(r, 0);
                            alvosEsq++;
                            if (!USE_THREAD_POOL) Thread.sleep(20); // Pequeno delay no modo legado para evitar thread-storm
                        }
                        while (rodando && alvosDir < metaAlvos) {
                            criarAlvoNoCampo(r, 1);
                            alvosDir++;
                            if (!USE_THREAD_POOL) Thread.sleep(20);
                        }

                        Thread.sleep(1500);
                    } catch (InterruptedException e) {
                        break;
                    } catch (Exception e) {
                        Log.e(TAG, "Erro no Gerenciador de Alvos: " + e.getMessage());
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

            // Thread CPS: Telemetria e Controlo Térmico (AV3)
            threadCPS = new Thread(() -> {
                while (rodando) {
                    try {
                        Thread.sleep(10000); // Executa a cada 10 segundos
                        executarRotinaCPS();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }, "Thread-CPS");
            threadCPS.setDaemon(true);
            threadCPS.start();

            Log.d(TAG, "Jogo iniciado! Duração: " + DURACAO_JOGO_SEGUNDOS + "s");
        }
    }

    /**
     * Rotina de Sistema Ciberfísico: Sensor de Temperatura + Feedback de Controlo (AV3).
     */
    private void executarRotinaCPS() {
        // 1. Simulação de Leitura de Temperatura (30ºC a 50ºC)
        double temp = 30.0 + (new Random().nextDouble() * 20.0);
        Log.d(TAG, String.format(java.util.Locale.US, "CPS: Temperatura detetada: %.1fºC", temp));

        // 2. Persistência Assíncrona no Firebase
        String uid = SessionManager.getUserUid();
        if (uid != null) {
            repository.saveTelemetry(new ThermalReading(uid, temp));
        }

        // 3. Feedback de Controlo em Tempo Real (Malha Fechada)
        boolean superaquecido = (temp > 40.0);
        
        // Atua sobre as threads dos canhões
        for (Canhao c : canhoes) {
            c.setSuperaquecido(superaquecido);
        }

        // Notificação visual via Listener (UI Thread)
        if (superaquecido && listener != null) {
            Log.w(TAG, "ALERTA TÉRMICO: Sistema em modo de arrefecimento!");
            // Notificamos a UI através de um mecanismo de mensagem se necessário
        }
    }

    /**
     * Coleta leituras ruidosas de todos os alvos ativos (AV2).
     */
    private void coletarLeiturasSensores() {
        for (Alvo alvo : alvos) {
            if (!alvo.isAtivo()) continue;
            
            // AV4: As atualizações podem ser distribuídas no pool de concorrência controlado
            ConcurrencyManager.execute(() -> {
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
            });
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
            
            // AV4: Submete ao pool ou cria thread manual (Modo Híbrido)
            if (USE_THREAD_POOL && gamePool != null) {
                gamePool.scheduleWithFixedDelay(alvo, 0, 30, TimeUnit.MILLISECONDS);
            } else {
                new Thread(() -> {
                    while (alvo.isAtivo() && rodando) {
                        alvo.run();
                        try { Thread.sleep(30); } catch (InterruptedException ignored) {}
                    }
                }, "Thread-Legacy-Alvo").start();
            }
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
        
        // AV4: Submete ao pool em vez de .start()
        gamePool.scheduleWithFixedDelay(alvo, 0, 30, TimeUnit.MILLISECONDS);
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

        // AV3: Persistência de Dados Sensíveis e Encriptados
        persistirResultadoPartida(abatesE + abatesD);

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
     * Monta o objeto de resultado, encripta dados sensíveis e envia para o repositório (AV3).
     */
    private void persistirResultadoPartida(int totalAbates) {
        String uid = SessionManager.getUserUid();
        if (uid == null) return;

        int finalScore = totalAbates * 10;

        // 1. Montagem dos dados sensíveis em JSON
        String sensitiveJson = String.format(java.util.Locale.US,
                "{\"user\":\"%s\", \"score\":%d}", SessionManager.getUserEmail(), finalScore);

        // 2. Encriptação AES-256
        String encrypted = Cryptography.encrypt(sensitiveJson);

        // 3. Criação do modelo para o Firestore
        MatchResult result = new MatchResult(
                uid,
                encrypted,
                totalAbates,
                getCanhoes().size()
        );

        // 4. Envio inteligente (Só grava se for o novo High Score)
        repository.updateHighScoreIfBetter(result, finalScore);
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
        if (threadCPS != null) threadCPS.interrupt();

        // AV4: Desativa todas as entidades
        for (Alvo a : alvos) a.setAtivo(false);
        for (Canhao c : canhoes) c.setAtivo(false);
        for (Projetil p : projeteis) p.setAtivo(false);

        if (reconciliacao != null) {
            reconciliacao.setAtivo(false);
            reconciliacao.interrupt();
        }
        
        if (gamePool != null) {
            gamePool.shutdownNow();
            try {
                if (!gamePool.awaitTermination(200, TimeUnit.MILLISECONDS)) {
                    gamePool.shutdownNow();
                }
            } catch (InterruptedException e) {
                gamePool.shutdownNow();
            }
            gamePool = null;
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

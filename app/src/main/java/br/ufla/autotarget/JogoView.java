package br.ufla.autotarget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Locale;

/**
 * View principal do jogo que renderiza o canvas com alvos, canhões e projéteis.
 * Usa SurfaceView para renderização eficiente em thread separada.
 * Exibe HUD com placar, timer, energia e linha divisória dos campos.
 */
public class JogoView extends SurfaceView implements SurfaceHolder.Callback {
    private static final String TAG = "JogoView";
    private Jogo jogo;
    private RenderThread renderThread;
    private boolean modoAdicionarCanhao = false;

    // Paints para elementos do jogo
    private Paint paintAlvoComum;
    private Paint paintAlvoRapido;
    private Paint paintAlvoComumBorda;
    private Paint paintAlvoRapidoBorda;
    private Paint paintGlowComum;
    private Paint paintGlowRapido;
    private Paint paintCanhaoEsq;
    private Paint paintCanhaoDir;
    private Paint paintCanhaoBorda;
    private Paint paintProjetil;
    private Paint paintProjetilGlow;
    private Paint paintLinhaDivisoria;
    private Paint paintFundoEsq;
    private Paint paintFundoDir;

    // Paints para HUD
    private Paint paintTextoHUD;
    private Paint paintTextoTimer;
    private Paint paintTextoTitulo;
    private Paint paintTextoEstado;
    private Paint paintBarraEnergia;
    private Paint paintBarraEnergiaFundo;
    private Paint paintHudFundo;
    private Paint paintResultado;
    private Paint paintCenterDot;
    private final Paint paintOverlay = new Paint();

    // Cores do tema
    private static final int COR_FUNDO_ESQ = Color.parseColor("#1B2838");
    private static final int COR_FUNDO_DIR = Color.parseColor("#1E3A5F");
    private static final int COR_ALVO_COMUM = Color.parseColor("#4FC3F7");
    private static final int COR_ALVO_RAPIDO = Color.parseColor("#FF7043");
    private static final int COR_CANHAO_ESQ = Color.parseColor("#66BB6A");
    private static final int COR_CANHAO_DIR = Color.parseColor("#AB47BC");
    private static final int COR_PROJETIL = Color.parseColor("#FFEE58");
    private static final int COR_DIVISORIA = Color.parseColor("#55FFFFFF");
    private static final int COR_ENERGIA = Color.parseColor("#76FF03");
    private static final int COR_ENERGIA_BAIXA = Color.parseColor("#FF1744");
    private static final int COR_HUD_FUNDO = Color.parseColor("#CC1a1a2e");
    private static final int COR_TEXTO = Color.WHITE;

    // Paints reutilizados para evitar pressão no GC
    private final Paint paintFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintGlow = new Paint(Paint.ANTI_ALIAS_FLAG);

    public JogoView(Context context, AttributeSet attrs) {
        super(context, attrs);
        getHolder().addCallback(this);
        setFocusable(true);
        paintStroke.setStyle(Paint.Style.STROKE);
        paintStroke.setStrokeWidth(3f);

        initPaints();
        Log.d(TAG, "JogoView construído");
    }

    private void initPaints() {
        // Fundos
        paintFundoEsq = new Paint();
        paintFundoEsq.setColor(COR_FUNDO_ESQ);
        paintFundoEsq.setStyle(Paint.Style.FILL);

        paintFundoDir = new Paint();
        paintFundoDir.setColor(COR_FUNDO_DIR);
        paintFundoDir.setStyle(Paint.Style.FILL);

        // Alvos comuns (azul claro)
        paintAlvoComum = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintAlvoComum.setColor(COR_ALVO_COMUM);
        paintAlvoComum.setStyle(Paint.Style.FILL);

        paintAlvoComumBorda = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintAlvoComumBorda.setColor(Color.parseColor("#B3E5FC"));
        paintAlvoComumBorda.setStyle(Paint.Style.STROKE);
        paintAlvoComumBorda.setStrokeWidth(3f);

        // Alvos rápidos (laranja)
        paintAlvoRapido = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintAlvoRapido.setColor(COR_ALVO_RAPIDO);
        paintAlvoRapido.setStyle(Paint.Style.FILL);

        paintAlvoRapidoBorda = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintAlvoRapidoBorda.setColor(Color.parseColor("#FFCCBC"));
        paintAlvoRapidoBorda.setStyle(Paint.Style.STROKE);
        paintAlvoRapidoBorda.setStrokeWidth(3f);

        // Glows (Pré-alocados para evitar pressão no GC)
        paintGlowComum = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintGlowComum.setColor(COR_ALVO_COMUM);
        paintGlowComum.setAlpha(40);
        paintGlowComum.setStyle(Paint.Style.FILL);

        paintGlowRapido = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintGlowRapido.setColor(COR_ALVO_RAPIDO);
        paintGlowRapido.setAlpha(60);
        paintGlowRapido.setStyle(Paint.Style.FILL);

        // Canhões
        paintCanhaoEsq = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintCanhaoEsq.setColor(COR_CANHAO_ESQ);
        paintCanhaoEsq.setStyle(Paint.Style.FILL);

        paintCanhaoDir = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintCanhaoDir.setColor(COR_CANHAO_DIR);
        paintCanhaoDir.setStyle(Paint.Style.FILL);

        paintCanhaoBorda = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintCanhaoBorda.setColor(Color.WHITE);
        paintCanhaoBorda.setStyle(Paint.Style.STROKE);
        paintCanhaoBorda.setStrokeWidth(2f);

        // Projéteis
        paintProjetil = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintProjetil.setColor(COR_PROJETIL);
        paintProjetil.setStyle(Paint.Style.FILL);

        paintProjetilGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintProjetilGlow.setColor(Color.parseColor("#80FFEE58"));
        paintProjetilGlow.setStyle(Paint.Style.FILL);

        // Linha divisória
        paintLinhaDivisoria = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintLinhaDivisoria.setColor(COR_DIVISORIA);
        paintLinhaDivisoria.setStrokeWidth(3f);
        paintLinhaDivisoria.setStyle(Paint.Style.STROKE);

        // HUD
        paintTextoHUD = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintTextoHUD.setColor(COR_TEXTO);
        paintTextoHUD.setTextSize(40);
        paintTextoHUD.setTypeface(Typeface.DEFAULT_BOLD);

        paintTextoTimer = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintTextoTimer.setColor(COR_TEXTO);
        paintTextoTimer.setTextSize(56);
        paintTextoTimer.setTypeface(Typeface.MONOSPACE);
        paintTextoTimer.setTextAlign(Paint.Align.CENTER);

        paintTextoTitulo = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintTextoTitulo.setColor(Color.parseColor("#B0BEC5"));
        paintTextoTitulo.setTextSize(26);

        paintTextoEstado = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintTextoEstado.setColor(Color.parseColor("#80FFFFFF"));
        paintTextoEstado.setTextSize(42);
        paintTextoEstado.setTextAlign(Paint.Align.CENTER);
        paintTextoEstado.setTypeface(Typeface.DEFAULT_BOLD);

        paintBarraEnergia = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintBarraEnergia.setStyle(Paint.Style.FILL);

        paintBarraEnergiaFundo = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintBarraEnergiaFundo.setColor(Color.parseColor("#40FFFFFF"));
        paintBarraEnergiaFundo.setStyle(Paint.Style.FILL);

        paintHudFundo = new Paint();
        paintHudFundo.setColor(COR_HUD_FUNDO);
        paintHudFundo.setStyle(Paint.Style.FILL);

        paintResultado = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintResultado.setColor(COR_TEXTO);
        paintResultado.setTextSize(64);
        paintResultado.setTypeface(Typeface.DEFAULT_BOLD);
        paintResultado.setTextAlign(Paint.Align.CENTER);

        paintCenterDot = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintCenterDot.setColor(Color.WHITE);
        paintCenterDot.setAlpha(180);

        // Inicializa paints globais de desenho rápido
        paintStroke.setStyle(Paint.Style.STROKE);
        paintStroke.setStrokeWidth(3f);
    }

    public void setModoAdicionarCanhao(boolean modo) {
        this.modoAdicionarCanhao = modo;
    }

    public Jogo getJogo() {
        return jogo;
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        Log.d(TAG, "surfaceCreated: width=" + getWidth() + " height=" + getHeight());
        if (jogo == null) {
            jogo = new Jogo(getWidth(), getHeight());
        }
        renderThread = new RenderThread(getHolder(), this);
        renderThread.setRunning(true);
        renderThread.start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.d(TAG, "surfaceChanged: width=" + width + " height=" + height);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.d(TAG, "surfaceDestroyed");
        boolean retry = true;
        if (renderThread != null) {
            renderThread.setRunning(false);
            while (retry) {
                try {
                    renderThread.join();
                    retry = false;
                } catch (InterruptedException e) {
                    // Tenta novamente
                }
            }
        }
        if (jogo != null) {
            jogo.pararJogo();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            performClick();
            if (modoAdicionarCanhao && jogo != null) {
                try {
                    jogo.adicionarCanhao(event.getX(), event.getY());
                    modoAdicionarCanhao = false;
                } catch (JogoException e) {
                    // Feedback ao usuário em caso de erro na regra de negócio
                    Toast.makeText(getContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    /**
     * Renderiza todos os elementos do jogo no canvas.
     */
    public void drawElements(Canvas canvas) {
        if (canvas == null) return;

        int width = canvas.getWidth();
        int height = canvas.getHeight();

        if (width <= 0 || height <= 0) return;

        int metade = width / 2;

        // === FUNDO SÓLIDO (dois campos) ===
        canvas.drawRect(0, 0, metade, height, paintFundoEsq);
        canvas.drawRect(metade, 0, width, height, paintFundoDir);

        if (jogo == null) {
            // Ainda não inicializou, mostra mensagem
            canvas.drawText("Carregando...", metade, height / 2f, paintTextoEstado);
            return;
        }

        // === LINHA DIVISÓRIA ===
        float dashLength = 20f;
        float gapLength = 15f;
        for (float y = 90; y < height; y += dashLength + gapLength) {
            float endY = Math.min(y + dashLength, height);
            canvas.drawLine(metade, y, metade, endY, paintLinhaDivisoria);
        }

        // === HUD SUPERIOR ===
        drawHUD(canvas, width);

        // === ALVOS (polimorfismo) ===
        drawAlvos(canvas);

        // === CANHÕES (triângulos) ===
        drawCanhoes(canvas);

        // === PROJÉTEIS ===
        drawProjeteis(canvas);

        // === MENSAGEM DE ESTADO ===
        if (!jogo.isRodando() && !jogo.isEncerrado()) {
            // Jogo parado, mostra instrução
            canvas.drawText("Adicione canhões e pressione Iniciar",
                    metade, height / 2f, paintTextoEstado);
        }

        // === TELA DE RESULTADO ===
        if (jogo.isEncerrado()) {
            drawResultado(canvas, width, height);
        }
    }

    private void drawHUD(Canvas canvas, int width) {
        int metade = width / 2;

        // Fundo do HUD
        canvas.drawRect(0, 0, width, 85, paintHudFundo);

        // === Campo Esquerdo ===
        paintTextoTitulo.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("ESQUERDA", 15, 25, paintTextoTitulo);

        paintTextoHUD.setTextAlign(Paint.Align.LEFT);
        paintTextoHUD.setColor(COR_CANHAO_ESQ);
        canvas.drawText("Abates: " + jogo.getAbatesEsquerda(), 15, 58, paintTextoHUD);

        // Barra de energia esquerda
        drawBarraEnergia(canvas, 15, 66, metade - 60, 80,
                jogo.getEnergiaEsquerda(), 100.0);

        // Canhões ativos
        int canhoesEsq = jogo.getCanhoesPorCampo(0).size();
        paintTextoTitulo.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("C:" + canhoesEsq, metade - 55, 78, paintTextoTitulo);

        // === Timer Central ===
        int tempoRestante = jogo.getTempoRestante();
        if (tempoRestante <= 10 && jogo.isRodando()) {
            paintTextoTimer.setColor(COR_ENERGIA_BAIXA);
        } else {
            paintTextoTimer.setColor(COR_TEXTO);
        }
        canvas.drawText(String.format(Locale.getDefault(), "%02d", tempoRestante), metade, 60, paintTextoTimer);

        // === Campo Direito ===
        paintTextoTitulo.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("DIREITA", width - 15, 25, paintTextoTitulo);

        paintTextoHUD.setTextAlign(Paint.Align.RIGHT);
        paintTextoHUD.setColor(COR_CANHAO_DIR);
        canvas.drawText("Abates: " + jogo.getAbatesDireita(), width - 15, 58, paintTextoHUD);

        // Barra de energia direita
        drawBarraEnergia(canvas, metade + 60, 66, width - 15, 80,
                jogo.getEnergiaDireita(), 100.0);

        // Canhões ativos
        int canhoesDir = jogo.getCanhoesPorCampo(1).size();
        paintTextoTitulo.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("C:" + canhoesDir, metade + 55, 78, paintTextoTitulo);
    }

    private void drawBarraEnergia(Canvas canvas, float left, float top,
                                   float right, float bottom,
                                   double valor, double maximo) {
        if (right <= left) return; // Segurança

        // Fundo da barra
        RectF fundoRect = new RectF(left, top, right, bottom);
        canvas.drawRoundRect(fundoRect, 4, 4, paintBarraEnergiaFundo);

        // Barra preenchida
        float porcentagem = (float) Math.max(0, Math.min(1, valor / maximo));
        float larguraPreenchida = left + (right - left) * porcentagem;

        if (porcentagem < 0.25f) {
            paintBarraEnergia.setColor(COR_ENERGIA_BAIXA);
        } else if (porcentagem < 0.5f) {
            paintBarraEnergia.setColor(Color.parseColor("#FFA726"));
        } else {
            paintBarraEnergia.setColor(COR_ENERGIA);
        }

        if (larguraPreenchida > left) {
            RectF barraRect = new RectF(left, top, larguraPreenchida, bottom);
            canvas.drawRoundRect(barraRect, 4, 4, paintBarraEnergia);
        }
    }

    private void drawAlvos(Canvas canvas) {
        List<Alvo> alvos = jogo.getAlvos();
        
        for (Alvo alvo : alvos) {
            if (!alvo.isAtivo()) continue;

            float ax = (float) alvo.getX();
            float ay = (float) alvo.getY();
            float ar = (float) alvo.getRaio();

            // Renderização Polimórfica (Princípio Aberto/Fechado)
            int cor = alvo.getCor();
            
            // Configura Paints com os valores do alvo específico
            paintGlow.setColor(cor);
            paintGlow.setAlpha(alvo.getGlowAlpha());
            
            paintFill.setColor(cor);
            
            paintStroke.setColor(alvo.getCorBorda());

            // Desenho
            canvas.drawCircle(ax, ay, ar + 7, paintGlow);
            canvas.drawCircle(ax, ay, ar, paintFill);
            canvas.drawCircle(ax, ay, ar, paintStroke);
        }
    }

    private void drawCanhoes(Canvas canvas) {
        List<Canhao> canhoes = jogo.getCanhoes();
        for (Canhao c : canhoes) {
            float cx = (float) c.getX();
            float cy = (float) c.getY();
            
            // Reaproveita paints globais
            int corCanhao = (c.getCampo() == 0) ? COR_CANHAO_ESQ : COR_CANHAO_DIR;
            paintFill.setColor(corCanhao);

            // Base do canhão (círculo)
            canvas.drawCircle(cx, cy + 15, 12, paintFill);

            // Corpo do canhão (triângulo)
            Path path = new Path();
            path.moveTo(cx, cy - 30);       // Topo
            path.lineTo(cx - 22, cy + 20);  // Esquerda
            path.lineTo(cx + 22, cy + 20);  // Direita
            path.close();
            canvas.drawPath(path, paintFill);
            
            paintStroke.setColor(Color.WHITE);
            paintStroke.setStrokeWidth(2f);
            canvas.drawPath(path, paintStroke);

            // Centro do canhão
            canvas.drawCircle(cx, cy, 5, paintCenterDot);
        }
    }

    private void drawProjeteis(Canvas canvas) {
        List<Projetil> projeteis = jogo.getProjeteis();
        for (Projetil p : projeteis) {
            if (!p.isAtivo()) continue;
            float px = (float) p.getX();
            float py = (float) p.getY();

            // Reaproveita paints globais para os projéteis
            paintGlow.setColor(COR_PROJETIL);
            paintGlow.setAlpha(128);
            canvas.drawCircle(px, py, 10f, paintGlow);
            
            paintFill.setColor(COR_PROJETIL);
            canvas.drawCircle(px, py, 5f, paintFill);
        }
    }

    private void drawResultado(Canvas canvas, int width, int height) {
        // Overlay semi-transparente (Zero alocação no loop)
        paintOverlay.setColor(Color.parseColor("#CC000000"));
        canvas.drawRect(0, 0, width, height, paintOverlay);

        int centerX = width / 2;
        int centerY = height / 2;

        // Título
        paintResultado.setTextSize(60);
        paintResultado.setColor(COR_PROJETIL);
        canvas.drawText("JOGO ENCERRADO!", centerX, centerY - 120, paintResultado);

        // Placar
        paintResultado.setTextSize(48);
        paintResultado.setColor(COR_CANHAO_ESQ);
        canvas.drawText("Esquerda: " + jogo.getAbatesEsquerda(), centerX, centerY - 30, paintResultado);

        paintResultado.setColor(COR_CANHAO_DIR);
        canvas.drawText("Direita: " + jogo.getAbatesDireita(), centerX, centerY + 40, paintResultado);

        // Vencedor
        paintResultado.setTextSize(52);
        String vencedor = jogo.getVencedor();
        if ("ESQUERDA".equals(vencedor)) {
            paintResultado.setColor(COR_CANHAO_ESQ);
        } else if ("DIREITA".equals(vencedor)) {
            paintResultado.setColor(COR_CANHAO_DIR);
        } else {
            paintResultado.setColor(COR_TEXTO);
        }
        canvas.drawText("Vencedor: " + vencedor, centerX, centerY + 130, paintResultado);
    }

    // ==================== Render Thread ====================

    /**
     * Thread de renderização que roda o loop de desenho a ~60 FPS.
     */
    static class RenderThread extends Thread {
        private final SurfaceHolder surfaceHolder;
        private final JogoView jogoView;
        private volatile boolean running = false;

        public RenderThread(SurfaceHolder surfaceHolder, JogoView jogoView) {
            this.surfaceHolder = surfaceHolder;
            this.jogoView = jogoView;
            setName("Thread-Render");
        }

        public void setRunning(boolean running) {
            this.running = running;
        }

        @Override
        public void run() {
            Log.d(TAG, "RenderThread iniciada");
            while (running) {
                Canvas canvas = null;
                try {
                    canvas = surfaceHolder.lockCanvas();
                    if (canvas != null) {
                        synchronized (surfaceHolder) {
                            jogoView.drawElements(canvas);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Erro ao renderizar: " + e.getMessage(), e);
                } finally {
                    if (canvas != null) {
                        try {
                            surfaceHolder.unlockCanvasAndPost(canvas);
                        } catch (Exception e) {
                            Log.e(TAG, "Erro ao postar canvas: " + e.getMessage());
                        }
                    }
                }
                try {
                    Thread.sleep(30); // Reduzido para ~33 FPS para estabilizar emuladores lentos
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            Log.d(TAG, "RenderThread encerrada");
        }
    }
}

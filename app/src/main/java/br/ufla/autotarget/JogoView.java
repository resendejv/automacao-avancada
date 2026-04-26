package br.ufla.autotarget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;

import java.util.List;

public class JogoView extends SurfaceView implements SurfaceHolder.Callback {
    private Jogo jogo;
    private RenderThread renderThread;
    private Paint paintAlvoComum;
    private Paint paintAlvoRapido;
    private Paint paintCanhao;
    private Paint paintProjetil;
    private Paint paintTexto;
    private boolean modoAdicionarCanhao = false;

    public JogoView(Context context, AttributeSet attrs) {
        super(context, attrs);
        getHolder().addCallback(this);

        paintAlvoComum = new Paint();
        paintAlvoComum.setColor(Color.BLUE);
        paintAlvoComum.setStyle(Paint.Style.FILL);

        paintAlvoRapido = new Paint();
        paintAlvoRapido.setColor(Color.RED);
        paintAlvoRapido.setStyle(Paint.Style.FILL);

        paintCanhao = new Paint();
        paintCanhao.setColor(Color.DKGRAY);
        paintCanhao.setStyle(Paint.Style.FILL);

        paintProjetil = new Paint();
        paintProjetil.setColor(Color.BLACK);
        paintProjetil.setStyle(Paint.Style.FILL);

        paintTexto = new Paint();
        paintTexto.setColor(Color.BLACK);
        paintTexto.setTextSize(50);
    }

    public void setModoAdicionarCanhao(boolean modo) {
        this.modoAdicionarCanhao = modo;
    }

    public Jogo getJogo() {
        return jogo;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (jogo == null) {
            jogo = new Jogo(getWidth(), getHeight());
        }
        renderThread = new RenderThread(getHolder(), this);
        renderThread.setRunning(true);
        renderThread.start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        boolean retry = true;
        renderThread.setRunning(false);
        while (retry) {
            try {
                renderThread.join();
                retry = false;
            } catch (InterruptedException e) {
                // Tenta novamente
            }
        }
        if (jogo != null) {
            jogo.pararJogo();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (modoAdicionarCanhao && jogo != null) {
                try {
                    jogo.adicionarCanhao(event.getX(), event.getY());
                    modoAdicionarCanhao = false; // Reset mode after adding one
                    Toast.makeText(getContext(), "Canhão adicionado!", Toast.LENGTH_SHORT).show();
                } catch (JogoException e) {
                    Toast.makeText(getContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }
        return true;
    }

    public void drawElements(Canvas canvas) {
        if (canvas == null || jogo == null) return;
        
        canvas.drawColor(Color.WHITE); // Fundo limpo

        // Desenha abates
        canvas.drawText("Abates: " + jogo.getAbates(), 50, 100, paintTexto);

        // Desenha alvos polimorficamente
        List<Alvo> alvos = jogo.getAlvos();
        for (Alvo alvo : alvos) {
            if (alvo instanceof AlvoRapido) {
                canvas.drawCircle((float)alvo.getX(), (float)alvo.getY(), (float)alvo.getRaio(), paintAlvoRapido);
            } else {
                canvas.drawCircle((float)alvo.getX(), (float)alvo.getY(), (float)alvo.getRaio(), paintAlvoComum);
            }
        }

        // Desenha canhões (triângulos)
        List<Canhao> canhoes = jogo.getCanhoes();
        for (Canhao c : canhoes) {
            Path path = new Path();
            path.moveTo((float)c.getX(), (float)c.getY() - 30);
            path.lineTo((float)c.getX() - 30, (float)c.getY() + 30);
            path.lineTo((float)c.getX() + 30, (float)c.getY() + 30);
            path.close();
            canvas.drawPath(path, paintCanhao);
        }

        // Desenha projéteis
        List<Projetil> projeteis = jogo.getProjeteis();
        for (Projetil p : projeteis) {
            canvas.drawCircle((float)p.getX(), (float)p.getY(), 5f, paintProjetil);
        }
    }

    class RenderThread extends Thread {
        private SurfaceHolder surfaceHolder;
        private JogoView jogoView;
        private boolean running = false;

        public RenderThread(SurfaceHolder surfaceHolder, JogoView jogoView) {
            this.surfaceHolder = surfaceHolder;
            this.jogoView = jogoView;
        }

        public void setRunning(boolean running) {
            this.running = running;
        }

        @Override
        public void run() {
            while (running) {
                Canvas canvas = null;
                try {
                    canvas = surfaceHolder.lockCanvas();
                    synchronized (surfaceHolder) {
                        jogoView.drawElements(canvas);
                    }
                } finally {
                    if (canvas != null) {
                        surfaceHolder.unlockCanvasAndPost(canvas);
                    }
                }
                try {
                    Thread.sleep(16); // ~60 FPS
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}

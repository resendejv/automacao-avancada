package br.ufla.autotarget;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

/**
 * Activity principal do jogo AutoTarget.
 * Contém a JogoView (canvas) e os botões de controle.
 * Implementa JogoListener para receber eventos do jogo (fim de partida).
 */
public class MainActivity extends AppCompatActivity implements Jogo.JogoListener {
    private static final String TAG = "MainActivity";

    private JogoView jogoView;
    private Button btnIniciar;
    private Handler uiHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        setContentView(R.layout.activity_main);

        // Handler para operações na UI thread
        uiHandler = new Handler(Looper.getMainLooper());

        // Referências às views
        jogoView = findViewById(R.id.jogoView);
        btnIniciar = findViewById(R.id.btnIniciar);
        Button btnAdicionarCanhao = findViewById(R.id.btnAdicionarCanhao);

        // === Botão Iniciar ===
        btnIniciar.setOnClickListener(v -> {
            Jogo jogo = jogoView.getJogo();
            if (jogo != null) {
                if (jogo.isRodando()) {
                    jogo.pararJogo();
                    btnIniciar.setText(R.string.btn_iniciar);
                } else {
                    jogo.setListener(this);
                    jogo.iniciarJogo();
                    btnIniciar.setText(R.string.btn_parar);
                }
            }
        });

        // === Botão Adicionar Canhão ===
        btnAdicionarCanhao.setOnClickListener(v -> {
            jogoView.setModoAdicionarCanhao(true);
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
        if (jogoView != null && jogoView.getJogo() != null) {
            // Interrompe o jogo para economizar recursos em segundo plano
            jogoView.getJogo().pararJogo();
            btnIniciar.setText(R.string.btn_iniciar);
        }
    }

    /**
     * Callback chamado quando o jogo encerra (timer zerou).
     */
    @Override
    public void onJogoEncerrado(String vencedor, int abatesEsquerda, int abatesDireita) {
        Log.d(TAG, "Jogo encerrado! Vencedor: " + vencedor);

        // Atualiza UI na main thread
        uiHandler.post(() -> {
            // O jogo reinicia automaticamente no Jogo.java
            btnIniciar.setText(R.string.btn_parar);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        if (jogoView != null && jogoView.getJogo() != null) {
            jogoView.getJogo().pararJogo();
        }
    }
}

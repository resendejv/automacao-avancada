package br.ufla.autotarget;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private JogoView jogoView;
    private Button btnIniciar;
    private Button btnAdicionarCanhao;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        jogoView = findViewById(R.id.jogoView);
        btnIniciar = findViewById(R.id.btnIniciar);
        btnAdicionarCanhao = findViewById(R.id.btnAdicionarCanhao);

        btnIniciar.setOnClickListener(v -> {
            if (jogoView.getJogo() != null) {
                jogoView.getJogo().iniciarJogo();
                Toast.makeText(this, "Jogo Iniciado!", Toast.LENGTH_SHORT).show();
            }
        });

        btnAdicionarCanhao.setOnClickListener(v -> {
            jogoView.setModoAdicionarCanhao(true);
            Toast.makeText(this, "Clique na tela para adicionar um canhão", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (jogoView != null && jogoView.getJogo() != null) {
            jogoView.getJogo().pararJogo();
        }
    }
}

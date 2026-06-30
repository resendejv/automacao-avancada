package br.ufla.autotarget;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

/**
 * Activity de Autenticação (Login e Registo).
 */
public class LoginActivity extends AppCompatActivity {

    private EditText editEmail, editPassword;
    private Button btnLogin, btnRegister;
    private ProgressBar progressBar;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        try {
            mAuth = FirebaseAuth.getInstance();
        } catch (Exception e) {
            Log.e("LoginActivity", "Erro ao inicializar Firebase", e);
            Toast.makeText(this, "Erro crítico: Firebase não configurado", Toast.LENGTH_LONG).show();
        }

        // Se já estiver logado, vai direto para o jogo
        if (SessionManager.isUserLoggedIn()) {
            iniciarJogo();
        }

        editEmail = findViewById(R.id.editEmail);
        editPassword = findViewById(R.id.editPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnRegister = findViewById(R.id.btnRegister);
        progressBar = findViewById(R.id.progressBar);

        btnLogin.setOnClickListener(v -> login());
        btnRegister.setOnClickListener(v -> register());
    }

    private void login() {
        String email = editEmail.getText().toString().trim();
        String password = editPassword.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Preencha todos os campos", Toast.LENGTH_SHORT).show();
            return;
        }

        if (mAuth == null) {
            Toast.makeText(this, "Erro: Firebase não configurado.", Toast.LENGTH_LONG).show();
            return;
        }

        showLoading(true);
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    showLoading(false);
                    if (task.isSuccessful()) {
                        iniciarJogo();
                    } else {
                        String error = task.getException() != null ? task.getException().getMessage() : "Erro desconhecido";
                        Log.e("LoginActivity", "Erro no login", task.getException());
                        Toast.makeText(this, "Erro no login: " + error, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void register() {
        String email = editEmail.getText().toString().trim();
        String password = editPassword.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Preencha todos os campos", Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.length() < 6) {
            Toast.makeText(this, "A password deve ter pelo menos 6 caracteres", Toast.LENGTH_SHORT).show();
            return;
        }

        if (mAuth == null) {
            Toast.makeText(this, "Erro: Firebase não configurado. Verifique o google-services.json", Toast.LENGTH_LONG).show();
            return;
        }

        showLoading(true);
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    showLoading(false);
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Conta criada com sucesso!", Toast.LENGTH_SHORT).show();
                        iniciarJogo();
                    } else {
                        String error = task.getException() != null ? task.getException().getMessage() : "Erro desconhecido";
                        Log.e("LoginActivity", "Erro no registo", task.getException());
                        Toast.makeText(this, "Erro no registo: " + error, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void iniciarJogo() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!show);
        btnRegister.setEnabled(!show);
    }
}

package br.ufla.autotarget;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Utilitário de Sessão para gerenciar o estado de autenticação do Firebase.
 */
public class SessionManager {
    
    private static FirebaseAuth mAuth;

    static {
        try {
            mAuth = FirebaseAuth.getInstance();
        } catch (Exception e) {
            android.util.Log.e("SessionManager", "ERRO: Firebase Authentication falhou. Verifica o ficheiro google-services.json.");
        }
    }

    /**
     * Verifica se existe um utilizador logado.
     */
    public static boolean isUserLoggedIn() {
        return mAuth != null && mAuth.getCurrentUser() != null;
    }

    /**
     * Retorna o UID do utilizador logado ou null se não houver sessão.
     */
    public static String getUserUid() {
        if (mAuth == null) return "LOCAL_TEST_UID";
        FirebaseUser user = mAuth.getCurrentUser();
        return (user != null) ? user.getUid() : null;
    }

    /**
     * Retorna o e-mail do utilizador logado.
     */
    public static String getUserEmail() {
        FirebaseUser user = mAuth.getCurrentUser();
        return (user != null) ? user.getEmail() : null;
    }

    /**
     * Finaliza a sessão do utilizador.
     */
    public static void logout() {
        mAuth.signOut();
    }
}

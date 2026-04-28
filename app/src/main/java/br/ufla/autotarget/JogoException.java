package br.ufla.autotarget;

/**
 * Exceção personalizada do jogo AutoTarget.
 * Lançada em situações de erro como posições inválidas,
 * limite de canhões excedido, etc.
 */
public class JogoException extends Exception {

    public JogoException(String message) {
        super(message);
    }

    public JogoException(String message, Throwable cause) {
        super(message, cause);
    }
}

package com.ifpr.androidapptemplatear.ui

data class Personagem(
    val nome: String,
    val arquivoModelo: String,
    val vidaMaxima: Int = 100,
    val danoAtaque: Int = 20,
    var vidaAtual: Int = vidaMaxima
) {
    val estaDerrotado: Boolean
        get() = vidaAtual <= 0

    fun receberDano(dano: Int) {
        if (dano > 0 && !estaDerrotado) {
            vidaAtual = (vidaAtual - dano).coerceAtLeast(0)
        }
    }

    fun restaurarVida() {
        vidaAtual = vidaMaxima
    }
}

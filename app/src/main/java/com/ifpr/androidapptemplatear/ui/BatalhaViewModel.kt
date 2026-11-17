
package com.ifpr.androidapptemplatear.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlin.random.Random

data class BatalhaEstado(
    val personagem1: String = "",
    val personagem2: String = "",
    val vida1: Int = 100,
    val vida2: Int = 100,
    val turno: Int = 1,
    val batalhaFinalizada: Boolean = false,
    val vencedor: String? = null,
    val derrotado: String? = null,
    val mensagem: String = "Aguardando os personagens..."
)

class BatalhaViewModel : ViewModel() {

    companion object {
        const val VIDA_MAXIMA = 100
        const val DANO_ATAQUE = 20
    }

    private val _estado = MutableLiveData(BatalhaEstado())
    val estado: LiveData<BatalhaEstado> = _estado

    fun iniciarBatalha(
        arquivo1: String?,
        arquivo2: String?
    ) {
        if (arquivo1.isNullOrBlank() || arquivo2.isNullOrBlank()) {
            _estado.value = BatalhaEstado(
                mensagem = "Selecione os dois personagens."
            )
            return
        }

        if (arquivo1 == arquivo2) {
            _estado.value = BatalhaEstado(
                personagem1 = arquivo1,
                personagem2 = arquivo2,
                mensagem = "Escolha dois personagens diferentes."
            )
            return
        }

        val primeiroTurno = Random.nextInt(1, 3)

        val nomeInicial = if (primeiroTurno == 1) {
            nome(arquivo1)
        } else {
            nome(arquivo2)
        }

        _estado.value = BatalhaEstado(
            personagem1 = arquivo1,
            personagem2 = arquivo2,
            vida1 = VIDA_MAXIMA,
            vida2 = VIDA_MAXIMA,
            turno = primeiroTurno,
            mensagem = "A batalha começou! Primeiro turno: $nomeInicial."
        )
    }

    fun atacar() {
        val atual = _estado.value ?: return

        if (
            atual.batalhaFinalizada ||
            atual.personagem1.isBlank() ||
            atual.personagem2.isBlank()
        ) return

        if (atual.turno == 1) {

            val novaVida2 =
                (atual.vida2 - DANO_ATAQUE).coerceAtLeast(0)

            if (novaVida2 == 0) {
                _estado.value = atual.copy(
                    vida2 = 0,
                    batalhaFinalizada = true,
                    vencedor = nome(atual.personagem1),
                    derrotado = nome(atual.personagem2),
                    mensagem = "${nome(atual.personagem1)} venceu! " +
                            "${nome(atual.personagem2)} foi derrotado."
                )
            } else {
                _estado.value = atual.copy(
                    vida2 = novaVida2,
                    turno = 2,
                    mensagem = "Agora é a vez de ${nome(atual.personagem2)}."
                )
            }

        } else {

            val novaVida1 =
                (atual.vida1 - DANO_ATAQUE).coerceAtLeast(0)

            if (novaVida1 == 0) {
                _estado.value = atual.copy(
                    vida1 = 0,
                    batalhaFinalizada = true,
                    vencedor = nome(atual.personagem2),
                    derrotado = nome(atual.personagem1),
                    mensagem = "${nome(atual.personagem2)} venceu! " +
                            "${nome(atual.personagem1)} foi derrotado."
                )
            } else {
                _estado.value = atual.copy(
                    vida1 = novaVida1,
                    turno = 1,
                    mensagem = "Agora é a vez de ${nome(atual.personagem1)}."
                )
            }
        }
    }

    fun jogarNovamente() {
        val atual = _estado.value ?: return

        if (
            atual.personagem1.isBlank() ||
            atual.personagem2.isBlank()
        ) return

        val primeiroTurno = Random.nextInt(1, 3)

        val nomeInicial = if (primeiroTurno == 1) {
            nome(atual.personagem1)
        } else {
            nome(atual.personagem2)
        }

        _estado.value = BatalhaEstado(
            personagem1 = atual.personagem1,
            personagem2 = atual.personagem2,
            vida1 = VIDA_MAXIMA,
            vida2 = VIDA_MAXIMA,
            turno = primeiroTurno,
            mensagem = "Nova batalha! Primeiro turno: $nomeInicial."
        )
    }

    private fun nome(arquivo: String): String {
        return arquivo
            .substringAfterLast("/")
            .substringBeforeLast(".")
            .replace("_", " ")
            .replaceFirstChar { it.uppercase() }
    }
}

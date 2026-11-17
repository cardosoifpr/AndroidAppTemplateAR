package com.ifpr.androidapptemplatear.ui

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.ifpr.androidapptemplatear.R


class ChangeObjectFragment : Fragment() {

    private lateinit var sharedViewModel: SharedModelViewModel

    // 1 = escolhendo o primeiro personagem
    // 2 = escolhendo o segundo personagem
    private var personagemSelecionado = 1

    private var primeiro: String? = null
    private var segundo: String? = null

    private var selecaoText: TextView? = null
    private var iniciarButton: Button? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(
            R.layout.fragment_changeobject,
            container,
            false
        )
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        sharedViewModel = ViewModelProvider(requireActivity())
            .get(SharedModelViewModel::class.java)

        // Recupera as escolhas já feitas
        primeiro = sharedViewModel.personagem1.value
        segundo = sharedViewModel.personagem2.value

        // Continua a seleção a partir do personagem que falta
        personagemSelecionado = if (primeiro.isNullOrBlank()) 1 else 2

        carregarModelos(view)
    }

    private fun carregarModelos(view: View) {

        val categoryContainer =
            view.findViewById<LinearLayout>(R.id.categoryContainer)

        categoryContainer.removeAllViews()

        val titulo = TextView(requireContext()).apply {
            text = "ESCOLHA OS PERSONAGENS"
            textSize = 22f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(12, 24, 12, 24)
        }

        categoryContainer.addView(titulo)

        selecaoText = TextView(requireContext()).apply {
            text = textoSelecao()
            textSize = 17f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(12, 12, 12, 20)
        }

        categoryContainer.addView(selecaoText)

        val modelos = requireContext()
            .assets
            .list("")
            ?.filter {
                it.endsWith(".glb", ignoreCase = true)
            }
            ?.sorted()
            ?: emptyList()

        if (modelos.isEmpty()) {
            categoryContainer.addView(
                TextView(requireContext()).apply {
                    text = "Nenhum arquivo .glb foi encontrado em app/src/main/assets."
                    setTextColor(Color.RED)
                    gravity = Gravity.CENTER
                }
            )
        }

        modelos.forEach { arquivo ->

            val botao = Button(requireContext()).apply {

                text = arquivo
                    .removeSuffix(".glb")
                    .replace("_", " ")
                    .replaceFirstChar { it.uppercase() }

                setOnClickListener {
                    selecionarArquivo(arquivo)
                }
            }

            categoryContainer.addView(
                botao,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        iniciarButton = Button(requireContext()).apply {

            text = "INICIAR BATALHA"

            setOnClickListener {

                val p1 = primeiro
                val p2 = segundo

                if (p1.isNullOrBlank() || p2.isNullOrBlank()) {
                    Toast.makeText(
                        requireContext(),
                        "Selecione os dois personagens.",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@setOnClickListener
                }

                if (p1 == p2) {
                    Toast.makeText(
                        requireContext(),
                        "Escolha dois personagens diferentes.",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@setOnClickListener
                }

                // As duas escolhas já estão no SharedModelViewModel.
                findNavController().navigate(
                    R.id.navigation_camera
                )
            }
        }

        categoryContainer.addView(iniciarButton)

        atualizarSelecao()
    }

    private fun selecionarArquivo(arquivo: String) {

        if (personagemSelecionado == 1) {

            primeiro = arquivo

            sharedViewModel.selecionarPersonagem1(arquivo)

            // Impede que os dois personagens sejam iguais.
            if (segundo == arquivo) {
                segundo = null
                sharedViewModel.selecionarPersonagem2("")
            }

            personagemSelecionado = 2

        } else {

            if (arquivo == primeiro) {
                Toast.makeText(
                    requireContext(),
                    "Escolha um personagem diferente do primeiro.",
                    Toast.LENGTH_SHORT
                ).show()

                return
            }

            segundo = arquivo

            sharedViewModel.selecionarPersonagem2(arquivo)
        }

        atualizarSelecao()
    }

    private fun atualizarSelecao() {

        selecaoText?.text = textoSelecao()

        iniciarButton?.isEnabled =
            !primeiro.isNullOrBlank() &&
                    !segundo.isNullOrBlank() &&
                    primeiro != segundo
    }

    private fun textoSelecao(): String {

        val etapa = if (personagemSelecionado == 1) {
            "Agora escolha o primeiro personagem."
        } else {
            "Agora escolha o segundo personagem."
        }

        return "Personagem 1: ${nomePersonagem(primeiro)}\n" +
                "Personagem 2: ${nomePersonagem(segundo)}\n\n" +
                etapa
    }

    private fun nomePersonagem(arquivo: String?): String {
        if (arquivo.isNullOrBlank()) {
            return "Não escolhido"
        }

        return arquivo
            .removeSuffix(".glb")
            .replace("_", " ")
            .replaceFirstChar { it.uppercase() }
    }

    override fun onDestroyView() {
        selecaoText = null
        iniciarButton = null

        super.onDestroyView()
    }
}
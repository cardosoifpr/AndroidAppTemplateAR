
package com.ifpr.androidapptemplatear.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.ifpr.androidapptemplatear.R
import io.github.sceneview.SceneView
import io.github.sceneview.math.Position
import io.github.sceneview.node.ModelNode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

class CameraFragment : Fragment() {

    companion object {
        private const val VIDA_MAXIMA = 100
    }

    private lateinit var sharedViewModel: SharedModelViewModel
    private lateinit var batalhaViewModel: BatalhaViewModel

    private var container: FrameLayout? = null
    private var placeButton: ExtendedFloatingActionButton? = null
    private var statusText: TextView? = null

    private var sceneView: SceneView? = null

    private var modelNode1: ModelNode? = null
    private var modelNode2: ModelNode? = null

    private var modelosVisiveis = false

    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraPreview: Preview? = null

    private var loadJob: Job? = null
    private var generation = 0
    private var modelGeneration = 0

    private var personagem1: String? = null
    private var personagem2: String? = null

    private var vidaBar1: ProgressBar? = null
    private var vidaBar2: ProgressBar? = null
    private var vidaTexto1: TextView? = null
    private var vidaTexto2: TextView? = null
    private var turnoTexto: TextView? = null

    private var ataqueButton: Button? = null
    private var reiniciarButton: Button? = null
    private var gameOverPanel: View? = null
    private var winnerText: TextView? = null
    private var restartGameButton: Button? = null

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (container == null) return@registerForActivityResult

            if (granted) {
                startPreview(useCamera = true)
            } else {
                startPreview(useCamera = false)
            }
        }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        parent: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(
            R.layout.fragment_camera,
            parent,
            false
        )
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        container = view.findViewById(R.id.sceneContainer)
        placeButton = view.findViewById(R.id.place)

        sharedViewModel = ViewModelProvider(requireActivity())
            .get(SharedModelViewModel::class.java)

        batalhaViewModel = ViewModelProvider(this)
            .get(BatalhaViewModel::class.java)

        configurarInterface(view)

        personagem1 = sharedViewModel.personagem1.value
        personagem2 = sharedViewModel.personagem2.value

        configurarBatalha()
        observarPersonagens()

        placeButton?.setOnClickListener {
            centralizarModelos()
        }

        validarSelecao()
        solicitarCamera()
    }

    // ---------------------------------------------------------
    // PERMISSÃO E INICIALIZAÇÃO
    // ---------------------------------------------------------

    private fun solicitarCamera() {

        val permitido = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (permitido) {
            startPreview(useCamera = true)
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // ---------------------------------------------------------
    // INTERFACE
    // ---------------------------------------------------------

    private fun configurarInterface(view: View) {

        statusText = view.findViewById(R.id.statusText)

        vidaTexto1 = view.findViewById(R.id.healthPlayer1)
        vidaTexto2 = view.findViewById(R.id.healthPlayer2)

        vidaBar1 = view.findViewById(R.id.barPlayer1)
        vidaBar2 = view.findViewById(R.id.barPlayer2)

        turnoTexto = view.findViewById(R.id.turnText)

        ataqueButton = view.findViewById(R.id.attackButton)
        reiniciarButton = view.findViewById(R.id.restartButton)

        gameOverPanel = view.findViewById(R.id.gameOverPanel)
        winnerText = view.findViewById(R.id.winnerText)
        restartGameButton = view.findViewById(R.id.restartGameButton)

        ataqueButton?.setOnClickListener {
            batalhaViewModel.atacar()
        }

        reiniciarButton?.setOnClickListener {
            reiniciarBatalha()
        }

        restartGameButton?.setOnClickListener {
            reiniciarBatalha()
        }

        gameOverPanel?.visibility = View.GONE
        reiniciarButton?.visibility = View.GONE

        ataqueButton?.isEnabled = false

        placeButton?.apply {
            text = "Mostrar personagens"
            isEnabled = false
        }
    }

    // ---------------------------------------------------------
    // PERSONAGENS SELECIONADOS
    // ---------------------------------------------------------

    private fun observarPersonagens() {

        sharedViewModel.personagem1.observe(viewLifecycleOwner) { arquivo ->
            if (arquivo != personagem1) {
                personagem1 = arquivo
                atualizarSelecao()
            }
        }

        sharedViewModel.personagem2.observe(viewLifecycleOwner) { arquivo ->
            if (arquivo != personagem2) {
                personagem2 = arquivo
                atualizarSelecao()
            }
        }
    }

    private fun atualizarSelecao() {

        validarSelecao()

        if (
            !personagem1.isNullOrBlank() &&
            !personagem2.isNullOrBlank() &&
            personagem1 != personagem2
        ) {
            batalhaViewModel.iniciarBatalha(
                personagem1,
                personagem2
            )
        }
    }

    private fun validarSelecao() {

        val primeiro = personagem1
        val segundo = personagem2

        if (primeiro.isNullOrBlank() || segundo.isNullOrBlank()) {
            statusText?.text = "Volte e selecione os dois personagens."
            ataqueButton?.isEnabled = false
            placeButton?.isEnabled = false
            return
        }

        if (primeiro == segundo) {
            statusText?.text = "Escolha dois personagens diferentes."
            ataqueButton?.isEnabled = false
            placeButton?.isEnabled = false
            return
        }

        if (sceneView != null) {
            carregarModelos()
        }
    }

    // ---------------------------------------------------------
    // SISTEMA DE BATALHA
    // ---------------------------------------------------------

    private fun configurarBatalha() {

        batalhaViewModel.estado.observe(viewLifecycleOwner) { estado ->
            atualizarInterfaceBatalha(estado)
        }

        if (
            !personagem1.isNullOrBlank() &&
            !personagem2.isNullOrBlank()
        ) {
            batalhaViewModel.iniciarBatalha(
                personagem1,
                personagem2
            )
        }
    }

    private fun reiniciarBatalha() {
        batalhaViewModel.jogarNovamente()
        centralizarModelos()
    }


    private fun atualizarInterfaceBatalha(
        estado: BatalhaEstado
    ) {
        vidaBar1?.max = VIDA_MAXIMA
        vidaBar2?.max = VIDA_MAXIMA

        vidaBar1?.progress = estado.vida1
        vidaBar2?.progress = estado.vida2

        vidaTexto1?.text =
            "${nomePersonagem(estado.personagem1)} • ${estado.vida1}/$VIDA_MAXIMA HP"

        vidaTexto2?.text =
            "${nomePersonagem(estado.personagem2)} • ${estado.vida2}/$VIDA_MAXIMA HP"

        turnoTexto?.text = when {
            estado.batalhaFinalizada -> "FIM DE BATALHA"

            estado.personagem1.isBlank() ||
                    estado.personagem2.isBlank() ->
                "Aguardando personagens"

            estado.turno == 1 ->
                "Turno: ${nomePersonagem(estado.personagem1)}"

            else ->
                "Turno: ${nomePersonagem(estado.personagem2)}"
        }

        if (estado.batalhaFinalizada) {
            ocultarModelos()

            gameOverPanel?.visibility = View.VISIBLE
            reiniciarButton?.visibility = View.VISIBLE
            winnerText?.text = estado.mensagem

            ataqueButton?.isEnabled = false
            placeButton?.isEnabled = false

            statusText?.text = "Batalha encerrada!"
        } else {
            gameOverPanel?.visibility = View.GONE
            reiniciarButton?.visibility = View.GONE

            placeButton?.isEnabled =
                modelNode1 != null && modelNode2 != null

            ataqueButton?.isEnabled =
                estado.personagem1.isNotBlank() &&
                        estado.personagem2.isNotBlank() &&
                        modelosVisiveis &&
                        modelNode1 != null &&
                        modelNode2 != null
        }
    }

    private fun ocultarModelos() {
        val scene = sceneView

        modelNode1?.let { node ->
            runCatching {
                scene?.removeChildNode(node)
            }
        }

        modelNode2?.let { node ->
            runCatching {
                scene?.removeChildNode(node)
            }
        }

        modelosVisiveis = false
    }


    private fun nomePersonagem(arquivo: String?): String {

        if (arquivo.isNullOrBlank()) return "Personagem"

        return arquivo
            .substringAfterLast("/")
            .substringBeforeLast(".")
            .replace("_", " ")
            .replaceFirstChar { it.uppercase() }
    }

    // ---------------------------------------------------------
    // CÂMERA E CENA 3D
    // ---------------------------------------------------------

    private fun startPreview(useCamera: Boolean) {

        val host = container ?: return

        clearScene()

        host.setBackgroundColor(Color.rgb(25, 30, 40))

        modelosVisiveis = false

        placeButton?.apply {
            text = "Mostrar personagens"
            isEnabled = false
        }

        if (useCamera) {

            val previewView = PreviewView(requireContext()).apply {
                implementationMode =
                    PreviewView.ImplementationMode.COMPATIBLE

                scaleType =
                    PreviewView.ScaleType.FILL_CENTER
            }

            host.addView(previewView, fullSize())

            abrirCamera(previewView)

        } else {
            statusText?.text =
                "Câmera indisponível. Exibindo cenário 3D."
        }

        try {

            val scene = SceneView(
                context = requireContext(),
                isOpaque = false
            )

            scene.lifecycle = viewLifecycleOwner.lifecycle

            scene.cameraNode.position = Position(0f, 0f, 3f)
            scene.cameraNode.lookAt(Position(0f, 0f, 0f))

            sceneView = scene

            // A cena fica sobre a imagem da câmera.
            host.addView(scene, fullSize())

            if (personagem1.isNullOrBlank() ||
                personagem2.isNullOrBlank()
            ) {
                statusText?.text =
                    "Selecione os dois personagens para começar."
            } else {
                carregarModelos()
            }

        } catch (_: Exception) {
            statusText?.text =
                "Não foi possível iniciar a visualização 3D."
        }
    }

    private fun abrirCamera(previewView: PreviewView) {

        val context = context ?: return
        val owner = viewLifecycleOwner

        try {

            val future =
                ProcessCameraProvider.getInstance(context)

            future.addListener({

                if (container == null) {
                    return@addListener
                }

                try {

                    val provider = future.get()

                    val selector = when {

                        provider.hasCamera(
                            CameraSelector.DEFAULT_BACK_CAMERA
                        ) -> CameraSelector.DEFAULT_BACK_CAMERA

                        provider.hasCamera(
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        ) -> CameraSelector.DEFAULT_FRONT_CAMERA

                        else -> {
                            statusText?.text =
                                "Nenhuma câmera disponível."
                            return@addListener
                        }
                    }

                    val preview = Preview.Builder().build()

                    preview.setSurfaceProvider(
                        previewView.surfaceProvider
                    )

                    provider.unbindAll()

                    provider.bindToLifecycle(
                        owner,
                        selector,
                        preview
                    )

                    cameraProvider = provider
                    cameraPreview = preview

                } catch (_: Exception) {
                    statusText?.text =
                        "Não foi possível abrir a câmera."
                }

            }, ContextCompat.getMainExecutor(context))

        } catch (_: Exception) {
            statusText?.text =
                "Erro ao inicializar a câmera."
        }
    }

    // ---------------------------------------------------------
    // CARREGAR OS DOIS PERSONAGENS
    // ---------------------------------------------------------

    private fun carregarModelos() {

        val scene = sceneView ?: return

        val arquivo1 = personagem1
        val arquivo2 = personagem2

        if (arquivo1.isNullOrBlank() || arquivo2.isNullOrBlank()) {
            statusText?.text = "Selecione os dois personagens."
            return
        }

        if (arquivo1 == arquivo2) {
            statusText?.text =
                "Escolha dois personagens diferentes."
            return
        }

        val location1 = modelLocation(arquivo1)
        val location2 = modelLocation(arquivo2)

        if (location1 == null || location2 == null) {
            statusText?.text =
                "Não foi possível encontrar os arquivos dos personagens."

            placeButton?.isEnabled = false
            ataqueButton?.isEnabled = false
            return
        }

        loadJob?.cancel()

        removerModelos()

        val expectedGeneration = generation
        val expectedModel = ++modelGeneration

        placeButton?.isEnabled = false
        ataqueButton?.isEnabled = false

        statusText?.text = "Carregando personagens 3D..."

        loadJob = viewLifecycleOwner.lifecycleScope.launch {

            try {

                val instance1 =
                    scene.modelLoader.loadModelInstance(location1)

                val instance2 =
                    scene.modelLoader.loadModelInstance(location2)

                if (
                    expectedGeneration != generation ||
                    expectedModel != modelGeneration ||
                    sceneView !== scene
                ) {
                    return@launch
                }

                if (instance1 == null || instance2 == null) {
                    statusText?.text =
                        "Não foi possível carregar um dos personagens."
                    return@launch
                }

                val node1 = ModelNode(
                    modelInstance = instance1,
                    scaleToUnits = 1.2f,
                    centerOrigin = Position(0f, 0f, 0f)
                )

                val node2 = ModelNode(
                    modelInstance = instance2,
                    scaleToUnits = 1.2f,
                    centerOrigin = Position(0f, 0f, 0f)
                )

                node1.position = Position(-0.65f, 0f, 0f)
                node2.position = Position(0.65f, 0f, 0f)

                modelNode1 = node1
                modelNode2 = node2

                modelosVisiveis = false

                placeButton?.apply {
                    text = "Mostrar personagens"
                    isEnabled = true
                }

                statusText?.text =
                    "Personagens carregados! Toque em Mostrar personagens."

                atualizarInterfaceBatalha(
                    batalhaViewModel.estado.value
                        ?: return@launch
                )

            } catch (e: CancellationException) {
                throw e

            } catch (_: Exception) {

                if (
                    expectedGeneration == generation &&
                    expectedModel == modelGeneration
                ) {
                    statusText?.text =
                        "Erro ao carregar os personagens 3D."
                }
            }
        }
    }

    // ---------------------------------------------------------
    // MOSTRAR E CENTRALIZAR OS PERSONAGENS
    // ---------------------------------------------------------

    private fun centralizarModelos() {

        val scene = sceneView
        val node1 = modelNode1
        val node2 = modelNode2

        if (scene == null || node1 == null || node2 == null) {
            statusText?.text =
                "Aguarde o carregamento dos personagens."
            return
        }

        try {

            if (!modelosVisiveis) {

                node1.position = Position(-0.65f, 0f, 0f)
                node2.position = Position(0.65f, 0f, 0f)

                scene.addChildNode(node1)
                scene.addChildNode(node2)

                modelosVisiveis = true
            } else {

                node1.position = Position(-0.65f, 0f, 0f)
                node2.position = Position(0.65f, 0f, 0f)
            }

            scene.cameraNode.position = Position(0f, 0f, 3f)
            scene.cameraNode.lookAt(Position(0f, 0f, 0f))

            placeButton?.apply {
                text = "Centralizar"
                isEnabled = true
            }

            statusText?.text =
                "Personagens exibidos na tela!"

            batalhaViewModel.estado.value?.let {
                atualizarInterfaceBatalha(it)
            }

        } catch (_: Exception) {
            statusText?.text =
                "Não foi possível mostrar os personagens."
        }
    }

    // ---------------------------------------------------------
    // LOCALIZAR ARQUIVOS DOS MODELOS
    // ---------------------------------------------------------

    private fun modelLocation(fileName: String?): String? {

        if (fileName.isNullOrBlank()) return null

        val context = context ?: return null

        try {
            context.assets.open(fileName).use { }
            return fileName
        } catch (_: Exception) {
            // Tenta localizar no armazenamento interno.
        }

        val file = File(context.filesDir, fileName)

        return if (file.isFile) {
            Uri.fromFile(file).toString()
        } else {
            null
        }
    }

    // ---------------------------------------------------------
    // REMOVER MODELOS
    // ---------------------------------------------------------

    private fun removerModelos() {

        val scene = sceneView

        modelNode1?.let { model ->
            runCatching {
                scene?.removeChildNode(model)
                model.destroy()
            }
        }

        modelNode2?.let { model ->
            runCatching {
                scene?.removeChildNode(model)
                model.destroy()
            }
        }

        modelNode1 = null
        modelNode2 = null

        modelosVisiveis = false
    }

    // ---------------------------------------------------------
    // LIMPAR CENA
    // ---------------------------------------------------------

    private fun clearScene() {

        generation++
        modelGeneration++

        loadJob?.cancel()
        loadJob = null

        cameraProvider?.let {
            runCatching {
                it.unbindAll()
            }
        }

        cameraProvider = null
        cameraPreview = null

        removerModelos()

        sceneView?.let { scene ->
            runCatching {
                scene.lifecycle = null
                scene.destroy()
            }
        }

        sceneView = null

        container?.removeAllViews()

        placeButton?.isEnabled = false
        ataqueButton?.isEnabled = false
    }

    // ---------------------------------------------------------
    // UTILITÁRIOS
    // ---------------------------------------------------------

    private fun fullSize(): FrameLayout.LayoutParams {

        return FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    // ---------------------------------------------------------
    // ENCERRAR TELA
    // ---------------------------------------------------------

    override fun onDestroyView() {

        clearScene()

        statusText = null
        placeButton = null
        container = null

        vidaBar1 = null
        vidaBar2 = null
        vidaTexto1 = null
        vidaTexto2 = null
        turnoTexto = null

        ataqueButton = null
        reiniciarButton = null
        gameOverPanel = null
        winnerText = null
        restartGameButton = null

        super.onDestroyView()
    }
}

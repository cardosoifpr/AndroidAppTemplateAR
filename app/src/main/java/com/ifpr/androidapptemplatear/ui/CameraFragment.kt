package com.ifpr.androidapptemplatear.ui

import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast

import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.ar.core.Config
import com.ifpr.androidapptemplatear.R
import com.ifpr.androidapptemplatear.databinding.FragmentCameraBinding

import io.github.sceneview.ar.ArSceneView
import io.github.sceneview.ar.node.ArModelNode
import io.github.sceneview.ar.node.PlacementMode
import io.github.sceneview.math.Position

import okio.IOException
import java.io.File
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt


class CameraFragment : Fragment() {

    // ============================================================
    // VIEWS
    // ============================================================

    private lateinit var sceneView: ArSceneView
    private lateinit var modelNode: ArModelNode
    private lateinit var placeButton: ExtendedFloatingActionButton

    private lateinit var sharedViewModel: SharedModelViewModel


    // ============================================================
    // CAMERA
    // ============================================================

    private val CAMERA_PERMISSION_CODE = 1001


    // ============================================================
    // MODELO
    // ============================================================

    private var currentModelFile =
        "old_man_walk_cycle.glb"


    // ============================================================
    // BINDING
    // ============================================================

    private var _binding: FragmentCameraBinding? = null

    private val binding
        get() = _binding!!


    // ============================================================
    // JOYSTICK
    // ============================================================

    private var joystickX = 0f
    private var joystickY = 0f

    private var targetX = 0f
    private var targetY = 0f

    private var joystickPressed = false


    // ============================================================
    // MOVIMENTO
    // ============================================================

    private val speed = 0.006f

    private val smoothness = 0.20f

    private val rotationSmoothness = 0.15f


    // ============================================================
    // LOOP
    // ============================================================

    private val handler =
        Handler(Looper.getMainLooper())


    private val movementLoop =
        object : Runnable {

            override fun run() {

                moveCharacter()

                handler.postDelayed(
                    this,
                    16L
                )
            }
        }


    // ============================================================
    // ON CREATE VIEW
    // ============================================================

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding =
            FragmentCameraBinding.inflate(
                inflater,
                container,
                false
            )


        // ========================================================
        // VIEW MODEL
        // ========================================================

        sharedViewModel =
            ViewModelProvider(requireActivity())[
                SharedModelViewModel::class.java
            ]


        // ========================================================
        // MODELO SELECIONADO
        // ========================================================

        sharedViewModel.selectedModel.observe(
            viewLifecycleOwner
        ) { path ->

            currentModelFile = path

            loadModel(path)
        }


        // ========================================================
        // SCENE VIEW
        // ========================================================

        sceneView =
            binding.sceneView.apply {

                lightEstimationMode =
                    Config.LightEstimationMode.DISABLED
            }


        // ========================================================
        // PLACE
        // ========================================================

        placeButton =
            binding.place


        placeButton.setOnClickListener {

            placeModel()
        }


        // ========================================================
        // JOYSTICK
        // ========================================================

        setupJoystick()


        return binding.root
    }


    // ============================================================
    // JOYSTICK
    // ============================================================

    private fun setupJoystick() {

        val joystick =
            binding.joystick

        val knob =
            binding.joystickKnob


        joystick.setOnTouchListener { view, event ->

            val centerX =
                view.width / 2f

            val centerY =
                view.height / 2f


            val maxDistance =
                (view.width - knob.width) / 2f


            when (event.action) {

                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_MOVE -> {

                    var dx =
                        event.x - centerX

                    var dy =
                        event.y - centerY


                    val distance =
                        sqrt(
                            dx * dx +
                                    dy * dy
                        )


                    // =========================================
                    // LIMITAR AO CÍRCULO
                    // =========================================

                    if (
                        distance > maxDistance &&
                        distance > 0f
                    ) {

                        dx =
                            dx / distance *
                                    maxDistance

                        dy =
                            dy / distance *
                                    maxDistance
                    }


                    // =========================================
                    // KNOB
                    // =========================================

                    knob.translationX = dx
                    knob.translationY = dy


                    // =========================================
                    // VALORES NORMALIZADOS
                    // =========================================

                    targetX =
                        (dx / maxDistance)
                            .coerceIn(-1f, 1f)

                    targetY =
                        (dy / maxDistance)
                            .coerceIn(-1f, 1f)


                    joystickPressed = true


                    true
                }


                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {

                    targetX = 0f
                    targetY = 0f

                    joystickPressed = false


                    knob.animate()
                        .translationX(0f)
                        .translationY(0f)
                        .setDuration(120)
                        .start()


                    true
                }


                else -> false
            }
        }
    }


    // ============================================================
    // MOVIMENTO DO BONECO
    // ============================================================

    private fun moveCharacter() {

        if (!::modelNode.isInitialized) {
            return
        }


        if (!joystickPressed) {

            // Suaviza a parada
            joystickX *= 0.85f
            joystickY *= 0.85f

            if (
                abs(joystickX) < 0.01f &&
                abs(joystickY) < 0.01f
            ) {

                joystickX = 0f
                joystickY = 0f
            }

        } else {

            // Suaviza o joystick
            joystickX +=
                (targetX - joystickX) *
                        smoothness

            joystickY +=
                (targetY - joystickY) *
                        smoothness
        }


        val magnitude =
            sqrt(
                joystickX * joystickX +
                        joystickY * joystickY
            )


        if (magnitude < 0.03f) {
            return
        }


        // ========================================================
        // DIREÇÃO
        // ========================================================

        val directionX =
            joystickX

        val directionZ =
            joystickY


        // ========================================================
        // VELOCIDADE
        // ========================================================

        val currentSpeed =
            speed *
                    magnitude.coerceIn(0f, 1f)


        val moveX =
            directionX * currentSpeed

        val moveZ =
            directionZ * currentSpeed


        // ========================================================
        // POSIÇÃO
        // ========================================================

        modelNode.position.x += moveX

        modelNode.position.z += moveZ


        // ========================================================
        // ROTAÇÃO
        // ========================================================

        val targetRotation =
            Math.toDegrees(
                atan2(
                    directionX.toDouble(),
                    directionZ.toDouble()
                )
            ).toFloat()


        var difference =
            targetRotation -
                    modelNode.rotation.y


        // ========================================================
        // CORRIGIR VOLTA DE 360°
        // ========================================================

        while (difference > 180f) {
            difference -= 360f
        }

        while (difference < -180f) {
            difference += 360f
        }


        // ========================================================
        // ROTACIONAR SUAVEMENTE
        // ========================================================

        modelNode.rotation.y +=
            difference *
                    rotationSmoothness
    }


    // ============================================================
    // VIEW CREATED
    // ============================================================

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {

        super.onViewCreated(
            view,
            savedInstanceState
        )


        checkCameraPermission()


        handler.post(
            movementLoop
        )
    }


    // ============================================================
    // DESTROY VIEW
    // ============================================================

    override fun onDestroyView() {

        handler.removeCallbacks(
            movementLoop
        )

        super.onDestroyView()

        _binding = null
    }


    // ============================================================
    // COLOCAR MODELO
    // ============================================================

    private fun placeModel() {

        if (!::modelNode.isInitialized) {
            return
        }


        // Cria o Anchor
        modelNode.anchor()


        // Não permitir arrastar o boneco
        modelNode.isEditable = false

        modelNode.isScaleEditable = false


        // Esconde os planos
        sceneView
            .planeRenderer
            .isVisible = false


        // ========================================================
        // SOM
        // ========================================================

        val soundResource = when (currentModelFile) {

            "old_man_walk_cycle.glb" ->
                R.raw.fahh

            "an_elderly_man.glb" ->
                R.raw.fahh

            "cadeira.glb" ->
                R.raw.fart

            "becks_garrafa.glb" ->
                R.raw.latinha

            else ->
                null
        }


        soundResource?.let { sound ->

            val mediaPlayer =
                MediaPlayer.create(
                    requireContext(),
                    sound
                )


            mediaPlayer?.apply {

                start()

                setOnCompletionListener { player ->

                    player.release()
                }
            }
        }
    }


    // ============================================================
    // PERMISSÃO
    // ============================================================

    private fun checkCameraPermission() {

        if (
            ContextCompat.checkSelfPermission(
                requireContext(),
                android.Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(
                    android.Manifest.permission.CAMERA
                ),
                CAMERA_PERMISSION_CODE
            )

        } else {

            initAR()
        }
    }


    // ============================================================
    // RESULTADO DA PERMISSÃO
    // ============================================================

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {

        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )


        if (
            requestCode == CAMERA_PERMISSION_CODE &&
            grantResults.isNotEmpty() &&
            grantResults[0] ==
            PackageManager.PERMISSION_GRANTED
        ) {

            initAR()

        } else {

            Toast.makeText(
                requireContext(),
                "Permissão de câmera negada",
                Toast.LENGTH_LONG
            ).show()
        }
    }


    // ============================================================
    // INICIAR AR
    // ============================================================

    private fun initAR() {

        loadModel(
            currentModelFile
        )
    }


    // ============================================================
    // CARREGAR MODELO
    // ============================================================

    private fun loadModel(
        fileName: String
    ) {

        try {

            if (::modelNode.isInitialized) {

                sceneView.removeChild(
                    modelNode
                )
            }

        } catch (e: Exception) {

            // Modelo ainda não existe
        }


        // ========================================================
        // CRIAR MODELO
        // ========================================================

        modelNode =
            ArModelNode(
                sceneView.engine,
                PlacementMode.INSTANT
            ).apply {

                loadModelGlbAsync(
                    glbFileLocation =
                        getFilePath(
                            requireContext(),
                            fileName
                        ),

                    centerOrigin =
                        Position(
                            0f,
                            0f,
                            1f
                        )
                ) {

                    sceneView
                        .planeRenderer
                        .isVisible = true
                }


                isEditable = false

                isScaleEditable = false
            }


        sceneView.addChild(
            modelNode
        )
    }


    // ============================================================
    // LOCALIZAR GLB
    // ============================================================

    private fun getFilePath(
        context: Context?,
        fileName: String
    ): String {

        try {

            context
                ?.assets
                ?.open(fileName)
                ?.close()


            return fileName

        } catch (e: IOException) {

            val internalFile =
                File(
                    context?.filesDir,
                    fileName
                )


            if (internalFile.exists()) {

                return Uri.fromFile(
                    internalFile
                ).toString()

            } else {

                return "Arquivo não encontrado em assets nem em filesDir: $fileName"
            }
        }
    }
}
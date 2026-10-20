package com.ifpr.androidapptemplatear.ui

import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
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

class CameraFragment : Fragment() {

    private lateinit var sceneView: ArSceneView
    private lateinit var modelNode: ArModelNode
    private lateinit var placeButton: ExtendedFloatingActionButton

    private lateinit var sharedViewModel: SharedModelViewModel

    private val CAMERA_PERMISSION_CODE = 1001

    private var currentModelFile: String = "an_elderly_man.glb"

    private var _binding: FragmentCameraBinding? = null

    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentCameraBinding.inflate(inflater, container, false)

        sharedViewModel =
            ViewModelProvider(requireActivity())[SharedModelViewModel::class.java]

        sharedViewModel.selectedModel.observe(viewLifecycleOwner) { path ->
            currentModelFile = path
            loadModel(path)
        }

        sceneView = binding.sceneView.apply {
            lightEstimationMode = Config.LightEstimationMode.DISABLED
        }

        placeButton = binding.place

        placeButton.setOnClickListener {
            placeModel()
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        checkCameraPermission()
    }

    private fun placeModel() {

        modelNode.anchor()
        modelNode.isEditable = true
        modelNode.isScaleEditable = false

        sceneView.planeRenderer.isVisible = false

        // Escolhe o som de acordo com o modelo GLB
        val soundResource = when (currentModelFile) {

            "an_elderly_man.glb" -> R.raw.fahh

            "cadeira.glb" -> R.raw.fart

            "becks_garrafa.glb" -> R.raw.latinha

            else -> null
        }

        // Toca o som
        soundResource?.let { sound ->

            val mediaPlayer = MediaPlayer.create(
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

    private fun checkCameraPermission() {

        if (
            ContextCompat.checkSelfPermission(
                requireContext(),
                android.Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(android.Manifest.permission.CAMERA),
                CAMERA_PERMISSION_CODE
            )

        } else {

            initAR()
        }
    }

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
            grantResults[0] == PackageManager.PERMISSION_GRANTED
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

    private fun initAR() {

        loadModel(currentModelFile)
    }

    private fun loadModel(fileName: String) {

        try {

            sceneView.removeChild(modelNode)

        } catch (e: Exception) {

            // Modelo ainda não existe
        }

        modelNode = ArModelNode(
            sceneView.engine,
            PlacementMode.INSTANT
        ).apply {

            loadModelGlbAsync(
                glbFileLocation = getFilePath(
                    requireContext(),
                    fileName
                ),
                centerOrigin = Position(
                    0f,
                    0f,
                    1f
                )
            ) {

                sceneView.planeRenderer.isVisible = true
            }

            // Permitir interação com o modelo
            isEditable = true
            isScaleEditable = false
        }

        sceneView.addChild(modelNode)
    }

    private fun getFilePath(
        context: Context?,
        fileName: String
    ): String {

        try {

            context?.assets?.open(fileName)?.close()

            // Arquivo encontrado nos assets
            return fileName

        } catch (e: IOException) {

            // Arquivo não encontrado nos assets
            // Procura no filesDir

            val internalFile = File(
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
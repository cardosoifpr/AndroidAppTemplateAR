package com.ifpr.androidapptemplatear.ui

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SharedModelViewModel : ViewModel() {

    val selectedModel = MutableLiveData<String?>()
    val personagem1 = MutableLiveData<String?>()
    val personagem2 = MutableLiveData<String?>()

    fun selectModel(modelPath: String) {
        selectedModel.value = modelPath
    }

    fun selecionarPersonagem1(modelPath: String) {
        personagem1.value = modelPath
    }

    fun selecionarPersonagem2(modelPath: String) {
        personagem2.value = modelPath
    }

    fun limparSelecao() {
        personagem1.value = null
        personagem2.value = null
    }
}
package com.example.mca_project.ml

import com.example.mca_project.domain.model.InferenceOutput
import javax.inject.Inject
import javax.inject.Singleton

/** 모델이 아직 준비되지 않았을 때 던지는 예외. UI에서 "Not ready!"로 표시. */
class ModelNotReadyException(message: String = "Not ready!") : Exception(message)

/**
 * TFLite 추론을 담당할 매니저 (현재는 stub).
 *
 * 실제 모델(.tflite)이 준비되면:
 *  1) assets에 모델 파일 추가
 *  2) Interpreter 로드 (loadModels)
 *  3) inferVoicePpg / inferFusion 에서 실제 추론 구현
 *
 * 그 전까지 추론 메서드는 ModelNotReadyException을 던진다.
 */
@Singleton
class ModelManager @Inject constructor() {

    /** 모델 로드 여부. TFLite 연동 전까지 항상 false. */
    val isReady: Boolean = false

    /** TODO(model): assets에서 TFLite 인터프리터 로드 */
    fun loadModels() {
        // TODO(model): Voice+PPG, Fusion 모델 Interpreter 초기화
    }

    /** 시나리오 2: 음성 + PPG 추론 */
    fun inferVoicePpg(
        audioFeatures: FloatArray,
        ppgFeatures: FloatArray,
    ): InferenceOutput {
        // TODO(model): 실제 Voice+PPG TFLite 추론
        throw ModelNotReadyException()
    }

    /** 시나리오 1: 음성 + 얼굴 + PPG 융합 추론 */
    fun inferFusion(
        audioFeatures: FloatArray,
        faceFeatures: FloatArray,
        ppgFeatures: FloatArray,
    ): InferenceOutput {
        // TODO(model): 실제 Fusion TFLite 추론
        throw ModelNotReadyException()
    }
}
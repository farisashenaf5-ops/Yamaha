#include <jni.h>
#include "OboeAudioEngine.h"

using faris::audio::OboeAudioEngine;

namespace {
OboeAudioEngine* fromHandle(jlong handle) {
  return reinterpret_cast<OboeAudioEngine*>(handle);
}
} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_faris_psrs975_audio_OboeNativeBridge_nativeCreate(JNIEnv* /*env*/, jobject /*thiz*/) {
  return reinterpret_cast<jlong>(new OboeAudioEngine());
}

JNIEXPORT void JNICALL
Java_com_faris_psrs975_audio_OboeNativeBridge_nativeDestroy(
    JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
  delete fromHandle(handle);
}

JNIEXPORT jboolean JNICALL
Java_com_faris_psrs975_audio_OboeNativeBridge_nativeStart(
    JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jint sampleRate, jint burstMultiplier) {
  auto* engine = fromHandle(handle);
  if (!engine) return JNI_FALSE;
  return engine->start(sampleRate, burstMultiplier) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_faris_psrs975_audio_OboeNativeBridge_nativeStop(
    JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
  if (auto* engine = fromHandle(handle)) {
    engine->stop();
  }
}

JNIEXPORT jboolean JNICALL
Java_com_faris_psrs975_audio_OboeNativeBridge_nativeSetSampleRate(
    JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jint sampleRate) {
  auto* engine = fromHandle(handle);
  if (!engine) return JNI_FALSE;
  return engine->setSampleRate(sampleRate) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_faris_psrs975_audio_OboeNativeBridge_nativeRegisterSampleZone(
    JNIEnv* env,
    jobject /*thiz*/,
    jlong handle,
    jint slotIndex,
    jfloatArray leftSamples,
    jfloatArray rightSamples,
    jint sampleRate,
    jint rootNote,
    jboolean loopEnabled,
    jint loopStart,
    jint loopEnd,
    jfloat fineTuneCents,
    jfloat attackSec,
    jfloat decaySec,
    jfloat sustainLevel,
    jfloat releaseSec,
    jfloat filterCutoffHz,
    jfloat baseGain,
    jfloat basePan,
    jboolean isDrum) {
  auto* engine = fromHandle(handle);
  if (!engine || !leftSamples) return -1;

  const jsize len = env->GetArrayLength(leftSamples);
  jfloat* leftPtr = env->GetFloatArrayElements(leftSamples, nullptr);
  jfloat* rightPtr = rightSamples ? env->GetFloatArrayElements(rightSamples, nullptr) : nullptr;

  const int32_t res = engine->registerSampleZone(
      slotIndex,
      leftPtr,
      rightPtr,
      len,
      sampleRate,
      rootNote,
      loopEnabled == JNI_TRUE,
      loopStart,
      loopEnd,
      fineTuneCents,
      attackSec,
      decaySec,
      sustainLevel,
      releaseSec,
      filterCutoffHz,
      baseGain,
      basePan,
      isDrum == JNI_TRUE);

  env->ReleaseFloatArrayElements(leftSamples, leftPtr, JNI_ABORT);
  if (rightPtr) {
    env->ReleaseFloatArrayElements(rightSamples, rightPtr, JNI_ABORT);
  }
  return res;
}

JNIEXPORT void JNICALL
Java_com_faris_psrs975_audio_OboeNativeBridge_nativeNoteOn(
    JNIEnv* /*env*/,
    jobject /*thiz*/,
    jlong handle,
    jint part,
    jint zoneIndex,
    jint midiNote,
    jint velocity,
    jfloat attackScale,
    jfloat releaseScale,
    jfloat brightnessScale) {
  if (auto* engine = fromHandle(handle)) {
    engine->enqueueNoteOn(part, zoneIndex, midiNote, velocity, attackScale, releaseScale, brightnessScale);
  }
}

JNIEXPORT void JNICALL
Java_com_faris_psrs975_audio_OboeNativeBridge_nativeNoteOff(
    JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jint part, jint midiNote) {
  if (auto* engine = fromHandle(handle)) {
    engine->enqueueNoteOff(part, midiNote);
  }
}

JNIEXPORT void JNICALL
Java_com_faris_psrs975_audio_OboeNativeBridge_nativeSetSustainPedal(
    JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jint part, jboolean down) {
  if (auto* engine = fromHandle(handle)) {
    engine->setSustainPedal(part, down == JNI_TRUE);
  }
}

JNIEXPORT void JNICALL
Java_com_faris_psrs975_audio_OboeNativeBridge_nativeSetPartMix(
    JNIEnv* /*env*/,
    jobject /*thiz*/,
    jlong handle,
    jint part,
    jfloat volume,
    jfloat pan,
    jboolean muted,
    jboolean soloed) {
  if (auto* engine = fromHandle(handle)) {
    engine->setPartMix(part, volume, pan, muted == JNI_TRUE, soloed == JNI_TRUE);
  }
}

JNIEXPORT void JNICALL
Java_com_faris_psrs975_audio_OboeNativeBridge_nativeSetPitchBend(
    JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jint part, jfloat semitones) {
  if (auto* engine = fromHandle(handle)) {
    engine->setPitchBend(part, semitones);
  }
}

JNIEXPORT void JNICALL
Java_com_faris_psrs975_audio_OboeNativeBridge_nativeSetModulation(
    JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jint part, jfloat depth) {
  if (auto* engine = fromHandle(handle)) {
    engine->setModulation(part, depth);
  }
}

JNIEXPORT void JNICALL
Java_com_faris_psrs975_audio_OboeNativeBridge_nativeSetMasterGainAndBalance(
    JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jfloat masterVolume, jfloat balance) {
  if (auto* engine = fromHandle(handle)) {
    engine->setMasterGainAndBalance(masterVolume, balance);
  }
}

JNIEXPORT void JNICALL
Java_com_faris_psrs975_audio_OboeNativeBridge_nativeAllNotesOff(
    JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jint part) {
  if (auto* engine = fromHandle(handle)) {
    engine->allNotesOff(part);
  }
}

JNIEXPORT void JNICALL
Java_com_faris_psrs975_audio_OboeNativeBridge_nativePanic(
    JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
  if (auto* engine = fromHandle(handle)) {
    engine->panic();
  }
}

JNIEXPORT jint JNICALL
Java_com_faris_psrs975_audio_OboeNativeBridge_nativeWriteStereoPcm16(
    JNIEnv* env, jobject /*thiz*/, jlong handle, jshortArray interleavedPcm16, jint frames) {
  auto* engine = fromHandle(handle);
  if (!engine || !interleavedPcm16 || frames <= 0) return 0;
  jshort* ptr = env->GetShortArrayElements(interleavedPcm16, nullptr);
  const int32_t written = engine->writeStereoPcm16(ptr, frames);
  env->ReleaseShortArrayElements(interleavedPcm16, ptr, JNI_ABORT);
  return written;
}

JNIEXPORT jint JNICALL
Java_com_faris_psrs975_audio_OboeNativeBridge_nativeAvailablePcmRingFrames(
    JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
  auto* engine = fromHandle(handle);
  return engine ? engine->availablePcmRingFrames() : 0;
}

JNIEXPORT void JNICALL
Java_com_faris_psrs975_audio_OboeNativeBridge_nativeQueryStats(
    JNIEnv* env, jobject /*thiz*/, jlong handle, jintArray outInts, jfloatArray outFloats) {
  auto* engine = fromHandle(handle);
  if (!engine || !outInts || !outFloats) return;
  const auto snap = engine->getStats();
  jint ints[10] = {
      snap.sampleRate,
      snap.framesPerBurst,
      snap.bufferSizeFrames,
      snap.bufferCapacityFrames,
      snap.xRunCount,
      snap.activeVoices,
      snap.audioApi,
      snap.sharingMode,
      snap.performanceMode,
      snap.isRunning ? 1 : 0,
  };
  jfloat floats[2] = {snap.peakLeft, snap.peakRight};
  env->SetIntArrayRegion(outInts, 0, 10, ints);
  env->SetFloatArrayRegion(outFloats, 0, 2, floats);
}

} // extern "C"

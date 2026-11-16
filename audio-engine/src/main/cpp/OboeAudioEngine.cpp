#include "OboeAudioEngine.h"

#include <algorithm>
#include <cmath>
#include <cstring>
#include <android/log.h>

#define LOG_TAG "FarisOboeAudio"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace faris::audio {

namespace {
constexpr double kPi = 3.14159265358979323846;
}

OboeAudioEngine::OboeAudioEngine()
    : mPcmRing(kPcmRingFrames * 2, 0.0f) {
  for (auto& ptr : mZonePtrs) {
    ptr.store(nullptr, std::memory_order_relaxed);
  }
}

OboeAudioEngine::~OboeAudioEngine() {
  stop();
  if (mRecoveryThread.joinable()) {
    mRecoveryThread.join();
  }
}

bool OboeAudioEngine::start(int32_t requestedSampleRate, int32_t bufferBurstMultiplier) {
  std::lock_guard<std::mutex> lock(mStreamMutex);
  if (mRunning.load(std::memory_order_acquire)) {
    return true;
  }
  mRequestedSampleRate.store(requestedSampleRate, std::memory_order_relaxed);
  mBurstMultiplier.store(std::clamp(bufferBurstMultiplier, 1, 8), std::memory_order_relaxed);
  return openAndStartInternal(requestedSampleRate, mBurstMultiplier.load(std::memory_order_relaxed));
}

void OboeAudioEngine::stop() {
  mRunning.store(false, std::memory_order_release);
  {
    std::lock_guard<std::mutex> lock(mStreamMutex);
    closeStreamInternal();
  }
  if (mRecoveryThread.joinable()) {
    mRecoveryThread.join();
  }
  mActiveVoiceCount.store(0, std::memory_order_relaxed);
  mPeakLeft.store(0.0f, std::memory_order_relaxed);
  mPeakRight.store(0.0f, std::memory_order_relaxed);
}

bool OboeAudioEngine::setSampleRate(int32_t sampleRate) {
  if (sampleRate != 44100 && sampleRate != 48000 && sampleRate != 0) {
    return false;
  }
  std::lock_guard<std::mutex> lock(mStreamMutex);
  const bool wasRunning = mRunning.load(std::memory_order_acquire);
  mRequestedSampleRate.store(sampleRate, std::memory_order_relaxed);
  if (!wasRunning) {
    mActualSampleRate.store(sampleRate > 0 ? sampleRate : 44100, std::memory_order_relaxed);
    return true;
  }
  closeStreamInternal();
  return openAndStartInternal(sampleRate, mBurstMultiplier.load(std::memory_order_relaxed));
}

bool OboeAudioEngine::openAndStartInternal(int32_t requestedSampleRate, int32_t bufferBurstMultiplier) {
  auto tryOpen = [&](oboe::SharingMode sharingMode) -> oboe::Result {
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(sharingMode)
        ->setFormat(oboe::AudioFormat::Float)
        ->setFormatConversionAllowed(true)
        ->setChannelCount(oboe::ChannelCount::Stereo)
        ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium)
        ->setUsage(oboe::Usage::Media)
        ->setContentType(oboe::ContentType::Music)
        ->setDataCallback(this)
        ->setErrorCallback(this);
    if (requestedSampleRate > 0) {
      builder.setSampleRate(requestedSampleRate);
    }
    return builder.openStream(mStream);
  };

  oboe::Result result = tryOpen(oboe::SharingMode::Exclusive);
  if (result != oboe::Result::OK) {
    LOGI("Exclusive sharing mode unavailable (%s); falling back to Shared",
         oboe::convertToText(result));
    result = tryOpen(oboe::SharingMode::Shared);
  }
  if (result != oboe::Result::OK || !mStream) {
    LOGE("Failed to open Oboe output stream: %s", oboe::convertToText(result));
    mRunning.store(false, std::memory_order_release);
    return false;
  }

  const int32_t framesPerBurst = std::max(64, mStream->getFramesPerBurst());
  const int32_t targetBufferFrames = framesPerBurst * std::clamp(bufferBurstMultiplier, 1, 8);
  mStream->setBufferSizeInFrames(targetBufferFrames);

  mActualSampleRate.store(mStream->getSampleRate(), std::memory_order_release);
  mFramesPerBurst.store(framesPerBurst, std::memory_order_relaxed);
  mBufferSizeFrames.store(mStream->getBufferSizeInFrames(), std::memory_order_relaxed);
  mBufferCapacityFrames.store(mStream->getBufferCapacityInFrames(), std::memory_order_relaxed);

  int32_t apiCode = 0;
  if (mStream->getAudioApi() == oboe::AudioApi::AAudio) apiCode = 1;
  else if (mStream->getAudioApi() == oboe::AudioApi::OpenSLES) apiCode = 2;
  mAudioApi.store(apiCode, std::memory_order_relaxed);

  mSharingMode.store(mStream->getSharingMode() == oboe::SharingMode::Exclusive ? 1 : 0,
                     std::memory_order_relaxed);

  int32_t perfCode = 0;
  if (mStream->getPerformanceMode() == oboe::PerformanceMode::PowerSaving) perfCode = 1;
  else if (mStream->getPerformanceMode() == oboe::PerformanceMode::LowLatency) perfCode = 2;
  mPerformanceMode.store(perfCode, std::memory_order_relaxed);

  // Clear stale ring samples before starting stream
  mPcmReadIdx.store(0, std::memory_order_relaxed);
  mPcmWriteIdx.store(0, std::memory_order_relaxed);

  mRunning.store(true, std::memory_order_release);
  result = mStream->requestStart();
  if (result != oboe::Result::OK) {
    LOGE("Failed to start Oboe stream: %s", oboe::convertToText(result));
    closeStreamInternal();
    mRunning.store(false, std::memory_order_release);
    return false;
  }

  LOGI("Oboe stream started: api=%d sr=%d burst=%d buf=%d cap=%d sharing=%d",
       apiCode,
       mActualSampleRate.load(std::memory_order_relaxed),
       framesPerBurst,
       mBufferSizeFrames.load(std::memory_order_relaxed),
       mBufferCapacityFrames.load(std::memory_order_relaxed),
       mSharingMode.load(std::memory_order_relaxed));
  return true;
}

void OboeAudioEngine::closeStreamInternal() {
  if (mStream) {
    mStream->stop();
    mStream->close();
    mStream.reset();
  }
}

void OboeAudioEngine::onErrorAfterClose(oboe::AudioStream* /*audioStream*/, oboe::Result error) {
  LOGI("Oboe stream closed with error: %s", oboe::convertToText(error));
  if (error == oboe::Result::ErrorDisconnected && mRunning.load(std::memory_order_acquire)) {
    bool expected = false;
    if (mRecoveryPending.compare_exchange_strong(expected, true)) {
      if (mRecoveryThread.joinable()) {
        mRecoveryThread.join();
      }
      mRecoveryThread = std::thread([this]() {
        std::lock_guard<std::mutex> lock(mStreamMutex);
        if (mRunning.load(std::memory_order_acquire)) {
          mStream.reset();
          openAndStartInternal(mRequestedSampleRate.load(std::memory_order_relaxed),
                               mBurstMultiplier.load(std::memory_order_relaxed));
        }
        mRecoveryPending.store(false, std::memory_order_release);
      });
    }
  }
}

int32_t OboeAudioEngine::registerSampleZone(
    int32_t slotIndex,
    const float* leftSamples,
    const float* rightSamples,
    int32_t sampleCount,
    int32_t sampleRate,
    int32_t rootNote,
    bool loopEnabled,
    int32_t loopStart,
    int32_t loopEnd,
    float fineTuneCents,
    float attackSec,
    float decaySec,
    float sustainLevel,
    float releaseSec,
    float filterCutoffHz,
    float baseGain,
    float basePan,
    bool isDrum) {
  if (slotIndex < 0 || slotIndex >= kMaxZones || !leftSamples || sampleCount < 2) {
    return -1;
  }
  auto zone = std::make_unique<NativeSampleZone>();
  zone->leftSamples.assign(leftSamples, leftSamples + sampleCount);
  if (rightSamples) {
    zone->rightSamples.assign(rightSamples, rightSamples + sampleCount);
  }
  zone->sampleRate = std::max(8000, sampleRate);
  zone->rootNote = std::clamp(rootNote, 0, 127);
  zone->loopEnabled = loopEnabled;
  zone->loopStart = std::clamp(loopStart, 0, sampleCount - 2);
  zone->loopEnd = std::clamp(loopEnd, zone->loopStart + 1, sampleCount - 1);
  zone->fineTuneCents = fineTuneCents;
  zone->attackSec = std::max(0.0005f, attackSec);
  zone->decaySec = std::max(0.001f, decaySec);
  zone->sustainLevel = std::clamp(sustainLevel, 0.0f, 1.0f);
  zone->releaseSec = std::max(0.001f, releaseSec);
  zone->filterCutoffHz = std::clamp(filterCutoffHz, 200.0f, 22000.0f);
  zone->baseGain = baseGain;
  zone->basePan = std::clamp(basePan, -1.0f, 1.0f);
  zone->isDrum = isDrum;

  std::lock_guard<std::mutex> lock(mZoneRegistryMutex);
  // Publish new zone atomically; keep previous zone alive until retired via swap
  mZonePtrs[slotIndex].store(nullptr, std::memory_order_release);
  mOwnedZones[slotIndex] = std::move(zone);
  mZonePtrs[slotIndex].store(mOwnedZones[slotIndex].get(), std::memory_order_release);
  return slotIndex;
}

bool OboeAudioEngine::pushCommand(const AudioCommand& cmd) {
  std::lock_guard<std::mutex> lock(mCmdProducerMutex);
  const uint32_t writeIdx = mCmdWriteIdx.load(std::memory_order_relaxed);
  const uint32_t readIdx = mCmdReadIdx.load(std::memory_order_acquire);
  if (writeIdx - readIdx >= kCommandRingCapacity) {
    return false;
  }
  mCommandRing[writeIdx & (kCommandRingCapacity - 1)] = cmd;
  mCmdWriteIdx.store(writeIdx + 1, std::memory_order_release);
  return true;
}

void OboeAudioEngine::enqueueNoteOn(
    int32_t part,
    int32_t zoneIndex,
    int32_t midiNote,
    int32_t velocity,
    float attackScale,
    float releaseScale,
    float brightnessScale) {
  if (part < 0 || part >= kMaxParts || zoneIndex < 0 || zoneIndex >= kMaxZones) return;
  const NativeSampleZone* zone = mZonePtrs[zoneIndex].load(std::memory_order_acquire);
  if (!zone) return;

  const int32_t clampedNote = std::clamp(midiNote, 0, 127);
  const int32_t clampedVel = std::clamp(velocity, 1, 127);
  const float sr = static_cast<float>(std::max(8000, mActualSampleRate.load(std::memory_order_relaxed)));

  const float semitoneShift = zone->isDrum ? 0.0f : static_cast<float>(clampedNote - zone->rootNote);
  const double pitchFactor = std::pow(2.0, (semitoneShift + zone->fineTuneCents / 100.0) / 12.0);
  const double ratio = (static_cast<double>(zone->sampleRate) / static_cast<double>(sr)) * pitchFactor;

  const float attackSamples = std::max(1.0f, zone->attackSec * std::max(0.1f, attackScale) * sr);
  const float decaySamples = std::max(16.0f, zone->decaySec * sr);
  const float releaseSamples = std::max(24.0f, zone->releaseSec * std::max(0.1f, releaseScale) * sr);

  const float velNorm = static_cast<float>(clampedVel) / 127.0f;
  const float cutoff = std::clamp(
      zone->filterCutoffHz * std::max(0.1f, brightnessScale) * (0.35f + 0.65f * velNorm),
      220.0f,
      sr * 0.45f);
  const float lpAlpha = std::clamp(
      1.0f - std::exp(static_cast<float>(-2.0 * kPi * cutoff / sr)),
      0.03f,
      0.99f);

  AudioCommand cmd{};
  cmd.type = CommandType::NOTE_ON;
  cmd.part = part;
  cmd.midiNote = clampedNote;
  cmd.zoneIndex = zoneIndex;
  cmd.phaseStep = ratio;
  cmd.attackRate = 1.0f / attackSamples;
  cmd.decayRate = (1.0f - zone->sustainLevel) / decaySamples;
  cmd.sustainTarget = zone->sustainLevel;
  cmd.releaseRate = 1.0f / releaseSamples;
  cmd.lpAlpha = lpAlpha;
  cmd.noteGain = (velNorm * velNorm * 0.85f + 0.15f) * zone->baseGain;
  cmd.zonePan = zone->basePan;
  pushCommand(cmd);
}

void OboeAudioEngine::enqueueNoteOff(int32_t part, int32_t midiNote) {
  if (part < 0 || part >= kMaxParts) return;
  AudioCommand cmd{};
  cmd.type = CommandType::NOTE_OFF;
  cmd.part = part;
  cmd.midiNote = std::clamp(midiNote, 0, 127);
  pushCommand(cmd);
}

void OboeAudioEngine::setSustainPedal(int32_t part, bool down) {
  if (part < 0 || part >= kMaxParts) return;
  mParts[part].sustainPedal.store(down, std::memory_order_release);
  if (!down) {
    AudioCommand cmd{};
    cmd.type = CommandType::RELEASE_SUSTAIN;
    cmd.part = part;
    pushCommand(cmd);
  }
}

void OboeAudioEngine::setPartMix(int32_t part, float volume, float pan, bool muted, bool soloed) {
  if (part < 0 || part >= kMaxParts) return;
  mParts[part].volume.store(std::clamp(volume, 0.0f, 1.5f), std::memory_order_relaxed);
  mParts[part].pan.store(std::clamp(pan, -1.0f, 1.0f), std::memory_order_relaxed);
  mParts[part].muted.store(muted, std::memory_order_relaxed);
  mParts[part].soloed.store(soloed, std::memory_order_relaxed);
}

void OboeAudioEngine::setPitchBend(int32_t part, float semitones) {
  if (part < 0 || part >= kMaxParts) return;
  mParts[part].pitchBendSemis.store(std::clamp(semitones, -12.0f, 12.0f), std::memory_order_relaxed);
}

void OboeAudioEngine::setModulation(int32_t part, float depth) {
  if (part < 0 || part >= kMaxParts) return;
  mParts[part].modDepth.store(std::clamp(depth, 0.0f, 1.0f), std::memory_order_relaxed);
}

void OboeAudioEngine::setMasterGainAndBalance(float masterVolume, float balance) {
  mMasterVolume.store(std::clamp(masterVolume, 0.0f, 1.5f), std::memory_order_relaxed);
  mMasterBalance.store(std::clamp(balance, -1.0f, 1.0f), std::memory_order_relaxed);
}

void OboeAudioEngine::allNotesOff(int32_t part) {
  AudioCommand cmd{};
  cmd.type = CommandType::ALL_NOTES_OFF;
  cmd.part = part;
  pushCommand(cmd);
}

void OboeAudioEngine::panic() {
  AudioCommand cmd{};
  cmd.type = CommandType::PANIC;
  pushCommand(cmd);
  mPcmReadIdx.store(mPcmWriteIdx.load(std::memory_order_acquire), std::memory_order_release);
}

int32_t OboeAudioEngine::writeStereoPcm16(const int16_t* interleavedStereo, int32_t frames) {
  if (!interleavedStereo || frames <= 0) return 0;
  const uint32_t writeIdx = mPcmWriteIdx.load(std::memory_order_relaxed);
  const uint32_t readIdx = mPcmReadIdx.load(std::memory_order_acquire);
  const uint32_t freeFrames = kPcmRingFrames - (writeIdx - readIdx);
  const uint32_t toWrite = std::min(static_cast<uint32_t>(frames), freeFrames);
  constexpr float inv32768 = 1.0f / 32768.0f;

  for (uint32_t i = 0; i < toWrite; ++i) {
    const uint32_t slot = (writeIdx + i) & (kPcmRingFrames - 1);
    mPcmRing[slot * 2] = static_cast<float>(interleavedStereo[i * 2]) * inv32768;
    mPcmRing[slot * 2 + 1] = static_cast<float>(interleavedStereo[i * 2 + 1]) * inv32768;
  }
  mPcmWriteIdx.store(writeIdx + toWrite, std::memory_order_release);
  return static_cast<int32_t>(toWrite);
}

int32_t OboeAudioEngine::availablePcmRingFrames() const {
  const uint32_t writeIdx = mPcmWriteIdx.load(std::memory_order_acquire);
  const uint32_t readIdx = mPcmReadIdx.load(std::memory_order_relaxed);
  return static_cast<int32_t>(writeIdx - readIdx);
}

void OboeAudioEngine::drainCommands() {
  uint32_t readIdx = mCmdReadIdx.load(std::memory_order_relaxed);
  const uint32_t writeIdx = mCmdWriteIdx.load(std::memory_order_acquire);
  while (readIdx != writeIdx) {
    const AudioCommand& cmd = mCommandRing[readIdx & (kCommandRingCapacity - 1)];
    handleCommand(cmd);
    ++readIdx;
  }
  mCmdReadIdx.store(readIdx, std::memory_order_release);
}

NativeVoice& OboeAudioEngine::allocateVoice(int32_t part, int32_t midiNote) {
  for (auto& v : mVoices) {
    if (!v.active) return v;
  }
  NativeVoice* bestReleasing = nullptr;
  NativeVoice* oldest = &mVoices[0];
  for (auto& v : mVoices) {
    if (v.part == part && v.midiNote == midiNote && v.envStage == EnvStage::RELEASE) {
      return v;
    }
    if (v.envStage == EnvStage::RELEASE) {
      if (!bestReleasing || v.envLevel < bestReleasing->envLevel) {
        bestReleasing = &v;
      }
    }
    if (v.ageCounter < oldest->ageCounter) {
      oldest = &v;
    }
  }
  return bestReleasing ? *bestReleasing : *oldest;
}

void OboeAudioEngine::handleCommand(const AudioCommand& cmd) {
  switch (cmd.type) {
    case CommandType::NOTE_ON: {
      NativeVoice& v = allocateVoice(cmd.part, cmd.midiNote);
      v.active = true;
      v.part = cmd.part;
      v.midiNote = cmd.midiNote;
      v.zoneIndex = cmd.zoneIndex;
      v.sustainedByPedal = false;
      v.ageCounter = ++mGlobalAge;
      v.playPos = 0.0;
      v.phaseStep = cmd.phaseStep;
      v.envStage = EnvStage::ATTACK;
      v.envLevel = 0.0f;
      v.attackRate = cmd.attackRate;
      v.decayRate = cmd.decayRate;
      v.sustainTarget = cmd.sustainTarget;
      v.releaseRate = cmd.releaseRate;
      v.lpZ1L = 0.0f;
      v.lpZ1R = 0.0f;
      v.lpAlpha = cmd.lpAlpha;
      v.noteGain = cmd.noteGain;
      v.zonePan = cmd.zonePan;
      break;
    }
    case CommandType::NOTE_OFF: {
      const bool pedalDown = mParts[cmd.part].sustainPedal.load(std::memory_order_acquire);
      for (auto& v : mVoices) {
        if (v.active && v.part == cmd.part && v.midiNote == cmd.midiNote &&
            v.envStage != EnvStage::RELEASE) {
          if (pedalDown) {
            v.sustainedByPedal = true;
          } else {
            v.envStage = EnvStage::RELEASE;
          }
        }
      }
      break;
    }
    case CommandType::RELEASE_SUSTAIN: {
      for (auto& v : mVoices) {
        if (v.active && v.part == cmd.part && v.sustainedByPedal) {
          v.sustainedByPedal = false;
          v.envStage = EnvStage::RELEASE;
        }
      }
      break;
    }
    case CommandType::ALL_NOTES_OFF: {
      for (auto& v : mVoices) {
        if (v.active && (cmd.part < 0 || v.part == cmd.part)) {
          v.sustainedByPedal = false;
          v.envStage = EnvStage::RELEASE;
        }
      }
      break;
    }
    case CommandType::PANIC: {
      for (auto& v : mVoices) {
        v.active = false;
        v.envStage = EnvStage::IDLE;
      }
      break;
    }
  }
}

oboe::DataCallbackResult OboeAudioEngine::onAudioReady(
    oboe::AudioStream* /*audioStream*/,
    void* audioData,
    int32_t numFrames) {
  float* out = static_cast<float*>(audioData);
  if (!out || numFrames <= 0) {
    return oboe::DataCallbackResult::Continue;
  }

  std::fill_n(out, numFrames * 2, 0.0f);

  // 1. Drain lock-free command queue
  drainCommands();

  // 2. Check solo state across parts
  bool anySolo = false;
  for (int32_t p = 0; p < kMaxParts; ++p) {
    if (mParts[p].soloed.load(std::memory_order_relaxed)) {
      anySolo = true;
      break;
    }
  }

  const float sr = static_cast<float>(std::max(8000, mActualSampleRate.load(std::memory_order_relaxed)));
  const float lfoInc = static_cast<float>(2.0 * kPi * 5.2 / sr);
  int32_t activeCount = 0;

  // 3. Polyphonic sample rendering directly on the real-time Oboe callback thread
  for (auto& slot : mVoices) {
    if (!slot.active) continue;
    const NativeSampleZone* zone =
        (slot.zoneIndex >= 0 && slot.zoneIndex < kMaxZones)
            ? mZonePtrs[slot.zoneIndex].load(std::memory_order_acquire)
            : nullptr;
    if (!zone || zone->leftSamples.size() < 2) {
      slot.active = false;
      continue;
    }
    ++activeCount;

    const auto& partParams = mParts[slot.part];
    const bool partAudible = !partParams.muted.load(std::memory_order_relaxed) &&
                             (!anySolo || partParams.soloed.load(std::memory_order_relaxed));
    const float partVol = partAudible ? partParams.volume.load(std::memory_order_relaxed) : 0.0f;
    const float pan = std::clamp(partParams.pan.load(std::memory_order_relaxed) + slot.zonePan, -1.0f, 1.0f);
    const float gainL = partVol * slot.noteGain * (1.0f - std::max(0.0f, pan));
    const float gainR = partVol * slot.noteGain * (1.0f + std::min(0.0f, pan));

    const float bendSemis = partParams.pitchBendSemis.load(std::memory_order_relaxed);
    const float modDepth = partParams.modDepth.load(std::memory_order_relaxed);
    const double bendFactor = (bendSemis != 0.0f) ? std::pow(2.0, bendSemis / 12.0) : 1.0;

    const float* samplesL = zone->leftSamples.data();
    const float* samplesR = zone->rightSamples.empty() ? samplesL : zone->rightSamples.data();
    const int32_t len = static_cast<int32_t>(zone->leftSamples.size());
    const bool loopOk = zone->loopEnabled && zone->loopEnd > zone->loopStart + 1 && zone->loopEnd < len;
    const double loopLen = loopOk ? static_cast<double>(zone->loopEnd - zone->loopStart) : 0.0;
    const float alpha = slot.lpAlpha;

    for (int32_t i = 0; i < numFrames; ++i) {
      switch (slot.envStage) {
        case EnvStage::IDLE:
          slot.active = false;
          i = numFrames;
          continue;
        case EnvStage::ATTACK:
          slot.envLevel += slot.attackRate;
          if (slot.envLevel >= 1.0f) {
            slot.envLevel = 1.0f;
            slot.envStage = EnvStage::DECAY;
          }
          break;
        case EnvStage::DECAY:
          slot.envLevel -= slot.decayRate;
          if (slot.envLevel <= slot.sustainTarget) {
            slot.envLevel = slot.sustainTarget;
            slot.envStage = EnvStage::SUSTAIN;
          }
          break;
        case EnvStage::SUSTAIN:
          slot.envLevel = slot.sustainTarget;
          break;
        case EnvStage::RELEASE:
          slot.envLevel -= slot.releaseRate;
          if (slot.envLevel <= 0.0005f) {
            slot.envLevel = 0.0f;
            slot.active = false;
            i = numFrames;
            continue;
          }
          break;
      }

      const int32_t idx0 = static_cast<int32_t>(slot.playPos);
      if (idx0 >= len - 1) {
        slot.active = false;
        break;
      }
      const float frac = static_cast<float>(slot.playPos - idx0);
      const float rawL = samplesL[idx0] + (samplesL[idx0 + 1] - samplesL[idx0]) * frac;
      const float rawR = samplesR[idx0] + (samplesR[idx0 + 1] - samplesR[idx0]) * frac;

      slot.lpZ1L += alpha * (rawL - slot.lpZ1L);
      slot.lpZ1R += alpha * (rawR - slot.lpZ1R);

      const float env = slot.envLevel;
      out[i * 2] += slot.lpZ1L * env * gainL;
      out[i * 2 + 1] += slot.lpZ1R * env * gainR;

      const double modVib = (modDepth > 0.001f)
          ? 1.0 + std::sin(mLfoPhase + static_cast<float>(i) * lfoInc) * 0.015 * modDepth
          : 1.0;
      slot.playPos += slot.phaseStep * bendFactor * modVib;
      if (loopOk && slot.playPos >= zone->loopEnd) {
        slot.playPos -= loopLen;
      } else if (!loopOk && slot.playPos >= len - 1) {
        slot.active = false;
        break;
      }
    }
  }

  mLfoPhase += lfoInc * static_cast<float>(numFrames);
  if (mLfoPhase >= static_cast<float>(2.0 * kPi)) {
    mLfoPhase -= static_cast<float>(2.0 * kPi);
  }

  // Apply master gain & balance to direct native voices before mixing pre-mastered DSP bus frames
  const float masterGain = mMasterVolume.load(std::memory_order_relaxed);
  const float bal = mMasterBalance.load(std::memory_order_relaxed);
  const float balL = masterGain * (1.0f - std::max(0.0f, bal));
  const float balR = masterGain * (1.0f + std::min(0.0f, bal));
  for (int32_t i = 0; i < numFrames; ++i) {
    out[i * 2] *= balL;
    out[i * 2 + 1] *= balR;
  }

  // 4. Mix lock-free SPSC stereo PCM feed from Kotlin/JVM DSP bus (if fed)
  const uint32_t pcmRead = mPcmReadIdx.load(std::memory_order_relaxed);
  const uint32_t pcmWrite = mPcmWriteIdx.load(std::memory_order_acquire);
  const uint32_t avail = pcmWrite - pcmRead;
  const uint32_t toRead = std::min(static_cast<uint32_t>(numFrames), avail);
  for (uint32_t i = 0; i < toRead; ++i) {
    const uint32_t slot = (pcmRead + i) & (kPcmRingFrames - 1);
    out[i * 2] += mPcmRing[slot * 2];
    out[i * 2 + 1] += mPcmRing[slot * 2 + 1];
  }
  if (toRead > 0) {
    mPcmReadIdx.store(pcmRead + toRead, std::memory_order_release);
  }

  // 5. Soft limit / clamp and compute peak telemetry
  float peakL = 0.0f;
  float peakR = 0.0f;
  for (int32_t i = 0; i < numFrames; ++i) {
    const float l = std::clamp(out[i * 2], -1.0f, 1.0f);
    const float r = std::clamp(out[i * 2 + 1], -1.0f, 1.0f);
    out[i * 2] = l;
    out[i * 2 + 1] = r;
    peakL = std::max(peakL, std::abs(l));
    peakR = std::max(peakR, std::abs(r));
  }

  mActiveVoiceCount.store(activeCount, std::memory_order_relaxed);
  mPeakLeft.store(peakL, std::memory_order_relaxed);
  mPeakRight.store(peakR, std::memory_order_relaxed);
  return oboe::DataCallbackResult::Continue;
}

StreamStatsSnapshot OboeAudioEngine::getStats() const {
  StreamStatsSnapshot snap{};
  snap.sampleRate = mActualSampleRate.load(std::memory_order_relaxed);
  snap.framesPerBurst = mFramesPerBurst.load(std::memory_order_relaxed);
  snap.bufferSizeFrames = mBufferSizeFrames.load(std::memory_order_relaxed);
  snap.bufferCapacityFrames = mBufferCapacityFrames.load(std::memory_order_relaxed);
  snap.activeVoices = mActiveVoiceCount.load(std::memory_order_relaxed);
  snap.audioApi = mAudioApi.load(std::memory_order_relaxed);
  snap.sharingMode = mSharingMode.load(std::memory_order_relaxed);
  snap.performanceMode = mPerformanceMode.load(std::memory_order_relaxed);
  snap.isRunning = mRunning.load(std::memory_order_relaxed);
  snap.peakLeft = mPeakLeft.load(std::memory_order_relaxed);
  snap.peakRight = mPeakRight.load(std::memory_order_relaxed);
  if (mStream) {
    const auto xruns = mStream->getXRunCount();
    if (xruns) {
      snap.xRunCount = xruns.value();
    }
  }
  return snap;
}

} // namespace faris::audio

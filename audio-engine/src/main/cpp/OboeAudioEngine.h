#pragma once

#include <array>
#include <atomic>
#include <cstdint>
#include <memory>
#include <mutex>
#include <thread>
#include <vector>
#include <oboe/Oboe.h>

namespace faris::audio {

static constexpr int32_t kMaxPolyphony = 64;
static constexpr int32_t kMaxParts = 16;
static constexpr int32_t kMaxZones = 512;
static constexpr uint32_t kCommandRingCapacity = 1024; // power of 2
static constexpr uint32_t kPcmRingFrames = 16384;      // power of 2 (~340 ms max ring buffer)

enum class EnvStage : uint8_t {
  IDLE = 0,
  ATTACK = 1,
  DECAY = 2,
  SUSTAIN = 3,
  RELEASE = 4
};

struct NativeSampleZone {
  std::vector<float> leftSamples;
  std::vector<float> rightSamples;
  int32_t sampleRate = 44100;
  int32_t rootNote = 60;
  bool loopEnabled = false;
  int32_t loopStart = 0;
  int32_t loopEnd = 0;
  float fineTuneCents = 0.0f;
  float attackSec = 0.005f;
  float decaySec = 0.35f;
  float sustainLevel = 0.7f;
  float releaseSec = 0.25f;
  float filterCutoffHz = 16000.0f;
  float baseGain = 1.0f;
  float basePan = 0.0f;
  bool isDrum = false;
};

struct NativePartParams {
  std::atomic<float> volume{0.85f};
  std::atomic<float> pan{0.0f};
  std::atomic<bool> muted{false};
  std::atomic<bool> soloed{false};
  std::atomic<float> pitchBendSemis{0.0f};
  std::atomic<float> modDepth{0.0f};
  std::atomic<bool> sustainPedal{false};
};

struct NativeVoice {
  bool active = false;
  int32_t part = 0;
  int32_t midiNote = 60;
  int32_t zoneIndex = -1;
  bool sustainedByPedal = false;
  uint64_t ageCounter = 0;

  double playPos = 0.0;
  double phaseStep = 1.0;

  EnvStage envStage = EnvStage::IDLE;
  float envLevel = 0.0f;
  float attackRate = 0.01f;
  float decayRate = 0.001f;
  float sustainTarget = 0.7f;
  float releaseRate = 0.001f;

  float lpZ1L = 0.0f;
  float lpZ1R = 0.0f;
  float lpAlpha = 0.5f;
  float noteGain = 1.0f;
  float zonePan = 0.0f;
};

enum class CommandType : uint8_t {
  NOTE_ON = 1,
  NOTE_OFF = 2,
  RELEASE_SUSTAIN = 3,
  ALL_NOTES_OFF = 4,
  PANIC = 5
};

struct AudioCommand {
  CommandType type = CommandType::PANIC;
  int32_t part = 0;
  int32_t midiNote = 60;
  int32_t zoneIndex = -1;
  double phaseStep = 1.0;
  float attackRate = 0.01f;
  float decayRate = 0.001f;
  float sustainTarget = 0.7f;
  float releaseRate = 0.001f;
  float lpAlpha = 0.5f;
  float noteGain = 1.0f;
  float zonePan = 0.0f;
};

struct StreamStatsSnapshot {
  int32_t sampleRate = 44100;
  int32_t framesPerBurst = 0;
  int32_t bufferSizeFrames = 0;
  int32_t bufferCapacityFrames = 0;
  int32_t xRunCount = 0;
  int32_t activeVoices = 0;
  int32_t audioApi = 0;        // 0 = Unknown, 1 = AAudio, 2 = OpenSLES
  int32_t sharingMode = 0;     // 0 = Shared, 1 = Exclusive
  int32_t performanceMode = 0; // 0 = None, 1 = PowerSaving, 2 = LowLatency
  bool isRunning = false;
  float peakLeft = 0.0f;
  float peakRight = 0.0f;
};

class OboeAudioEngine : public oboe::AudioStreamDataCallback,
                        public oboe::AudioStreamErrorCallback {
public:
  OboeAudioEngine();
  ~OboeAudioEngine() override;

  bool start(int32_t requestedSampleRate, int32_t bufferBurstMultiplier = 2);
  void stop();
  bool setSampleRate(int32_t sampleRate);

  int32_t registerSampleZone(
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
      bool isDrum);

  void enqueueNoteOn(
      int32_t part,
      int32_t zoneIndex,
      int32_t midiNote,
      int32_t velocity,
      float attackScale,
      float releaseScale,
      float brightnessScale);

  void enqueueNoteOff(int32_t part, int32_t midiNote);
  void setSustainPedal(int32_t part, bool down);
  void setPartMix(int32_t part, float volume, float pan, bool muted, bool soloed);
  void setPitchBend(int32_t part, float semitones);
  void setModulation(int32_t part, float depth);
  void setMasterGainAndBalance(float masterVolume, float balance);
  void allNotesOff(int32_t part);
  void panic();

  // Lock-free SPSC stereo PCM feed from JVM DSP engine
  int32_t writeStereoPcm16(const int16_t* interleavedStereo, int32_t frames);
  int32_t availablePcmRingFrames() const;

  StreamStatsSnapshot getStats() const;

  // oboe::AudioStreamDataCallback
  oboe::DataCallbackResult onAudioReady(
      oboe::AudioStream* audioStream,
      void* audioData,
      int32_t numFrames) override;

  // oboe::AudioStreamErrorCallback
  void onErrorAfterClose(oboe::AudioStream* audioStream, oboe::Result error) override;

private:
  bool openAndStartInternal(int32_t requestedSampleRate, int32_t bufferBurstMultiplier);
  void closeStreamInternal();
  bool pushCommand(const AudioCommand& cmd);
  void drainCommands();
  NativeVoice& allocateVoice(int32_t part, int32_t midiNote);
  void handleCommand(const AudioCommand& cmd);

  std::mutex mStreamMutex;
  std::shared_ptr<oboe::AudioStream> mStream;
  std::atomic<bool> mRunning{false};
  std::atomic<int32_t> mRequestedSampleRate{44100};
  std::atomic<int32_t> mActualSampleRate{44100};
  std::atomic<int32_t> mBurstMultiplier{2};

  // Recovery thread for device disconnect / audio route change
  std::thread mRecoveryThread;
  std::atomic<bool> mRecoveryPending{false};

  // Lock-free zone registry (populated from control thread, read on audio thread)
  std::array<std::unique_ptr<NativeSampleZone>, kMaxZones> mOwnedZones;
  std::array<std::atomic<const NativeSampleZone*>, kMaxZones> mZonePtrs{};
  std::mutex mZoneRegistryMutex;

  // Lock-free SPSC command ring buffer
  std::array<AudioCommand, kCommandRingCapacity> mCommandRing{};
  std::atomic<uint32_t> mCmdWriteIdx{0};
  std::atomic<uint32_t> mCmdReadIdx{0};
  std::mutex mCmdProducerMutex; // serializes multiple Kotlin caller threads

  // Lock-free SPSC stereo PCM ring buffer
  std::vector<float> mPcmRing;
  std::atomic<uint32_t> mPcmWriteIdx{0};
  std::atomic<uint32_t> mPcmReadIdx{0};

  // Real-time audio thread owned voice state (no mutex touched inside onAudioReady)
  std::array<NativeVoice, kMaxPolyphony> mVoices{};
  uint64_t mGlobalAge = 0;
  float mLfoPhase = 0.0f;

  // Per-part and master atomic parameters
  std::array<NativePartParams, kMaxParts> mParts{};
  std::atomic<float> mMasterVolume{0.85f};
  std::atomic<float> mMasterBalance{0.0f};

  // Telemetry atomics updated by audio callback
  std::atomic<int32_t> mActiveVoiceCount{0};
  std::atomic<float> mPeakLeft{0.0f};
  std::atomic<float> mPeakRight{0.0f};
  std::atomic<int32_t> mFramesPerBurst{0};
  std::atomic<int32_t> mBufferSizeFrames{0};
  std::atomic<int32_t> mBufferCapacityFrames{0};
  std::atomic<int32_t> mAudioApi{0};
  std::atomic<int32_t> mSharingMode{0};
  std::atomic<int32_t> mPerformanceMode{0};
};

} // namespace faris::audio

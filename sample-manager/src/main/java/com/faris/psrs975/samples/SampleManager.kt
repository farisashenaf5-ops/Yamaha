package com.faris.psrs975.samples

import android.content.Context
import com.faris.psrs975.audio.SampleInstrument
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Caches built-in, asset-bundled, and imported SampleInstrument banks in memory with bounded
 * LRU eviction for instant zero-hitch switching across phones and tablets.
 */
class SampleManager(
  private val context: Context? = null,
  private val maxBuiltInCacheSize: Int = 8,
) {
  private val importedCache = ConcurrentHashMap<String, SampleInstrument>()
  private val builtInLru: MutableMap<String, SampleInstrument> = Collections.synchronizedMap(
    object : LinkedHashMap<String, SampleInstrument>(24, 0.75f, true) {
      override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SampleInstrument>?): Boolean {
        return size > maxBuiltInCacheSize
      }
    },
  )

  fun getOrBuildInstrument(
    id: String,
    name: String,
    model: AcousticSynthesisModel,
  ): SampleInstrument {
    importedCache[id]?.let { return it }
    synchronized(builtInLru) {
      builtInLru[id]?.let { return it }
    }
    val assetBundled = SampleAssetsLoader.tryLoadBundledInstrument(context, id, name)
    val resolved = assetBundled ?: SampleBankFactory.buildInstrument(id, name, model)
    synchronized(builtInLru) {
      builtInLru[id] = resolved
    }
    return resolved
  }

  fun registerImportedInstrument(instrument: SampleInstrument) {
    importedCache[instrument.id] = instrument
  }

  fun getRegisteredInstrument(id: String): SampleInstrument? {
    importedCache[id]?.let { return it }
    return synchronized(builtInLru) { builtInLru[id] }
  }

  fun removeInstrument(id: String) {
    importedCache.remove(id)
    synchronized(builtInLru) { builtInLru.remove(id) }
  }

  fun listImportedIds(): Set<String> = importedCache.keys.toSet()
}

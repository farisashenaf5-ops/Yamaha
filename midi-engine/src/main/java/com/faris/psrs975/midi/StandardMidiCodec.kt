package com.faris.psrs975.midi

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Standard MIDI File (SMF Type 0 / Type 1) parser and writer.
 */
object StandardMidiCodec {

  fun parse(input: InputStream, id: String, title: String, filePath: String? = null): MidiSong {
    val bytes = input.readBytes()
    return parseBytes(bytes, id, title, filePath)
  }

  fun parseBytes(bytes: ByteArray, id: String, title: String, filePath: String? = null): MidiSong {
    require(bytes.size >= 14) { "Invalid MIDI file: too short" }
    val bb = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
    val headerTag = String(bytes, 0, 4, Charsets.US_ASCII)
    require(headerTag == "MThd") { "Invalid MIDI header: expected MThd" }
    val headerLen = bb.getInt(4)
    val trackCount = bb.getShort(10).toInt() and 0xFFFF
    val division = bb.getShort(12).toInt() and 0xFFFF
    val tpqn = if ((division and 0x8000) == 0) max(24, division) else 480

    var pos = 8 + headerLen
    val allEvents = mutableListOf<MidiEvent>()
    val trackInfos = mutableListOf<MidiTrackInfo>()
    var initialBpm = 120
    var tsNum = 4
    var tsDen = 4
    var maxTick = 0L

    for (t in 0 until trackCount) {
      if (pos + 8 > bytes.size) break
      val chunkTag = String(bytes, pos, 4, Charsets.US_ASCII)
      val chunkLen = bb.getInt(pos + 4)
      pos += 8
      if (chunkTag != "MTrk" || pos + chunkLen > bytes.size) {
        pos += max(0, chunkLen)
        continue
      }

      val endPos = pos + chunkLen
      var cursor = pos
      var tick = 0L
      var runningStatus = 0
      var trackName = "Track ${t + 1}"
      var primaryCh = t % 16
      var program = 0
      var noteCount = 0

      while (cursor < endPos) {
        val (delta, nextCursor) = readVarLen(bytes, cursor, endPos)
        cursor = nextCursor
        tick += delta
        if (tick > maxTick) maxTick = tick
        if (cursor >= endPos) break

        var statusByte = bytes[cursor].toInt() and 0xFF
        if (statusByte < 0x80) {
          statusByte = runningStatus
        } else {
          cursor++
          if (statusByte < 0xF0) runningStatus = statusByte
        }

        when {
          statusByte == 0xFF -> {
            if (cursor >= endPos) break
            val metaType = bytes[cursor++].toInt() and 0xFF
            val (metaLen, afterLen) = readVarLen(bytes, cursor, endPos)
            cursor = afterLen
            val dataEnd = (cursor + metaLen.toInt()).coerceAtMost(endPos)
            when (metaType) {
              0x03 -> {
                if (dataEnd > cursor) {
                  trackName = String(bytes, cursor, dataEnd - cursor, Charsets.UTF_8).trim().ifEmpty { trackName }
                }
              }
              0x51 -> {
                if (cursor + 3 <= dataEnd) {
                  val usPerQuarter = ((bytes[cursor].toInt() and 0xFF) shl 16) or
                    ((bytes[cursor + 1].toInt() and 0xFF) shl 8) or
                    (bytes[cursor + 2].toInt() and 0xFF)
                  if (usPerQuarter > 0) {
                    val bpm = (60_000_000.0 / usPerQuarter).roundToInt().coerceIn(30, 280)
                    if (tick == 0L) initialBpm = bpm
                    allEvents.add(MidiEvent(tick, t, 0, MidiEventType.TEMPO, tempoBpm = bpm))
                  }
                }
              }
              0x58 -> {
                if (cursor + 2 <= dataEnd) {
                  tsNum = bytes[cursor].toInt() and 0xFF
                  tsDen = 1 shl (bytes[cursor + 1].toInt() and 0x07)
                }
              }
            }
            cursor = dataEnd
          }
          statusByte == 0xF0 || statusByte == 0xF7 -> {
            val (sysexLen, afterLen) = readVarLen(bytes, cursor, endPos)
            cursor = (afterLen + sysexLen.toInt()).coerceAtMost(endPos)
          }
          else -> {
            val cmd = statusByte and 0xF0
            val ch = statusByte and 0x0F
            primaryCh = ch
            when (cmd) {
              0x80 -> {
                if (cursor + 2 <= endPos) {
                  val note = bytes[cursor].toInt() and 0x7F
                  val vel = bytes[cursor + 1].toInt() and 0x7F
                  allEvents.add(MidiEvent(tick, t, ch, MidiEventType.NOTE_OFF, note, vel))
                  cursor += 2
                } else cursor = endPos
              }
              0x90 -> {
                if (cursor + 2 <= endPos) {
                  val note = bytes[cursor].toInt() and 0x7F
                  val vel = bytes[cursor + 1].toInt() and 0x7F
                  if (vel == 0) {
                    allEvents.add(MidiEvent(tick, t, ch, MidiEventType.NOTE_OFF, note, 0))
                  } else {
                    noteCount++
                    allEvents.add(MidiEvent(tick, t, ch, MidiEventType.NOTE_ON, note, vel))
                  }
                  cursor += 2
                } else cursor = endPos
              }
              0xB0 -> {
                if (cursor + 2 <= endPos) {
                  val cc = bytes[cursor].toInt() and 0x7F
                  val value = bytes[cursor + 1].toInt() and 0x7F
                  allEvents.add(MidiEvent(tick, t, ch, MidiEventType.CONTROL_CHANGE, cc, value))
                  cursor += 2
                } else cursor = endPos
              }
              0xC0 -> {
                if (cursor + 1 <= endPos) {
                  program = bytes[cursor].toInt() and 0x7F
                  allEvents.add(MidiEvent(tick, t, ch, MidiEventType.PROGRAM_CHANGE, program, 0))
                  cursor += 1
                } else cursor = endPos
              }
              0xE0 -> {
                if (cursor + 2 <= endPos) {
                  val lsb = bytes[cursor].toInt() and 0x7F
                  val msb = bytes[cursor + 1].toInt() and 0x7F
                  allEvents.add(MidiEvent(tick, t, ch, MidiEventType.PITCH_BEND, lsb, msb))
                  cursor += 2
                } else cursor = endPos
              }
              0xA0 -> cursor = (cursor + 2).coerceAtMost(endPos)
              0xD0 -> cursor = (cursor + 1).coerceAtMost(endPos)
              else -> cursor = endPos
            }
          }
        }
      }

      trackInfos.add(
        MidiTrackInfo(
          trackIndex = t,
          name = trackName,
          channel = primaryCh,
          programNumber = program,
          noteCount = noteCount,
        ),
      )
      pos = endPos
    }

    val sorted = allEvents.sortedBy { it.tick }
    return MidiSong(
      id = id,
      title = title,
      ticksPerQuarterNote = tpqn,
      initialBpm = initialBpm,
      timeSignatureNumerator = tsNum,
      timeSignatureDenominator = tsDen,
      totalTicks = max(tpqn.toLong() * 4L, maxTick),
      tracks = trackInfos,
      events = sorted,
      filePath = filePath,
    )
  }

  fun write(song: MidiSong, out: OutputStream) {
    val grouped = song.events.groupBy { it.trackIndex }
    val trackIndices = (song.tracks.map { it.trackIndex } + grouped.keys).distinct().sorted()
      .ifEmpty { listOf(0) }

    val header = ByteBuffer.allocate(14).order(ByteOrder.BIG_ENDIAN)
    header.put("MThd".toByteArray(Charsets.US_ASCII))
    header.putInt(6)
    header.putShort(if (trackIndices.size > 1) 1 else 0)
    header.putShort(trackIndices.size.toShort())
    header.putShort(song.ticksPerQuarterNote.toShort())
    out.write(header.array())

    for (tIdx in trackIndices) {
      val trkStream = ByteArrayOutputStream()
      val trackName = song.tracks.find { it.trackIndex == tIdx }?.name ?: "Track ${tIdx + 1}"
      // Meta track name at delta 0
      writeVarLen(trkStream, 0)
      trkStream.write(0xFF)
      trkStream.write(0x03)
      val nameBytes = trackName.toByteArray(Charsets.UTF_8)
      writeVarLen(trkStream, nameBytes.size.toLong())
      trkStream.write(nameBytes)

      if (tIdx == trackIndices.first()) {
        val usPerQuarter = 60_000_000 / song.initialBpm.coerceIn(30, 280)
        writeVarLen(trkStream, 0)
        trkStream.write(0xFF)
        trkStream.write(0x51)
        trkStream.write(0x03)
        trkStream.write((usPerQuarter shr 16) and 0xFF)
        trkStream.write((usPerQuarter shr 8) and 0xFF)
        trkStream.write(usPerQuarter and 0xFF)
      }

      var lastTick = 0L
      val events = (grouped[tIdx] ?: emptyList()).sortedBy { it.tick }
      for (ev in events) {
        val delta = max(0L, ev.tick - lastTick)
        lastTick = ev.tick
        when (ev.type) {
          MidiEventType.NOTE_ON -> {
            writeVarLen(trkStream, delta)
            trkStream.write(0x90 or (ev.channel and 0x0F))
            trkStream.write(ev.data1 and 0x7F)
            trkStream.write(ev.data2 and 0x7F)
          }
          MidiEventType.NOTE_OFF -> {
            writeVarLen(trkStream, delta)
            trkStream.write(0x80 or (ev.channel and 0x0F))
            trkStream.write(ev.data1 and 0x7F)
            trkStream.write(ev.data2 and 0x7F)
          }
          MidiEventType.CONTROL_CHANGE -> {
            writeVarLen(trkStream, delta)
            trkStream.write(0xB0 or (ev.channel and 0x0F))
            trkStream.write(ev.data1 and 0x7F)
            trkStream.write(ev.data2 and 0x7F)
          }
          MidiEventType.PROGRAM_CHANGE -> {
            writeVarLen(trkStream, delta)
            trkStream.write(0xC0 or (ev.channel and 0x0F))
            trkStream.write(ev.data1 and 0x7F)
          }
          MidiEventType.PITCH_BEND -> {
            writeVarLen(trkStream, delta)
            trkStream.write(0xE0 or (ev.channel and 0x0F))
            trkStream.write(ev.data1 and 0x7F)
            trkStream.write(ev.data2 and 0x7F)
          }
          MidiEventType.TEMPO -> {
            val us = 60_000_000 / ev.tempoBpm.coerceIn(30, 280)
            writeVarLen(trkStream, delta)
            trkStream.write(0xFF)
            trkStream.write(0x51)
            trkStream.write(0x03)
            trkStream.write((us shr 16) and 0xFF)
            trkStream.write((us shr 8) and 0xFF)
            trkStream.write(us and 0xFF)
          }
        }
      }

      // End of track
      writeVarLen(trkStream, 0)
      trkStream.write(0xFF)
      trkStream.write(0x2F)
      trkStream.write(0x00)

      val trkBytes = trkStream.toByteArray()
      val trkHeader = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
      trkHeader.put("MTrk".toByteArray(Charsets.US_ASCII))
      trkHeader.putInt(trkBytes.size)
      out.write(trkHeader.array())
      out.write(trkBytes)
    }
  }

  private fun readVarLen(bytes: ByteArray, start: Int, limit: Int): Pair<Long, Int> {
    var value = 0L
    var cursor = start
    var count = 0
    while (cursor < limit && count < 4) {
      val b = bytes[cursor++].toInt() and 0xFF
      value = (value shl 7) or (b and 0x7F).toLong()
      count++
      if ((b and 0x80) == 0) break
    }
    return value to cursor
  }

  private fun writeVarLen(out: OutputStream, value: Long) {
    var v = value
    val buf = IntArray(4)
    var idx = 0
    buf[idx++] = (v and 0x7F).toInt()
    v = v ushr 7
    while (v > 0 && idx < 4) {
      buf[idx++] = ((v and 0x7F) or 0x80).toInt()
      v = v ushr 7
    }
    for (i in idx - 1 downTo 0) {
      out.write(buf[i])
    }
  }
}

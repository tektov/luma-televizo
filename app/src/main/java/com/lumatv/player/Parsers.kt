package com.lumatv.player

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.zip.GZIPInputStream

data class ParsedPlaylist(
    val channels: List<Channel>,
    val detectedEpgUrl: String?
)

object Parsers {

    fun parseM3u(urlString: String): ParsedPlaylist {
        val text = downloadText(urlString)
        val channels = mutableListOf<Channel>()
        var detectedEpgUrl: String? = null

        var currentName = "Канал"
        var currentGroup = "Общие"
        var currentTvgId = ""
        var currentCatchupDays = 0
        var currentCatchupSource = ""
        var counter = 1

        for (rawLine in text.lines()) {
            val line = rawLine.trim()
            if (line.startsWith("#EXTM3U")) {
                detectedEpgUrl = extractAttr(line, "url-tvg")
                    ?: extractAttr(line, "x-tvg-url")
            } else if (line.startsWith("#EXTINF:")) {
                currentGroup = extractAttr(line, "group-title")?.takeIf { it.isNotBlank() } ?: "Общие"
                currentTvgId = extractAttr(line, "tvg-id") ?: ""
                currentCatchupDays = extractAttr(line, "catchup-days")?.toIntOrNull()
                    ?: extractAttr(line, "tvg-rec")?.toIntOrNull() ?: 0
                currentCatchupSource = extractAttr(line, "catchup-source") ?: ""

                val commaIdx = line.lastIndexOf(',')
                if (commaIdx != -1 && commaIdx < line.length - 1) {
                    currentName = line.substring(commaIdx + 1).trim()
                }
            } else if (line.startsWith("#EXTGRP:")) {
                val grp = line.removePrefix("#EXTGRP:").trim()
                if (grp.isNotEmpty()) currentGroup = grp
            } else if (line.startsWith("http://") || line.startsWith("https://")) {
                channels.add(
                    Channel(
                        number = counter++,
                        name = currentName,
                        category = currentGroup,
                        url = line,
                        tvgId = currentTvgId,
                        catchupDays = currentCatchupDays,
                        catchupSource = currentCatchupSource
                    )
                )
                currentName = "Канал $counter"
                currentGroup = "Общие"
                currentTvgId = ""
                currentCatchupDays = 0
                currentCatchupSource = ""
            }
        }
        return ParsedPlaylist(channels, detectedEpgUrl)
    }

    fun parseXmltv(urlString: String): Map<String, List<EpgProgram>> {
        val result = mutableMapOf<String, MutableList<EpgProgram>>()
        val channelNamesToId = mutableMapOf<String, String>()

        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 25000
            setRequestProperty("User-Agent", "Televizo/1.9.9.44")
        }

        val rawStream: InputStream = conn.inputStream
        val stream: InputStream = if (urlString.endsWith(".gz", ignoreCase = true) ||
            conn.contentEncoding?.contains("gzip", ignoreCase = true) == true
        ) {
            GZIPInputStream(rawStream)
        } else {
            rawStream
        }

        stream.use { input ->
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(input, "UTF-8")

            var eventType = parser.eventType
            var currentChannelId = ""
            var progChannel = ""
            var progStart = 0L
            var progStop = 0L
            var progTitle = ""

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "channel" -> {
                                currentChannelId = parser.getAttributeValue(null, "id") ?: ""
                            }
                            "display-name" -> {
                                val disp = parser.nextText().trim()
                                if (currentChannelId.isNotEmpty() && disp.isNotEmpty()) {
                                    channelNamesToId[disp.lowercase(Locale.getDefault())] = currentChannelId
                                }
                            }
                            "programme" -> {
                                progChannel = parser.getAttributeValue(null, "channel") ?: ""
                                progStart = parseXmltvDate(parser.getAttributeValue(null, "start"))
                                progStop = parseXmltvDate(parser.getAttributeValue(null, "stop"))
                                progTitle = ""
                            }
                            "title" -> {
                                if (progChannel.isNotEmpty()) {
                                    progTitle = parser.nextText().trim()
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "programme" && progChannel.isNotEmpty() && progTitle.isNotEmpty() && progStart > 0) {
                            val list = result.getOrPut(progChannel) { mutableListOf() }
                            list.add(EpgProgram(progChannel, progTitle, progStart, progStop))
                            progChannel = ""
                        } else if (parser.name == "channel") {
                            currentChannelId = ""
                        }
                    }
                }
                eventType = parser.next()
            }
        }

        for ((nameLower, chId) in channelNamesToId) {
            val progs = result[chId]
            if (progs != null && !result.containsKey(nameLower)) {
                result[nameLower] = progs
            }
        }

        result.values.forEach { list -> list.sortBy { it.startMs } }
        return result
    }

    fun buildArchiveUrl(channel: Channel, program: EpgProgram): String {
        val utcStart = program.startMs / 1000L
        val utcEnd = program.endMs / 1000L
        val duration = (utcEnd - utcStart).coerceAtLeast(60L)

        if (channel.catchupSource.isNotEmpty()) {
            val sTag = "\${start}"
            val eTag = "\${end}"
            val dTag = "\${duration}"
            var src = channel.catchupSource
                .replace(sTag, utcStart.toString())
                .replace(eTag, utcEnd.toString())
                .replace(dTag, duration.toString())
                .replace("{utc}", utcStart.toString())
                .replace("{lutc}", (System.currentTimeMillis() / 1000L).toString())
            if (src.startsWith("?") || src.startsWith("&")) {
                val sep = if (channel.url.contains("?")) "&" else "?"
                return channel.url + sep + src.trimStart('?', '&')
            }
            return src
        }

        val sep = if (channel.url.contains("?")) "&" else "?"
        return "${channel.url}${sep}utc=$utcStart&lutc=${System.currentTimeMillis() / 1000L}"
    }

    private fun extractAttr(line: String, attrName: String): String? {
        val pattern = Regex(attrName + "="([^"]*)"")
        return pattern.find(line)?.groupValues?.getOrNull(1)
    }

    private fun parseXmltvDate(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        return try {
            val clean = raw.trim()
            val fmt = if (clean.contains(" ")) {
                SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
            } else {
                SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
            }
            fmt.parse(clean)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    private fun downloadText(urlString: String): String {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("User-Agent", "Televizo/1.9.9.44")
        }
        return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}

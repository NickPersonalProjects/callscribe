package com.nicholaston.callscribe.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class CallRepository(
    private val calls: CallDao,
    private val transcripts: TranscriptDao,
) {
    fun observeCalls(): Flow<List<CallRecord>> = calls.observeAll()

    fun observeCall(id: Long): Flow<CallRecord?> = calls.observe(id)

    fun observeTranscript(callId: Long): Flow<List<TranscriptSegment>> =
        transcripts.observeForCall(callId)

    fun search(query: String): Flow<List<CallWithSnippet>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return calls.searchMetadata("")
        val fts = FtsQuery.fromUserInput(trimmed)
        return combine(calls.searchTranscript(fts), calls.searchMetadata(trimmed)) { transcript, metadata ->
            (metadata + transcript).distinctBy { it.call.id }.sortedByDescending { it.call.startedAt }
        }
    }
}

object FtsQuery {
    fun fromUserInput(input: String): String =
        input.trim()
            .split(Regex("\\s+"))
            .filter(String::isNotBlank)
            .joinToString(" AND ") { token ->
                "\"${token.replace("\"", "\"\"")}\"*"
            }
            .ifEmpty { "\"\"" }
}

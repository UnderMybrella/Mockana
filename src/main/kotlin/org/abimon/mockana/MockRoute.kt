package org.abimon.mockana

import io.ktor.http.HttpMethod

data class MockRoute (
    val method: HttpMethod,
    val path: Regex,
    val response: MockResponse
)

class MockResponse {
    val headers: MutableList<Pair<String, String>> = ArrayList()
    var statusCode = 200
    var body: MockanaOutgoingContent<*> = MockanaOutgoingContent.MockanaTextOutgoingContent("")
}
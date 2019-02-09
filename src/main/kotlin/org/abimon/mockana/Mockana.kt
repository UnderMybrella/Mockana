package org.abimon.mockana

import info.spiralframework.base.properties.cache
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.AutoHeadResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.request.httpMethod
import io.ktor.request.path
import io.ktor.response.header
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.parboiled.parserunners.BasicParseRunner
import java.io.File

object Mockana {
    val parser = MockanaParser()
    val runner = BasicParseRunner<Any>(parser.Mockana())
    val routes: Array<MockRoute> by cache(File("routes.mock")) { file ->
        val result = runner.run(file.readText().replace("\r\n", "\n").replace("\n\r", "\n"))
        if (result.matched)
            return@cache result.valueStack.reversed().filterIsInstance(MockRoute::class.java).toTypedArray()
        return@cache emptyArray<MockRoute>()
    }

    val server = embeddedServer(Netty, 9919) {
        install(AutoHeadResponse)

        routing {
            get("/mockana/debug/routes") {
                call.respondText(routes.joinToString())
            }

            route("{...}") {
                handle {
                    try {
                        val request = call.request
                        val path = request.path()
                        val method = request.httpMethod
                        val route = routes.firstOrNull { route -> route.method == method && route.path.matches(path) }
                            ?: return@handle

                        val httpResponse = call.response
                        val mockResponse = route.response

                        val headers = mockResponse.headers

                        val contentLength = headers.firstOrNull { (key) -> key.equals("Content-Length", true) }
                        if (contentLength != null)
                            headers.remove(contentLength)

                        val contentType = headers.firstOrNull { (key) -> key.equals("Content-Type", true) }
                        if (contentType != null)
                            headers.remove(contentType)

                        if (contentType != null || contentLength != null)
                            mockResponse.body = mockResponse.body.copyOf(contentLength = contentLength?.second?.toLongOrNull(), contentType = contentType?.second?.let(ContentType.Companion::parse))

                        headers.forEach { (key, value) -> httpResponse.header(key, value) }
                        httpResponse.status(HttpStatusCode.fromValue(mockResponse.statusCode))

                        call.respond(route.response.body)
                    } catch (th: Throwable) {
                        th.printStackTrace()
                    }
                }
            }
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        server.start(wait = true)
    }
}
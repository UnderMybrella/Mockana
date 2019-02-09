package org.abimon.mockana

import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.http.defaultForFile
import io.ktor.util.cio.readChannel
import kotlinx.coroutines.io.ByteReadChannel
import java.io.File
import java.io.InputStream

sealed class MockanaOutgoingContent<T>(override val contentLength: Long?, override val contentType: ContentType?): OutgoingContent.ReadChannelContent() {
    class MockanaTextOutgoingContent(val text: String, contentLength: Long? = null, contentType: ContentType? = null): MockanaOutgoingContent<String>(contentLength, contentType) {
        override fun readFrom(): ByteReadChannel = ByteReadChannel(text, Charsets.UTF_8)

        override fun copyOf(data: String?, contentLength: Long?, contentType: ContentType?): MockanaTextOutgoingContent
            = MockanaTextOutgoingContent(data ?: this.text, contentLength ?: this.contentLength, contentType ?: this.contentType)
    }

    class MockanaStreamOutgoingContent(val stream: InputStream, contentLength: Long? = null, contentType: ContentType? = null): MockanaOutgoingContent<InputStream>(contentLength, contentType) {
        val data: ByteArray by lazy { stream.use { stream -> stream.readBytes() } }

        override fun readFrom(): ByteReadChannel = ByteReadChannel(data)

        override fun copyOf(data: InputStream?, contentLength: Long?, contentType: ContentType?): MockanaStreamOutgoingContent
                = MockanaStreamOutgoingContent(data ?: this.stream, contentLength ?: this.contentLength, contentType ?: this.contentType)
    }

    class MockanaFileOutgoingContent(val file: File, contentLength: Long? = null, contentType: ContentType? = null): MockanaOutgoingContent<File>(contentLength ?: file.length(), contentType ?: ContentType.defaultForFile(file)) {
        @Suppress("EXPERIMENTAL_API_USAGE")
        override fun readFrom(): ByteReadChannel = file.readChannel()

        override fun copyOf(data: File?, contentLength: Long?, contentType: ContentType?): MockanaFileOutgoingContent
                = MockanaFileOutgoingContent(data ?: this.file, contentLength ?: this.contentLength, contentType ?: this.contentType)
    }

    abstract fun copyOf(data: T? = null, contentLength: Long? = null, contentType: ContentType? = null): MockanaOutgoingContent<T>
}
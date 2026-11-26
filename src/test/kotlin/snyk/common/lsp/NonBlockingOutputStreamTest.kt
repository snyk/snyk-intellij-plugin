package snyk.common.lsp

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.awaitility.Awaitility.await
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler
import org.eclipse.lsp4j.jsonrpc.json.StreamMessageConsumer
import org.eclipse.lsp4j.jsonrpc.messages.NotificationMessage
import org.junit.After
import org.junit.Test

class NonBlockingOutputStreamTest {
  private val executor = Executor { Thread(it).apply { isDaemon = true }.start() }
  private val release = CountDownLatch(1)

  @After
  fun tearDown() {
    release.countDown()
  }

  @Test(timeout = 5_000)
  fun `sending messages does not block when server stops reading`() {
    val serverInput =
      object : OutputStream() {
        override fun write(b: Int) {
          release.await()
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
          release.await()
        }
      }
    val consumer =
      StreamMessageConsumer(
        NonBlockingOutputStream(serverInput, executor),
        MessageJsonHandler(emptyMap()),
      )

    repeat(3) { consumer.consume(notification("textDocument/didSave")) }
  }

  @Test
  fun `writes reach server in order and are flushed`() {
    val flushes = AtomicInteger()
    val received =
      object : ByteArrayOutputStream() {
        override fun flush() {
          flushes.incrementAndGet()
        }
      }
    val cut = NonBlockingOutputStream(received, executor)

    cut.write("ab".toByteArray())
    cut.write('c'.code)
    cut.write("xdx".toByteArray(), 1, 1)

    await().atMost(5, TimeUnit.SECONDS).until { received.toString() == "abcd" && flushes.get() > 0 }
  }

  @Test
  fun `write fails after server input breaks`() {
    val broken =
      object : OutputStream() {
        override fun write(b: Int) = throw IOException("Broken pipe")
      }
    val cut = NonBlockingOutputStream(broken, executor)

    cut.write(1)

    await().atMost(5, TimeUnit.SECONDS).until { writeFails(cut) }
  }

  @Test
  fun `close closes server input and rejects later writes`() {
    val closed = AtomicBoolean()
    val serverInput =
      object : ByteArrayOutputStream() {
        override fun close() {
          closed.set(true)
        }
      }
    val cut = NonBlockingOutputStream(serverInput, executor)
    cut.write("a".toByteArray())

    cut.close()

    await().atMost(5, TimeUnit.SECONDS).untilTrue(closed)
    assertEquals("a", serverInput.toString())
    assertTrue(writeFails(cut))
  }

  private fun writeFails(stream: OutputStream): Boolean =
    try {
      stream.write(1)
      false
    } catch (_: IOException) {
      true
    }

  private fun notification(method: String) =
    NotificationMessage().apply {
      jsonrpc = "2.0"
      this.method = method
    }
}

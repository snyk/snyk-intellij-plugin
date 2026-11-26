package snyk.common.lsp

import com.intellij.openapi.diagnostic.logger
import java.io.IOException
import java.io.OutputStream
import java.util.Objects
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue

/**
 * Queues writes and passes them to [delegate] on a background thread.
 *
 * LSP4J writes outgoing messages on the caller thread under one lock. When the language server does
 * not read stdin, that write blocks the caller, every other sender, and the thread that reads
 * server output when it answers a server request. Then neither side reads and both hang.
 */
class NonBlockingOutputStream(private val delegate: OutputStream, executor: Executor) :
  OutputStream() {
  private val queue = LinkedBlockingQueue<ByteArray>()
  @Volatile private var closed = false

  init {
    executor.execute(::drain)
  }

  override fun write(b: Int) {
    write(byteArrayOf(b.toByte()), 0, 1)
  }

  override fun write(b: ByteArray, off: Int, len: Int) {
    Objects.checkFromIndexSize(off, len, b.size)
    if (closed) throw IOException("Stream closed")
    if (len == 0) return
    queue.put(b.copyOfRange(off, off + len))
  }

  // Background thread flushes once the queue is empty.
  override fun flush() {}

  override fun close() {
    if (closed) return
    closed = true
    queue.put(CLOSE)
  }

  private fun drain() {
    try {
      while (true) {
        val chunk = queue.take()
        if (chunk === CLOSE) break
        delegate.write(chunk)
        if (queue.isEmpty()) delegate.flush()
      }
    } catch (e: IOException) {
      logger.debug("Writing to Snyk Language Server failed", e)
    } catch (_: InterruptedException) {
      Thread.currentThread().interrupt()
    } finally {
      closed = true
      queue.clear()
      try {
        delegate.close()
      } catch (_: IOException) {
        // process is gone
      }
    }
  }

  companion object {
    private val logger = logger<NonBlockingOutputStream>()
    private val CLOSE = ByteArray(0)
  }
}

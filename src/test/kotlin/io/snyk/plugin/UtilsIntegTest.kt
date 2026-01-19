package io.snyk.plugin

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.messages.Topic
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.Test
import snyk.common.lsp.LanguageServerWrapper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class UtilsIntegTest : BasePlatformTestCase() {

    private lateinit var languageServerWrapperMock: LanguageServerWrapper

    override fun setUp() {
        super.setUp()

        // Mock LanguageServerWrapper to prevent actual LSP initialization
        mockkObject(LanguageServerWrapper.Companion)
        languageServerWrapperMock = mockk(relaxed = true)
        every { LanguageServerWrapper.getInstance(project) } returns languageServerWrapperMock
        justRun { languageServerWrapperMock.dispose() }
        justRun { languageServerWrapperMock.shutdown() }

        resetSettings(project)
    }

    override fun tearDown() {
        unmockkAll()
        resetSettings(project)
        super.tearDown()
    }

    interface TestEventListener {
        fun onTestEvent(message: String)
    }

    @Test
    fun `test publishAsync delivers event to listener`() {
        val eventReceived = AtomicBoolean(false)
        val latch = CountDownLatch(1)
        val receivedMessage = StringBuilder()

        val topic = Topic.create("test.publishAsync.topic", TestEventListener::class.java)

        // Subscribe to the topic
        project.messageBus.connect(testRootDisposable).subscribe(
            topic,
            object : TestEventListener {
                override fun onTestEvent(message: String) {
                    receivedMessage.append(message)
                    eventReceived.set(true)
                    latch.countDown()
                }
            }
        )

        // Publish async
        publishAsync(project, topic) {
            onTestEvent("hello from async")
        }

        // Wait for async delivery
        assertTrue("Event should be delivered within timeout", latch.await(5, TimeUnit.SECONDS))
        assertTrue("Event should have been received", eventReceived.get())
        assertEquals("hello from async", receivedMessage.toString())
    }

    @Test
    fun `test publishAsync handles multiple rapid publishes`() {
        val eventCount = AtomicInteger(0)
        val latch = CountDownLatch(5)

        val topic = Topic.create("test.publishAsync.rapid.topic", TestEventListener::class.java)

        project.messageBus.connect(testRootDisposable).subscribe(
            topic,
            object : TestEventListener {
                override fun onTestEvent(message: String) {
                    eventCount.incrementAndGet()
                    latch.countDown()
                }
            }
        )

        // Publish multiple events rapidly
        repeat(5) { i ->
            publishAsync(project, topic) {
                onTestEvent("event $i")
            }
        }

        // All events should be delivered
        assertTrue("All events should be delivered within timeout", latch.await(5, TimeUnit.SECONDS))
        assertEquals(5, eventCount.get())
    }

    @Test
    fun `test getSyncPublisher returns publisher for valid project`() {
        val topic = Topic.create("test.getSyncPublisher.topic", TestEventListener::class.java)

        val publisher = getSyncPublisher(project, topic)
        assertNotNull("Publisher should not be null for valid project", publisher)
    }

    @Test
    fun `test isCliInstalled returns true in test mode`() {
        // In unit test mode, isCliInstalled should return true
        assertTrue(isCliInstalled())
    }
}

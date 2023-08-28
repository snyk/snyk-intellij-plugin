package snyk.amplitude

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import org.junit.After
import org.junit.Before
import org.junit.Test
import snyk.amplitude.api.AmplitudeExperimentApiClient
import snyk.amplitude.api.ExperimentUser

class AmplitudeExperimentServiceTest {
    private val amplitudeApiClientMock = mockk<AmplitudeExperimentApiClient>()
    private lateinit var cut: AmplitudeExperimentService
    private lateinit var user: ExperimentUser

    @Before
    fun setUp() {
        unmockkAll()
        mockkStatic("io.snyk.plugin.UtilsKt")
        every { pluginSettings() } returns SnykApplicationSettingsStateService()
        user = ExperimentUser("testUser")
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `fetch should call amplitude for data with the userId`() {
        every { amplitudeApiClientMock.allVariants(any()) } returns emptyMap()

        cut = AmplitudeExperimentService()
        cut.setApiClient(amplitudeApiClientMock)
        cut.fetch(user)

        verify(exactly = 1) { amplitudeApiClientMock.allVariants(user) }
    }

    @Test
    fun `fetch should not call amplitude for data with the userId when fedramp`() {
        every { amplitudeApiClientMock.allVariants(any()) } returns emptyMap()
        pluginSettings().customEndpointUrl = "https://api.fedramp.snykgov.io"
        cut = AmplitudeExperimentService()

        cut.fetch(user)

        verify(exactly = 0) { amplitudeApiClientMock.allVariants(user) }
    }
}

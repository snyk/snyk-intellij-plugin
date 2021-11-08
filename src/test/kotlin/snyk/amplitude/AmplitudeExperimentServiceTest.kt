package snyk.amplitude

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Test
import snyk.amplitude.AmplitudeExperimentService.Companion.CHANGE_AUTHENTICATE_BUTTON
import snyk.amplitude.AmplitudeExperimentService.Companion.TEST_GROUP
import snyk.amplitude.api.AmplitudeExperimentApiClient
import snyk.amplitude.api.ExperimentUser
import snyk.amplitude.api.Variant

class AmplitudeExperimentServiceTest {
    private val amplitudeApiClientMock = mockk<AmplitudeExperimentApiClient>()
    private val cut = AmplitudeExperimentService()
    private val user = ExperimentUser("testUser")

    private fun variantMap(value: String): MutableMap<String, Variant> {
        val variant = Variant(value, null)
        val mapEntry = Pair(CHANGE_AUTHENTICATE_BUTTON, variant)
        val expectedVariants = mutableMapOf(mapEntry)
        expectedVariants[CHANGE_AUTHENTICATE_BUTTON] = variant
        return expectedVariants
    }

    private fun stubApi(expectedVariants: MutableMap<String, Variant>) {
        every { amplitudeApiClientMock.allVariants(any()) } returns expectedVariants
        cut.fetch(user)
    }

    @Before
    fun setUp() {
        clearAllMocks()
        cut.setApiClient(amplitudeApiClientMock)
    }

    @Test
    fun `isPartOfExperimentalWelcomeWorkflow should return true, if user is in the test group`() {
        val expectedVariants = variantMap(TEST_GROUP)
        stubApi(expectedVariants)

        val isPartOfExperiment = cut.isPartOfExperimentalWelcomeWorkflow()

        assertTrue("Expected user to be part of test group, but wasn't.", isPartOfExperiment)
    }

    @Test
    fun `isPartOfExperimentalWelcomeWorkflow should return false, if user is in the control group`() {
        val expectedVariants = variantMap("anything that is not test group")
        stubApi(expectedVariants)

        val isPartOfExperiment = cut.isPartOfExperimentalWelcomeWorkflow()
        assertFalse("Expected user to be part of control group, but wasn't.", isPartOfExperiment)
    }

    @Test
    fun `fetch should call amplitude for data with the userId`() {
        every { amplitudeApiClientMock.allVariants(any()) } returns emptyMap()

        cut.fetch(user)

        verify(exactly = 1) { amplitudeApiClientMock.allVariants(user) }
    }

    @Test
    fun `isPartOfExperimentalWelcomeWorkflow should return false, if experiment not found`() {
        assertFalse("Expected control group, but wasn't.", cut.isPartOfExperimentalWelcomeWorkflow())
    }
}

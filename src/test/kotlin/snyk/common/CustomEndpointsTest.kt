package snyk.common

import org.hamcrest.core.IsEqual.equalTo
import org.junit.Assert.assertThat
import org.junit.Test

class CustomEndpointsTest {

    @Test
    fun `resolveCustomEndpoint returns production SaaS url if endpointUrl is null or empty`() {
        val endpointForNull = resolveCustomEndpoint(null)
        val endpointForEmpty = resolveCustomEndpoint("")

        assertThat(endpointForNull, equalTo("https://snyk.io/api"))
        assertThat(endpointForEmpty, equalTo("https://snyk.io/api"))
    }

    @Test
    fun `resolveCustomEndpoint removes all trailing slashes if present`() {
        val endpointWithSingleTrailingSlash = resolveCustomEndpoint("https://on-prem.internal/api")
        val endpointWithMultipleTrailingSlashes = resolveCustomEndpoint("https://on-prem.internal/api///")

        assertThat(endpointWithSingleTrailingSlash, equalTo("https://on-prem.internal/api"))
        assertThat(endpointWithMultipleTrailingSlashes, equalTo("https://on-prem.internal/api"))
    }

    @Test
    fun `isSnykCodeAvailable returns true for SaaS deployments`() {
        val snykCodeAvailableForProduction = isSnykCodeAvailable("https://snyk.io/api")
        val snykCodeAvailableForDevelopment = isSnykCodeAvailable("https://dev.snyk.io/api")

        assertThat(snykCodeAvailableForProduction, equalTo(true))
        assertThat(snykCodeAvailableForDevelopment, equalTo(true))
    }

    @Test
    fun `isSnykCodeAvailable returns true for Single Tenant deployments`() {
        val snykCodeAvailable = isSnykCodeAvailable("https://registry-web.random-uuid.polaris.snyk-internal.net/api")

        assertThat(snykCodeAvailable, equalTo(true))
    }

    @Test
    fun `isSnykCodeAvailable returns false for OnPremises deployments`() {
        val snykCodeAvailable = isSnykCodeAvailable("https://on-prem.internal/api")

        assertThat(snykCodeAvailable, equalTo(false))
    }

    @Test
    fun `toSnykCodeApiUrl returns correct deeproxy url for SaaS deployments`() {
        val apiUrlForProduction = toSnykCodeApiUrl("https://snyk.io/api")
        val apiUrlForDevelopment = toSnykCodeApiUrl("https://dev.snyk.io/api")

        assertThat(apiUrlForProduction, equalTo("https://deeproxy.snyk.io/"))
        assertThat(apiUrlForDevelopment, equalTo("https://deeproxy.dev.snyk.io/"))
    }

    @Test
    fun `toSnykCodeApiUrl returns correct deeproxy url for Single Tenant deployments`() {
        val apiUrl = toSnykCodeApiUrl("https://registry-web.random-uuid.polaris.snyk-internal.net/api")

        assertThat(apiUrl, equalTo("https://deeproxy.random-uuid.polaris.snyk-internal.net/"))
    }

    @Test
    fun `toSnykCodeSettingsUrl returns correct settings url for SaaS deployments`() {
        val productionSettingsUrl = toSnykCodeSettingsUrl("https://snyk.io/api")
        val developmentSettingsUrl = toSnykCodeSettingsUrl("https://dev.snyk.io/api")

        assertThat(productionSettingsUrl, equalTo("https://app.snyk.io/manage/snyk-code"))
        assertThat(developmentSettingsUrl, equalTo("https://app.dev.snyk.io/manage/snyk-code"))
    }

    @Test
    fun `toSnykCodeSettingsUrl returns correct settings url for Single Tenant deployments`() {
        val settingsUrl = toSnykCodeSettingsUrl("https://registry-web.random-uuid.polaris.snyk-internal.net/api")

        assertThat(settingsUrl, equalTo("https://registry-web.random-uuid.polaris.snyk-internal.net/manage/snyk-code"))
    }
}

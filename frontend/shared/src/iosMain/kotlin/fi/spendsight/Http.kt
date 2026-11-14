package fi.spendsight

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSURLAuthenticationMethodServerTrust
import platform.Foundation.NSURLCredential
import platform.Foundation.NSURLSessionAuthChallengePerformDefaultHandling
import platform.Foundation.NSURLSessionAuthChallengeUseCredential
import platform.Foundation.credentialForTrust
import platform.Foundation.serverTrust

/** The deployed URL when the build sets `spendsight.apiUrl` (OPS-10), else this computer. */
actual fun defaultBaseUrl(): String = DEPLOYED_BASE_URL.ifEmpty { "https://127.0.0.1:8000" }

@OptIn(ExperimentalForeignApi::class)
actual fun createHttpClient(): HttpClient = HttpClient(Darwin) {
    engine {
        handleChallenge { _, _, challenge, completionHandler ->
            val space = challenge.protectionSpace
            val trust = space.serverTrust
            val localDev = space.authenticationMethod == NSURLAuthenticationMethodServerTrust &&
                trust != null &&
                isLocalDevHost(space.host)
            if (localDev) {
                completionHandler(
                    NSURLSessionAuthChallengeUseCredential,
                    NSURLCredential.credentialForTrust(trust),
                )
            } else {
                completionHandler(NSURLSessionAuthChallengePerformDefaultHandling, null)
            }
        }
    }
    spendSight()
}

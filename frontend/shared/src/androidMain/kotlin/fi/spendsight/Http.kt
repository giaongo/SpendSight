package fi.spendsight

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

actual fun defaultBaseUrl(): String = "https://10.0.2.2:8000"

actual fun createHttpClient(): HttpClient = HttpClient(OkHttp) {
    engine {
        config {
            val trust = LocalDevTrustManager(systemTrustManager())
            val context = SSLContext.getInstance("TLS")
            context.init(null, arrayOf<TrustManager>(trust), SecureRandom())
            sslSocketFactory(context.socketFactory, trust)
            hostnameVerifier { hostname, session ->
                if (HttpsURLConnection.getDefaultHostnameVerifier().verify(hostname, session)) {
                    true
                } else if (!isLocalDevHost(hostname)) {
                    false
                } else {
                    val leaf = session.peerCertificates.firstOrNull() as? X509Certificate
                    leaf != null && leaf.isSpendSightDevCert()
                }
            }
        }
    }
    spendSight()
}

/**
 * Accepts the system trust store, and also the SpendSight dev certificate
 * (CN or SAN localhost / 127.0.0.1). A public host still has to match its name.
 */
private class LocalDevTrustManager(
    private val system: X509TrustManager,
) : X509TrustManager {
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        system.checkClientTrusted(chain, authType)
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        try {
            system.checkServerTrusted(chain, authType)
        } catch (error: CertificateException) {
            val leaf = chain?.firstOrNull()
            if (leaf != null && leaf.isSpendSightDevCert()) return
            throw error
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = system.acceptedIssuers
}

private fun systemTrustManager(): X509TrustManager {
    val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    factory.init(null as KeyStore?)
    return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
}

private fun X509Certificate.isSpendSightDevCert(): Boolean {
    val names = try {
        subjectAlternativeNames
    } catch (_: CertificateException) {
        null
    }
    if (names != null) {
        for (san in names) {
            val type = (san.getOrNull(0) as? Number)?.toInt()
            val value = san.getOrNull(1)
            val localName = type == 2 && value?.toString()?.equals("localhost", ignoreCase = true) == true
            val localIp = type == 7 && when (value) {
                is String -> value == "127.0.0.1"
                is ByteArray -> value.contentEquals(byteArrayOf(127, 0, 0, 1))
                else -> false
            }
            if (localName || localIp) return true
        }
    }
    return subjectX500Principal.name.contains("CN=localhost", ignoreCase = true)
}

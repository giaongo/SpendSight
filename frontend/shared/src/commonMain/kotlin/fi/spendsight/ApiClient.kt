package fi.spendsight

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Service address. Change this when you run the API somewhere else.
 * `python -m app` serves the API on HTTPS port 8000. `/docs` is the reference only.
 * `GET /session` names this customer's Today, Spending, and Save paths in `reads`.
 * The phone calls those paths. `/docs` is the reference only.
 * This computer or the iOS simulator: https://127.0.0.1:8000
 * Android emulator: https://10.0.2.2:8000 (127.0.0.1 on the emulator is the emulator)
 * A build with `spendsight.apiUrl` set calls that deployed URL instead (see frontend/APP.md).
 * The client trusts the dev certificate only for a local address.
 */
expect fun defaultBaseUrl(): String

var baseUrl: String = defaultBaseUrl()

/** Loopback, the Android emulator's host alias, and private LAN addresses. */
internal fun isLocalDevHost(host: String): Boolean {
    val name = host.trim().lowercase().trimEnd('.')
    if (name == "localhost" || name == "127.0.0.1" || name == "::1" || name == "10.0.2.2") {
        return true
    }
    val parts = name.split('.')
    if (parts.size != 4) return false
    val nums = parts.map { it.toIntOrNull() ?: return false }
    if (nums.any { it !in 0..255 }) return false
    return nums[0] == 10 ||
        (nums[0] == 192 && nums[1] == 168) ||
        (nums[0] == 172 && nums[1] in 16..31)
}

val ApiJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

class ApiStatusException(val status: Int, detail: String) : Exception(detail)

fun HttpClientConfig<*>.spendSight() {
    install(ContentNegotiation) {
        json(ApiJson)
    }
    install(HttpTimeout) {
        connectTimeoutMillis = 8_000
        requestTimeoutMillis = 20_000
    }
    expectSuccess = false
    HttpResponseValidator {
        validateResponse { response ->
            val code = response.status.value
            if (code !in 200..299) {
                throw ApiStatusException(code, apiDetail(response.bodyAsText()))
            }
        }
    }
}

expect fun createHttpClient(): HttpClient

class SpendSightApi(
    private val serviceUrl: String = baseUrl,
    private val client: HttpClient = createHttpClient(),
) {
    suspend fun loadBundle(): WireBundle = coroutineScope {
        val session = get<CustomerSessionDto>("/session")
        val reads = session.reads
        val customerToday = async { get<CustomerTodayDto>(reads.today) }
        val spending = async { get<SpendingDto>(reads.spending) }
        val customerSave = async { save(reads.save, 5) }
        val effort = async { get<EffortSettingsDto>("/settings/effort") }
        val payments = async { get<PaymentsDto>("/payments") }
        val summary = async { get<SummaryDto>("/ledger/summary") }
        val week = async { get<WeekDto>("/tools/week") }
        val spender = async { get<SpenderDto>("/tools/spender") }
        val dining = async { get<DiningDto>("/tools/dining") }
        val impulse = async { get<ImpulseDto>("/tools/impulse") }
        val runway = async { get<RunwayDto>("/tools/runway") }
        val subs = async { get<SubsDto>("/tools/subscriptions") }
        val charts = async { get<ChartsDto>("/charts") }
        val bonus = async { get<BonusDto>("/bonus") }
        val purchase = async { get<PurchaseDto>("/purchase") }
        val watch = async { get<WatchDto>("/watch") }
        val loan = async { loan(220_000, "4.0") }
        WireBundle(
            session = session,
            customerToday = customerToday.await(),
            spending = spending.await(),
            customerSave = customerSave.await(),
            effort = effort.await(),
            payments = payments.await().payments,
            summary = summary.await(),
            week = week.await(),
            spender = spender.await(),
            dining = dining.await(),
            impulse = impulse.await(),
            runway = runway.await(),
            subs = subs.await(),
            charts = charts.await(),
            bonus = bonus.await(),
            purchase = purchase.await(),
            watch = watch.await(),
            loan = loan.await(),
        )
    }

    suspend fun ask(customerId: String, text: String): CoastResponseDto =
        client.post(endpoint("/customers/$customerId/coast/ask")) {
            contentType(ContentType.Application.Json)
            setBody(AskBody(text))
            timeout {
                requestTimeoutMillis = 70_000
                socketTimeoutMillis = 70_000
            }
        }.body()

    suspend fun putEffort(id: String): EffortSettingsDto = put("/settings/effort", EffortBody(id))

    suspend fun refile(id: Int, category: String): PaymentDto =
        post("/payments/$id/filing", FilingBody(category))

    suspend fun spending(path: String, month: String?): SpendingDto =
        client.get(endpoint(path)) {
            if (month != null) parameter("month", month)
        }.body()

    suspend fun save(path: String, years: Int): CustomerSaveDto =
        client.get(endpoint(path)) {
            parameter("years", years)
        }.body()

    suspend fun loan(priceEur: Int, ratePercent: String): LoanDto =
        client.get(endpoint("/loan/sketch")) {
            parameter("price_eur", priceEur)
            parameter("rate_percent", ratePercent)
            parameter("years", 25)
            parameter("down_percent", "10")
        }.body()

    suspend fun deleteThread() {
        client.delete(endpoint("/thread"))
    }

    private suspend inline fun <reified T> get(path: String): T = client.get(endpoint(path)).body()

    private suspend inline fun <reified B, reified T> post(path: String, payload: B): T =
        client.post(endpoint(path)) {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }.body()

    private suspend inline fun <reified B, reified T> put(path: String, payload: B): T =
        client.put(endpoint(path)) {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }.body()

    private fun endpoint(path: String): String {
        val base = serviceUrl.trimEnd('/')
        val tail = path.trimStart('/')
        return "$base/$tail"
    }
}

internal fun apiDetail(text: String): String {
    if (text.isBlank()) return "The service returned an error."
    return try {
        val root = ApiJson.parseToJsonElement(text) as? JsonObject ?: return text
        when (val detail = root["detail"]) {
            is JsonPrimitive -> detail.contentOrNull ?: text
            is JsonArray -> detail.mapNotNull { item ->
                (item as? JsonObject)?.get("msg")?.let { (it as? JsonPrimitive)?.contentOrNull }
            }.joinToString(" ").ifBlank { text }
            else -> text
        }
    } catch (_: Exception) {
        text
    }
}

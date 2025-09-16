package info.varden.hauk.http

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.UnsupportedEncodingException
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.Random
import javax.net.ssl.HttpsURLConnection
import info.varden.hauk.BuildConfig
import info.varden.hauk.Constants
import info.varden.hauk.R
import info.varden.hauk.http.security.CertificateValidationPolicy
import info.varden.hauk.http.security.InsecureHostnameVerifier
import info.varden.hauk.http.security.InsecureTrustManager
import info.varden.hauk.struct.Version
import info.varden.hauk.utils.Log

/**
 * Handles HTTP POST requests using Kotlin Coroutines.
 *
 * @author Marius Lindvall (original author)
 * @author Gemini (conversion to Kotlin Coroutines)
 */
internal class ConnectionThread(private val callback: Callback) {

    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    /**
     * Executes the HTTP request asynchronously using coroutines.
     * The actual network operation is performed on an IO dispatcher,
     * and the callback is run on the Main dispatcher.
     * This method is intended for calls from Kotlin code.
     *
     * @param request The request to send.
     */
    suspend fun execute(request: Request) {
        val response = performRequest(request)
        withContext(Dispatchers.Main) {
            callback.run(response)
        }
    }

    /**
     * Executes the HTTP request from Java code by launching a coroutine.
     * The callback will be invoked on the main thread.
     *
     * @param request The request to send.
     */
    fun executeFromJava(request: Request) {
        coroutineScope.launch {
            // It's important to handle potential exceptions within this coroutine
            // to prevent crashes if 'execute' or 'performRequest' throws.
            // The existing 'performRequest' already catches exceptions and returns them in Response.
            execute(request)
        }
    }

    @Suppress("HardCodedStringLiteral")
    private suspend fun performRequest(req: Request): Response {
        // Perform the network operation on the IO dispatcher
        return withContext(Dispatchers.IO) {
            val seq = Random().nextInt()
            try {
                Log.v("Assigning seq=%s for request %s", seq, req)

                // Configure and open the connection.
                val proxy = req.parameters.proxy
                val url = URL(req.url)
                val client = (if (proxy == null) url.openConnection() else url.openConnection(proxy)) as HttpURLConnection
                if (url.host.endsWith(".onion") && url.protocol == "https") {
                    if (req.parameters.tlsPolicy == CertificateValidationPolicy.DISABLE_TRUST_ANCHOR_ONION) {
                        Log.v("[seq:%s] Setting insecure SSL socket factory for connection to comply with TLS policy", seq)
                        (client as HttpsURLConnection).sslSocketFactory = InsecureTrustManager.getSocketFactory() // Changed
                    } else if (req.parameters.tlsPolicy == CertificateValidationPolicy.DISABLE_ALL_ONION) {
                        Log.v("[seq:%s] Setting insecure SSL socket factory and disabling hostname validation for connection to comply with TLS policy", seq)
                        (client as HttpsURLConnection).sslSocketFactory = InsecureTrustManager.getSocketFactory() // Changed
                        client.hostnameVerifier = InsecureHostnameVerifier()
                    }
                }

                Log.v("[seq:%s] Setting connection parameters", seq)
                client.connectTimeout = req.parameters.timeout
                client.requestMethod = "POST"
                client.setRequestProperty("Accept-Language", Locale.getDefault().language)
                if (req.url.endsWith("/api/create")) {
                    client.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                } else {
                    client.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                }
                client.setRequestProperty("User-Agent", "Hauk/" + BuildConfig.VERSION_NAME + " " + System.getProperty("http.agent"))
                client.doInput = true
                client.doOutput = true

                Log.v("[seq:%s] Writing data to socket", seq)
                val os: OutputStream = client.outputStream
                val writer = BufferedWriter(OutputStreamWriter(os, StandardCharsets.UTF_8))
                if (req.url.endsWith("/api/create")) {
                    val jsonBuilder = StringBuilder("{")
                    var first = true
                    for ((key, value) in req.data) {
                        if (!first) jsonBuilder.append(",") else first = false
                        jsonBuilder.append("\"").append(key).append("\":")
                        jsonBuilder.append("\"").append(value.replace("\"", "\\\"")).append("\"")
                    }
                    jsonBuilder.append("}")
                    writer.write(jsonBuilder.toString())
                } else {
                    writer.write(req.urlEncodedData)
                }
                writer.flush()
                os.close()

                val responseCode = client.responseCode
                Log.v("[seq:%s] Response code for request is %s", seq, responseCode)
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val lines = ArrayList<String>()
                    val br = BufferedReader(InputStreamReader(client.inputStream, StandardCharsets.UTF_8))
                    var line: String?
                    while (br.readLine().also { line = it } != null) {
                        Log.v("[seq:%s] resp += \"%s\"", seq, line)
                        lines.add(line!!)
                    }
                    br.close()
                    Log.v("[seq:%s] Returning success response", seq)
                    Response(null, lines.toTypedArray(), Version(client.getHeaderField(Constants.HTTP_HEADER_HAUK_VERSION)))
                } else {
                    Log.v("[seq:%s] Returning HTTP code failure response", seq)
                    Response(ServerException(String.format(req.context.getString(R.string.err_response_code), responseCode.toString())), null, null)
                }
            } catch (ex: Exception) {
                Log.v("[seq:%s] Returning exception failure response", ex, seq)
                Response(ex, null, null)
            }
        }
    }

    internal class Request(
        internal val context: Context,
        internal val url: String,
        data: Map<String, String>,
        internal val parameters: ConnectionParameters
    ) {
        internal val data: Map<String, String> = data.toMap() // Make a defensive copy

        @get:Throws(UnsupportedEncodingException::class)
        internal val urlEncodedData: String
            get() {
                val sb = StringBuilder()
                var first = true
                for ((key, value) in this.data) {
                    if (first) first = false else sb.append("&")
                    sb.append(URLEncoder.encode(key, StandardCharsets.UTF_8.name()))
                    sb.append("=")
                    sb.append(URLEncoder.encode(value, StandardCharsets.UTF_8.name()))
                }
                return sb.toString()
            }

        override fun toString(): String {
            val body: String = try {
                urlEncodedData
            } catch (e: UnsupportedEncodingException) {
                Log.e("Unsupported encoding used in Request#toString()", e)
                "<exception>"
            }
            return "Request{url=$url,body=$body,params=$parameters}"
        }
    }

    internal data class Response(
        val ex: Exception?,
        val data: Array<String>?,
        val ver: Version?
    ) {
        // Auto-generated equals, hashCode, toString by data class are generally sufficient.
        // Custom implementations are kept here if they were specifically needed.
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Response
            if (ex != other.ex) return false
            if (data != null) {
                if (other.data == null) return false
                if (!data.contentEquals(other.data)) return false
            } else if (other.data != null) return false
            if (ver != other.ver) return false
            return true
        }

        override fun hashCode(): Int {
            var result = ex?.hashCode() ?: 0
            result = 31 * result + (data?.contentHashCode() ?: 0)
            result = 31 * result + (ver?.hashCode() ?: 0)
            return result
        }

        override fun toString(): String {
            return "Response(ex=$ex, data=${data?.contentToString()}, ver=$ver)"
        }
    }

    fun interface Callback {
        fun run(resp: Response)
    }
}

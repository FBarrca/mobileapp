package coredevices.ring.endpoints
actual object CustomEndpoints {
    actual fun read() = EndpointSettings()
    actual fun save(settings: EndpointSettings) { error("Custom endpoints are available on Android") }
    actual suspend fun chat(profile: EndpointProfile, json: String): String = error("Android only")
    actual suspend fun speech(profile: EndpointProfile, wav: ByteArray, language: String?): String = error("Android only")
}

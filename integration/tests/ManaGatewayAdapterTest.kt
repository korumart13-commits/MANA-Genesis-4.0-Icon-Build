package com.mana.os.integration

import org.junit.Assert.*
import org.junit.Test

class ManaGatewayAdapterTest {
    @Test
    fun configRequiresExactHttpsAllowlistedHostAndRoute() {
        ManaGatewayConfig("https://gateway.example/v1/mana/respond", "gateway.example")
        assertRejected { ManaGatewayConfig("http://gateway.example/v1/mana/respond", "gateway.example") }
        assertRejected { ManaGatewayConfig("https://evil.example/v1/mana/respond", "gateway.example") }
        assertRejected { ManaGatewayConfig("https://gateway.example/v1/mana/respond?token=x", "gateway.example") }
        assertRejected { ManaGatewayConfig("https://gateway.example/other", "gateway.example") }
        assertRejected { ManaGatewayConfig("https://user:pass@gateway.example/v1/mana/respond", "gateway.example") }
    }

    @Test
    fun missingDeviceCredentialFailsBeforeNetwork() {
        val transport = RecordingTransport()
        val model = ManaGatewayReasoningModel(
            ManaGatewayConfig("https://gateway.example/v1/mana/respond", "gateway.example"),
            ManaGatewayCredentialProvider { null }, transport, StubCodec()
        )
        try {
            model.decide(sampleContext(), ManaRuntimeControl.NEVER_STOP)
            fail("Expected missing credential to fail closed.")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("authorization"))
        }
        assertFalse(transport.called)
    }

    @Test
    fun malformedDeviceTokenIsRejectedBeforeNetwork() {
        val transport = RecordingTransport()
        val model = ManaGatewayReasoningModel(
            ManaGatewayConfig("https://gateway.example/v1/mana/respond", "gateway.example"),
            ManaGatewayCredentialProvider { "token\\r\\nInjected: yes" }, transport, StubCodec()
        )
        try {
            model.decide(sampleContext(), ManaRuntimeControl.NEVER_STOP)
            fail("Expected malformed token to fail closed.")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("authorization"))
        }
        assertFalse(transport.called)
    }

    @Test
    fun nonSuccessHttpStatusDoesNotReachModelDecoder() {
        val codec = StubCodec()
        val transport = RecordingTransport(ManaGatewayHttpResponse(503, "unavailable"))
        val model = ManaGatewayReasoningModel(
            ManaGatewayConfig("https://gateway.example/v1/mana/respond", "gateway.example"),
            ManaGatewayCredentialProvider { "short-lived-device-token" }, transport, codec
        )
        try {
            model.decide(sampleContext(), ManaRuntimeControl.NEVER_STOP)
            fail("Expected gateway failure.")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("HTTP 503"))
        }
        assertFalse(codec.decoded)
    }

    @Test
    fun stopRequestedBeforeCallPreventsNetwork() {
        val transport = RecordingTransport()
        val model = ManaGatewayReasoningModel(
            ManaGatewayConfig("https://gateway.example/v1/mana/respond", "gateway.example"),
            ManaGatewayCredentialProvider { "device-token" }, transport, StubCodec()
        )
        val stop = ManaCancellationSignal().apply { requestStop() }
        try {
            model.decide(sampleContext(), stop)
            fail("Expected cancellation.")
        } catch (_: IllegalStateException) { }
        assertFalse(transport.called)
    }

    private fun assertRejected(block: () -> Unit) {
        try { block(); fail("Expected invalid gateway configuration.") }
        catch (_: IllegalArgumentException) { }
    }

    private fun sampleContext() = ManaCognitiveContext(
        userInput = "Привет", conversationId = "test", cycle = 1,
        recalledMemories = emptyList(), observations = emptyList(), availableTools = emptyList()
    )

    private class RecordingTransport(
        private val response: ManaGatewayHttpResponse = ManaGatewayHttpResponse(200, "{}")
    ) : ManaGatewayHttpTransport {
        var called = false
        override fun post(config: ManaGatewayConfig, bearerToken: String, body: String, control: ManaRuntimeControl): ManaGatewayHttpResponse {
            called = true
            assertTrue(bearerToken.isNotBlank())
            return response
        }
    }

    private class StubCodec : ManaGatewayJsonCodec {
        var decoded = false
        override fun encode(context: ManaCognitiveContext) = "request"
        override fun decode(responseBody: String): ManaCognitiveDecision {
            decoded = true
            return ManaCognitiveDecision(finalAnswer = "ok")
        }
    }
}

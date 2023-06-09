import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import kotlin.test.assertContentEquals

class KeepSuffixTest {
    private val suffixes = getPublicSuffixBytes()

    // Readable output when test fails
    private fun readable(domain: List<ByteArray>): String {
        return domain.joinToString(".") { b -> b.decodeToString() }
    }

    private fun assertEquals(
        expected: List<ByteArray>,
        actual: List<ByteArray>
    ) {
        assertEquals(readable(actual), readable(expected))
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (expectedB, actualB) ->
            assertContentEquals(expectedB, actualB)
        }
    }

    // Necessary because ByteArrays are compared by reference
    private fun assert(
        expected: Pair<Boolean, List<ByteArray>>?,
        actual: Pair<Boolean, List<ByteArray>>?
    ) {
        if (expected == null || actual == null) {
            val expectedValue = expected?.second.orEmpty()
            val actualValue = actual?.second.orEmpty()
            assertEquals(readable(expectedValue), readable(actualValue))
            assertEquals(expected, actual)
        } else {
            assertEquals(expected.first, actual.first)
            assertEquals(expected.second, actual.second)
        }
    }

    private fun toDomain(domain: String): List<ByteArray> {
        return domain.split('.')
            .map { s -> s.encodeToByteArray() } + byteArrayOf()
    }

    @Test
    fun validTLDs() {
        listOf("nl", "xn--j6w193g", "xn--o3cw4h").forEach { tld ->
            assert(
                Pair(false, listOf(tld.encodeToByteArray(), byteArrayOf())),
                anonymizeDomain(listToDomainName(toDomain(tld)), suffixes)
            )
        }
    }

    @Test
    fun invalidTLDs() {
        listOf("invalid", "test", "a").forEach { tld ->
            assert(
                null,
                anonymizeDomain(listToDomainName(toDomain(tld)), suffixes)
            )
        }
    }

    @Test
    fun validSuffix() {
        listOf(
            "dh.bytemark.co.uk",
            "co.uk",
            "xn--wcvs22d.xn--j6w193g"
        ).forEach { suffix ->
            assert(
                Pair(false, toDomain(suffix)),
                anonymizeDomain(listToDomainName(toDomain(suffix)), suffixes)
            )
        }
    }

    @Test
    fun subdomainOfValidSuffix() {
        listOf(
            "dh.bytemark.co.uk",
            "co.uk",
            "xn--wcvs22d.xn--j6w193g",
            "com"
        ).forEach { suffix ->
            assert(
                Pair(true, toDomain(suffix)),
                anonymizeDomain(listToDomainName(toDomain("abc.$suffix")), suffixes)
            )
        }
    }

    @Test
    fun longerSubdomainOfValidSuffix() {
        listOf(
            "s3.dualstack.ap-northeast-1.amazonaws.com",
            "xn--mgba3a4fra.ir",
            "xn--wcvs22d.xn--j6w193g",
            "xn--2scrj9c"
        ).forEach { suffix ->
            assert(
                Pair(true, toDomain(suffix)),
                anonymizeDomain(
                    listToDomainName(toDomain("a.b.c.test.$suffix")),
                    suffixes
                )
            )
        }
    }


    @Test
    fun noRoot() {
        assert(
            null,
            anonymizeDomain(
                byteArrayOf(2) + "na".encodeToByteArray() + byteArrayOf(1) + "n".encodeToByteArray(),
                suffixes
            )
        )
    }
}

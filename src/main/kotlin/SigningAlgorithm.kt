enum class SigningAlgorithm {
    RSA, ECDSA_P_256, ECDSA_P_384, Ed25519, Ed448
}

fun toSigningAlgorithm(algo: UByte): SigningAlgorithm {
    return when (algo.toInt()) {
        5 -> SigningAlgorithm.RSA
        7 -> SigningAlgorithm.RSA
        8 -> SigningAlgorithm.RSA
        10 -> SigningAlgorithm.RSA
        13 -> SigningAlgorithm.ECDSA_P_256
        14 -> SigningAlgorithm.ECDSA_P_384
        15 -> SigningAlgorithm.Ed25519
        16 -> SigningAlgorithm.Ed448
        else -> throw NotImplementedError()
    }
}

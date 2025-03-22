package dev.andrewohara.cats

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTDecodeException
import com.auth0.jwt.exceptions.SignatureVerificationException

class Authorizer(
    issuer: String,
    audience: String,
    algorithm: Algorithm
) {
    private val verifier = JWT.require(algorithm)
        .withIssuer(issuer)
        .withAudience(audience)
        .build()

    fun verify(token: String): String? {
        return try {
            verifier.verify(token).subject
        } catch (e: JWTDecodeException) {
            null
        } catch (e: SignatureVerificationException) {
            null
        }
    }
}
package dev.andrewohara.cats

import com.nimbusds.jose.proc.BadJOSEException
import com.nimbusds.jose.proc.JWSKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import java.text.ParseException

class Authorizer(
    issuer: String,
    audience: List<String>,
    keySelector: JWSKeySelector<SecurityContext>
) {
    private val processor = DefaultJWTProcessor<SecurityContext>().apply {
        jwtClaimsSetVerifier = DefaultJWTClaimsVerifier(
            JWTClaimsSet.Builder().issuer(issuer).audience(audience).build(),
            emptySet()
        )
        jwsKeySelector = keySelector
    }

    fun verify(token: String): String? {
        return try {
            processor.process(token, null).subject
        } catch (e: ParseException) {
            null
        } catch (e: BadJOSEException) {
            null
        }
    }
}
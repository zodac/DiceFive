package net.zodac.dicefive.purchases

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Play's signature on a purchase: an SHA1withRSA signature of the purchase's JSON ([com.android.billingclient.api.Purchase.getOriginalJson])
 * by the app's licence key, Play Console's Base64 X.509 RSA public key ([net.zodac.dicefive.BuildConfig.PLAY_LICENCE_KEY]).
 */
internal object PurchaseSignature {

    /** Whether [signature] (Base64) is [licenceKey]'s on [signedData]. False for a key or signature that can't be read. */
    fun isValid(licenceKey: String, signedData: String, signature: String): Boolean = runCatching {
        val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(licenceKey)))
        Signature.getInstance("SHA1withRSA").run {
            initVerify(key)
            update(signedData.toByteArray(Charsets.UTF_8))
            verify(Base64.getDecoder().decode(signature))
        }
    }.getOrDefault(false)
}

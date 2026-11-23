package com.autocall.app.call

import android.net.Uri

/**
 * Encoding for legacy Telephony gateway URI extras.
 *
 * AOSP rebuilds [com.android.phone.extra.GATEWAY_URI] with [Uri.parse]. A raw `#` in the
 * string starts the URI fragment, which truncates bridge/DTMF strings (pauses + `#`).
 * Percent-encode `#` (and `%`) in the SSP for that string only; keep the live dial [Uri]
 * untouched.
 */
object GatewayUriLogic {

    fun legacyGatewayUriString(uri: Uri): String {
        val scheme = uri.scheme?.takeIf { it.isNotBlank() } ?: "tel"
        val ssp = uri.schemeSpecificPart ?: return uri.toString()
        val encodedSsp = ssp.replace("%", "%25").replace("#", "%23")
        return "$scheme:$encodedSsp"
    }
}

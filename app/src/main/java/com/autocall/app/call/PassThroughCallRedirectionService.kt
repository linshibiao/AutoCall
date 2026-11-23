package com.autocall.app.call

import android.net.Uri
import android.telecom.CallRedirectionService
import android.telecom.PhoneAccountHandle
import android.util.Log

/**
 * No-op call redirection: always places the dial unmodified.
 *
 * Google Voice holds [android.app.role.CALL_REDIRECTION] on many devices and rewrites
 * outbound dials even when [TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE] pins the SIM.
 * When AutoCall holds the call-redirection role instead, Telecom binds this service and
 * SIM-pinned dials (including pause/# bridge strings) are left alone.
 */
class PassThroughCallRedirectionService : CallRedirectionService() {

    override fun onPlaceCall(
        handle: Uri,
        initialPhoneAccount: PhoneAccountHandle,
        allowInteractiveResponse: Boolean,
    ) {
        Log.i(
            TAG,
            "Passing call through unmodified " +
                "account=${initialPhoneAccount.componentName.packageName} " +
                "interactive=$allowInteractiveResponse",
        )
        placeCallUnmodified()
    }

    companion object {
        private const val TAG = "PassThroughCallRedir"
    }
}

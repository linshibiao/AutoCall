package com.autocall.app.call

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat

object CallLauncher {

    fun buildTelUri(phoneNumber: String): Uri =
        Uri.fromParts("tel", phoneNumber, null)

    fun placeCall(
        context: Context,
        phoneNumber: String,
        useSpeakerphone: Boolean = false,
        forceSimDial: Boolean = false,
    ): Boolean {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "CALL_PHONE missing; cannot placeCall")
            return false
        }

        val uri = buildTelUri(phoneNumber)
        val appContext = context.applicationContext
        val simHandle = if (forceSimDial) {
            SimDialAccountResolver.resolveSimHandle(appContext)
        } else {
            null
        }

        if (forceSimDial && simHandle == null) {
            Log.w(
                TAG,
                "forceSimDial set but no SIM account resolved; placing without account pin",
            )
            showToast(
                appContext,
                "SIM dial: no cellular account pin found; placing with default account",
            )
        }

        // Prefer TelecomManager.placeCall — never shows the Voice/Phone chooser.
        // Gateway extras ask Telecom to skip the user CallRedirectionService (GV) for
        // this dial only, so normal Phone-app calls can still use GV redirection.
        if (forceSimDial) {
            val viaTelecom = placeCallViaTelecom(
                context = appContext,
                uri = uri,
                useSpeakerphone = useSpeakerphone,
                phoneAccountHandle = simHandle,
                skipUserCallRedirection = true,
            )
            if (viaTelecom) return true
            Log.w(TAG, "telecom.placeCall with gateway failed; trying dialer-targeted ACTION_CALL")
            val viaDialer = placeCallViaActionCall(
                context = appContext,
                uri = uri,
                useSpeakerphone = useSpeakerphone,
                phoneAccountHandle = simHandle,
                skipUserCallRedirection = true,
            )
            if (viaDialer) return true
            Log.w(TAG, "Gateway dial paths failed; falling back without gateway tag")
        }

        if (simHandle != null) {
            val pinned = placeCallViaTelecom(
                context = appContext,
                uri = uri,
                useSpeakerphone = useSpeakerphone,
                phoneAccountHandle = simHandle,
                skipUserCallRedirection = false,
            )
            if (pinned) return true
            Log.w(TAG, "Pinned SIM placeCall failed; retrying without account pin")
        }

        return placeCallViaTelecom(
            context = appContext,
            uri = uri,
            useSpeakerphone = useSpeakerphone,
            phoneAccountHandle = null,
            skipUserCallRedirection = false,
        )
    }

    private fun placeCallViaTelecom(
        context: Context,
        uri: Uri,
        useSpeakerphone: Boolean,
        phoneAccountHandle: PhoneAccountHandle?,
        skipUserCallRedirection: Boolean,
    ): Boolean {
        val extras = Bundle().apply {
            if (useSpeakerphone) {
                putBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, true)
            }
            if (phoneAccountHandle != null) {
                putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccountHandle)
            }
            if (skipUserCallRedirection) {
                putGatewayExtras(this, context.packageName, uri)
            }
        }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val telecomManager = context.getSystemService(TelecomManager::class.java)
                if (telecomManager != null) {
                    telecomManager.placeCall(uri, extras)
                    Log.i(
                        TAG,
                        "telecom.placeCall ok pin=${phoneAccountHandle != null} " +
                            "skipUserRedir=$skipUserCallRedirection",
                    )
                    return true
                }
            }
            placeCallViaActionCall(
                context = context,
                uri = uri,
                useSpeakerphone = useSpeakerphone,
                phoneAccountHandle = phoneAccountHandle,
                skipUserCallRedirection = skipUserCallRedirection,
            )
        } catch (error: Exception) {
            Log.e(TAG, "placeCallViaTelecom failed pin=${phoneAccountHandle != null}", error)
            false
        }
    }

    /**
     * Last-resort path. Always targets the default dialer package so Android does not show
     * "Complete action using Voice / Phone".
     */
    private fun placeCallViaActionCall(
        context: Context,
        uri: Uri,
        useSpeakerphone: Boolean,
        phoneAccountHandle: PhoneAccountHandle?,
        skipUserCallRedirection: Boolean,
    ): Boolean {
        return try {
            val telecomManager = context.getSystemService(TelecomManager::class.java)
            val dialerPackage = telecomManager?.defaultDialerPackage
                ?.takeIf { it.isNotBlank() && it != context.packageName }

            val callIntent = Intent(Intent.ACTION_CALL, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, useSpeakerphone)
                if (phoneAccountHandle != null) {
                    putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccountHandle)
                }
                if (skipUserCallRedirection) {
                    putGatewayExtras(this, context.packageName, uri)
                }
                if (dialerPackage != null) {
                    setPackage(dialerPackage)
                }
            }
            context.startActivity(callIntent)
            Log.i(
                TAG,
                "ACTION_CALL started dialer=$dialerPackage pin=${phoneAccountHandle != null} " +
                    "skipUserRedir=$skipUserCallRedirection",
            )
            true
        } catch (error: Exception) {
            Log.e(TAG, "ACTION_CALL failed", error)
            false
        }
    }

    private fun putGatewayExtras(bundle: Bundle, packageName: String, uri: Uri) {
        // Identity gateway: dial the same URI, but mark as gateway so Telecom skips
        // the user-defined CallRedirectionService (Google Voice) for this call only.
        // Encode # in the legacy string so Uri.parse does not truncate at fragment.
        val legacyGateway = GatewayUriLogic.legacyGatewayUriString(uri)
        bundle.putString(LEGACY_GATEWAY_PROVIDER_PACKAGE, packageName)
        bundle.putString(LEGACY_GATEWAY_URI, legacyGateway)
        bundle.putString(TelecomManager.GATEWAY_PROVIDER_PACKAGE, packageName)
        bundle.putParcelable(TelecomManager.GATEWAY_ORIGINAL_ADDRESS, uri)
    }

    private fun putGatewayExtras(intent: Intent, packageName: String, uri: Uri) {
        val legacyGateway = GatewayUriLogic.legacyGatewayUriString(uri)
        intent.putExtra(LEGACY_GATEWAY_PROVIDER_PACKAGE, packageName)
        intent.putExtra(LEGACY_GATEWAY_URI, legacyGateway)
        intent.putExtra(TelecomManager.GATEWAY_PROVIDER_PACKAGE, packageName)
        intent.putExtra(TelecomManager.GATEWAY_ORIGINAL_ADDRESS, uri)
    }

    private fun showToast(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    // Legacy Telephony extras still read by Telecom's NewOutgoingCallIntentBroadcaster.
    private const val LEGACY_GATEWAY_PROVIDER_PACKAGE =
        "com.android.phone.extra.GATEWAY_PROVIDER_PACKAGE"
    private const val LEGACY_GATEWAY_URI = "com.android.phone.extra.GATEWAY_URI"

    private const val TAG = "CallLauncher"
}

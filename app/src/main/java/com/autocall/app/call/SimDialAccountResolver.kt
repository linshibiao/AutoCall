package com.autocall.app.call

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import com.autocall.app.util.PermissionHelper

object SimDialAccountResolver {

    @SuppressLint("MissingPermission")
    fun resolveSimHandle(context: Context): PhoneAccountHandle? {
        if (!PermissionHelper.hasReadPhoneStatePermission(context)) {
            Log.w(TAG, "READ_PHONE_STATE missing; cannot list phone accounts for SIM dial")
            return null
        }
        val telecom = context.getSystemService(TelecomManager::class.java) ?: return null

        val defaultOutgoing = runCatching {
            telecom.getDefaultOutgoingPhoneAccount(PhoneAccount.SCHEME_TEL)
        }.onFailure {
            Log.w(TAG, "getDefaultOutgoingPhoneAccount failed", it)
        }.getOrNull()

        val handles = runCatching { telecom.callCapablePhoneAccounts }
            .onFailure { Log.e(TAG, "callCapablePhoneAccounts failed", it) }
            .getOrNull()
            .orEmpty()

        val candidates = handles.mapNotNull { handle ->
            toCandidate(telecom, handle)
        }

        val defaultOutgoingId = defaultOutgoing?.let { handleId(it) }
        val selected = SimDialAccountLogic.selectSimAccount(
            candidates = candidates,
            defaultOutgoingId = defaultOutgoingId,
        )

        if (selected != null) {
            val match = handles.firstOrNull { handleId(it) == selected.id }
            if (match != null) {
                logSelected(selected)
                return match
            }
        }

        // On some Android builds callCapablePhoneAccounts is empty/filtered for non-dialer
        // apps even when a SIM account is the default tel outgoing handle. Prefer that.
        val defaultCandidate = defaultOutgoing?.let { toCandidate(telecom, it) }
        if (
            defaultOutgoing != null &&
            defaultCandidate != null &&
            SimDialAccountLogic.looksLikeTelephony(defaultCandidate)
        ) {
            logSelected(defaultCandidate)
            return defaultOutgoing
        }

        Log.w(
            TAG,
            "No SIM/telephony account matched " +
                "(capable=${candidates.size}, default=${defaultCandidate?.packageName})",
        )
        return null
    }

    @SuppressLint("MissingPermission")
    private fun toCandidate(
        telecom: TelecomManager,
        handle: PhoneAccountHandle,
    ): SimDialAccountLogic.AccountCandidate? {
        val account = runCatching { telecom.getPhoneAccount(handle) }.getOrNull()
            ?: return null
        return SimDialAccountLogic.AccountCandidate(
            id = handleId(handle),
            packageName = handle.componentName.packageName,
            className = handle.componentName.className,
            label = account.label?.toString().orEmpty(),
            isSimSubscription = hasSimSubscriptionCapability(account),
            isEnabled = account.isEnabled,
        )
    }

    private fun logSelected(selected: SimDialAccountLogic.AccountCandidate) {
        Log.i(
            TAG,
            "Selected SIM dial account ${selected.packageName}/${selected.className} " +
                "label=\"${selected.label}\" simCap=${selected.isSimSubscription}",
        )
    }

    private fun hasSimSubscriptionCapability(account: PhoneAccount): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return account.hasCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)
    }

    private fun handleId(handle: PhoneAccountHandle): String =
        "${handle.componentName.flattenToString()}#${handle.id}"

    private const val TAG = "SimDialAccountResolver"
}

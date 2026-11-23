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
        val handles = runCatching { telecom.callCapablePhoneAccounts }.getOrNull().orEmpty()
        if (handles.isEmpty()) {
            Log.w(TAG, "No call-capable phone accounts")
            return null
        }

        val candidates = handles.mapNotNull { handle ->
            val account = runCatching { telecom.getPhoneAccount(handle) }.getOrNull()
                ?: return@mapNotNull null
            SimDialAccountLogic.AccountCandidate(
                id = handleId(handle),
                packageName = handle.componentName.packageName,
                className = handle.componentName.className,
                label = account.label?.toString().orEmpty(),
                isSimSubscription = hasSimSubscriptionCapability(account),
                isEnabled = account.isEnabled,
            )
        }

        val defaultOutgoing = runCatching {
            telecom.getDefaultOutgoingPhoneAccount("tel")
        }.getOrNull()

        val selected = SimDialAccountLogic.selectSimAccount(
            candidates = candidates,
            defaultOutgoingId = defaultOutgoing?.let { handleId(it) },
        ) ?: run {
            Log.w(TAG, "No SIM/telephony account matched among ${candidates.size} accounts")
            return null
        }

        val match = handles.firstOrNull { handleId(it) == selected.id }
        if (match != null) {
            Log.i(
                TAG,
                "Selected SIM dial account ${selected.packageName}/${selected.className} " +
                    "label=\"${selected.label}\" simCap=${selected.isSimSubscription}",
            )
        }
        return match
    }

    private fun hasSimSubscriptionCapability(account: PhoneAccount): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return account.hasCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)
    }

    private fun handleId(handle: PhoneAccountHandle): String =
        "${handle.componentName.flattenToString()}#${handle.id}"

    private const val TAG = "SimDialAccountResolver"
}

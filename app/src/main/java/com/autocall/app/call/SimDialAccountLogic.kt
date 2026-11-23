package com.autocall.app.call

/**
 * Pure heuristics for picking a SIM / telephony [PhoneAccount] among call-capable accounts.
 */
object SimDialAccountLogic {

    data class AccountCandidate(
        val id: String,
        val packageName: String,
        val className: String = "",
        val label: String = "",
        val isSimSubscription: Boolean = false,
        val isEnabled: Boolean = true,
    )

    /**
     * Prefer an enabled SIM-subscription account; otherwise a telephony-looking package.
     * When several telephony accounts exist, prefer the default outgoing handle if it qualifies.
     */
    fun selectSimAccount(
        candidates: List<AccountCandidate>,
        defaultOutgoingId: String? = null,
    ): AccountCandidate? {
        val enabled = candidates.filter { it.isEnabled }
        if (enabled.isEmpty()) return null

        val simAccounts = enabled.filter { it.isSimSubscription }
        if (simAccounts.isNotEmpty()) {
            return simAccounts.firstOrNull { it.id == defaultOutgoingId } ?: simAccounts.first()
        }

        val telephony = enabled.filter { looksLikeTelephony(it) }
        if (telephony.isEmpty()) return null
        return telephony.firstOrNull { it.id == defaultOutgoingId } ?: telephony.first()
    }

    fun looksLikeTelephony(candidate: AccountCandidate): Boolean {
        val pkg = candidate.packageName.lowercase()
        val cls = candidate.className.lowercase()
        val label = candidate.label.lowercase()
        // Reject known OTT / GV-style packages before weaker label heuristics.
        if (isLikelyOverTheTop(pkg)) return false
        if (candidate.isSimSubscription) return true
        if (pkg == "com.android.phone" || pkg == "com.android.server.telecom") return true
        if (pkg.contains("telephony")) return true
        if (cls.contains("telephony") || cls.contains("gstk") || cls.contains("sim")) return true
        // Common carrier / OEM telephony connection services
        if (pkg.startsWith("com.samsung") && (cls.contains("phone") || pkg.contains("phone"))) {
            return true
        }
        if (label.contains("sim") || label.contains("mobile") || label.contains("carrier")) {
            return true
        }
        return false
    }

    fun isLikelyOverTheTop(packageName: String): Boolean {
        val pkg = packageName.lowercase()
        return pkg.contains("googlevoice") ||
            pkg.contains("google.android.apps.googlevoice") ||
            pkg.contains("com.google.android.apps.tachyon") || // Duo/Meet remnants
            pkg.contains("whatsapp") ||
            pkg.contains("telegram") ||
            pkg.contains("skype") ||
            pkg.contains("viber")
    }
}

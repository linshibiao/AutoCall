package com.autocall.app.util

/**
 * Pure helpers for multi-number contact picks.
 */
object ContactPhoneLogic {

    data class PhoneOption(
        val number: String,
        val label: String,
    )

    data class ContactPhones(
        val name: String?,
        val phones: List<PhoneOption>,
    )

    /**
     * Keep first occurrence of each trimmed number (query should already prefer primary).
     */
    fun dedupePhones(phones: List<PhoneOption>): List<PhoneOption> {
        val seen = LinkedHashSet<String>()
        return phones.mapNotNull { option ->
            val trimmed = option.number.trim()
            if (trimmed.isEmpty() || !seen.add(trimmed)) {
                null
            } else {
                option.copy(number = trimmed, label = option.label.trim().ifEmpty { "Phone" })
            }
        }
    }

    /** Single number can be applied immediately; multiple require a chooser. */
    fun needsChooser(phones: List<PhoneOption>): Boolean = phones.size > 1

    fun formatOptionLine(option: PhoneOption): String =
        if (option.label.isBlank()) option.number else "${option.label}: ${option.number}"
}

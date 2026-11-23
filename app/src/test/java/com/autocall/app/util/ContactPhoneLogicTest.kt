package com.autocall.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactPhoneLogicTest {

    @Test
    fun dedupePhones_keepsFirstOccurrenceAndTrims() {
        val deduped = ContactPhoneLogic.dedupePhones(
            listOf(
                ContactPhoneLogic.PhoneOption(" 555-0100 ", "Mobile"),
                ContactPhoneLogic.PhoneOption("555-0100", "Work"),
                ContactPhoneLogic.PhoneOption("555-0199", "Home"),
                ContactPhoneLogic.PhoneOption("  ", "Empty"),
            ),
        )
        assertEquals(
            listOf(
                ContactPhoneLogic.PhoneOption("555-0100", "Mobile"),
                ContactPhoneLogic.PhoneOption("555-0199", "Home"),
            ),
            deduped,
        )
    }

    @Test
    fun needsChooser_onlyWhenMoreThanOne() {
        assertFalse(
            ContactPhoneLogic.needsChooser(
                listOf(ContactPhoneLogic.PhoneOption("1", "Mobile")),
            ),
        )
        assertTrue(
            ContactPhoneLogic.needsChooser(
                listOf(
                    ContactPhoneLogic.PhoneOption("1", "Mobile"),
                    ContactPhoneLogic.PhoneOption("2", "Work"),
                ),
            ),
        )
    }

    @Test
    fun formatOptionLine_includesLabelWhenPresent() {
        assertEquals(
            "Mobile: 555-0100",
            ContactPhoneLogic.formatOptionLine(
                ContactPhoneLogic.PhoneOption("555-0100", "Mobile"),
            ),
        )
        assertEquals(
            "555-0100",
            ContactPhoneLogic.formatOptionLine(
                ContactPhoneLogic.PhoneOption("555-0100", ""),
            ),
        )
    }
}

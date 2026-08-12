package com.autocall.app.util

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract

object ContactPickerHelper {

    data class PickedContact(
        val name: String?,
        val phoneNumber: String,
    )

    fun resolveContact(context: Context, contactUri: Uri): PickedContact? {
        val contentResolver = context.contentResolver

        val name = contentResolver.query(
            contactUri,
            arrayOf(ContactsContract.Contacts.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(0)
            } else {
                null
            }
        }

        val contactId = contentResolver.query(
            contactUri,
            arrayOf(ContactsContract.Contacts._ID),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: return null

        val phoneNumber = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId),
            "${ContactsContract.CommonDataKinds.Phone.IS_PRIMARY} DESC",
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: return null

        return PickedContact(name = name, phoneNumber = phoneNumber.trim())
    }
}

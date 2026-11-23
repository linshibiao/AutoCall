package com.autocall.app.util

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract

object ContactPickerHelper {

    fun resolveContact(context: Context, contactUri: Uri): ContactPhoneLogic.ContactPhones? {
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

        val phones = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.TYPE,
                ContactsContract.CommonDataKinds.Phone.LABEL,
            ),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId),
            "${ContactsContract.CommonDataKinds.Phone.IS_PRIMARY} DESC, " +
                "${ContactsContract.CommonDataKinds.Phone._ID} ASC",
        )?.use { cursor ->
            val numberIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val typeIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE)
            val labelIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.LABEL)
            buildList {
                while (cursor.moveToNext()) {
                    val number = cursor.getString(numberIdx)?.trim().orEmpty()
                    if (number.isEmpty()) continue
                    val type = cursor.getInt(typeIdx)
                    val customLabel = cursor.getString(labelIdx)
                    val label = ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                        context.resources,
                        type,
                        customLabel,
                    ).toString()
                    add(ContactPhoneLogic.PhoneOption(number = number, label = label))
                }
            }
        }.orEmpty()

        val deduped = ContactPhoneLogic.dedupePhones(phones)
        if (deduped.isEmpty()) return null
        return ContactPhoneLogic.ContactPhones(name = name, phones = deduped)
    }
}

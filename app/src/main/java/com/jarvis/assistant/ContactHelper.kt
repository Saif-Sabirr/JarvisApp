package com.jarvis.assistant

import android.content.Context
import android.provider.ContactsContract

/** Resolves a spoken name ("john", "mom") to a phone number from the device's contacts. */
object ContactHelper {

    fun findNumber(context: Context, spokenName: String): String? {
        val name = spokenName.trim()
        if (name.isEmpty()) return null

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$name%"),
            null
        )

        cursor?.use {
            if (it.moveToFirst()) {
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                return it.getString(numberIndex)
            }
        }
        return null
    }
}

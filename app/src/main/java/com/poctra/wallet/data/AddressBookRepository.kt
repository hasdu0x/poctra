package com.poctra.wallet.data

import android.content.Context
import org.json.JSONArray

class AddressBookRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("poctra_address_book", Context.MODE_PRIVATE)

    fun contacts(): List<AddressBookContact> {
        val raw = prefs.getString(KeyContacts, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val contact = AddressBookContact.fromJson(array.getJSONObject(index))
                    if (contact.address.isNotBlank()) add(contact)
                }
            }
        }.getOrDefault(emptyList())
            .distinctBy { it.address.lowercase() }
            .sortedBy { it.label.lowercase() }
    }

    fun save(contact: AddressBookContact): List<AddressBookContact> {
        val clean = contact.copy(
            label = contact.label.trim().take(32),
            address = contact.address.trim(),
            note = contact.note?.trim()?.take(160)?.ifBlank { null },
            identityName = contact.identityName?.trim()?.lowercase()?.take(96)?.ifBlank { null },
        )
        val next = contacts().filterNot {
            it.id == clean.id || it.address.equals(clean.address, ignoreCase = true)
        } + clean
        persist(next)
        return contacts()
    }

    fun remove(id: String): List<AddressBookContact> {
        persist(contacts().filterNot { it.id == id })
        return contacts()
    }

    private fun persist(contacts: List<AddressBookContact>) {
        val array = JSONArray().apply { contacts.forEach { put(it.toJson()) } }
        prefs.edit().putString(KeyContacts, array.toString()).apply()
    }

    companion object {
        private const val KeyContacts = "contacts"
    }
}

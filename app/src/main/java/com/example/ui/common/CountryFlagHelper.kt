package com.example.ui.common

import java.util.Locale

object CountryFlagHelper {

    private val countryAliasToIso = mapOf(
        "philippines" to "PH",
        "ph" to "PH",
        "united states" to "US",
        "united states of america" to "US",
        "usa" to "US",
        "u.s.a." to "US",
        "us" to "US",
        "canada" to "CA",
        "united kingdom" to "GB",
        "uk" to "GB",
        "u.k." to "GB",
        "great britain" to "GB",
        "england" to "GB",
        "japan" to "JP",
        "south korea" to "KR",
        "korea" to "KR",
        "republic of korea" to "KR",
        "singapore" to "SG",
        "australia" to "AU",
        "new zealand" to "NZ",
        "united arab emirates" to "AE",
        "uae" to "AE",
        "dubai" to "AE",
        "saudi arabia" to "SA",
        "ksa" to "SA",
        "qatar" to "QA",
        "kuwait" to "KW",
        "hong kong" to "HK",
        "taiwan" to "TW",
        "malaysia" to "MY",
        "thailand" to "TH",
        "indonesia" to "ID",
        "vietnam" to "VN",
        "india" to "IN",
        "germany" to "DE",
        "france" to "FR",
        "spain" to "ES",
        "italy" to "IT",
        "netherlands" to "NL",
        "switzerland" to "CH",
        "norway" to "NO",
        "sweden" to "SE",
        "ireland" to "IE",
        "brazil" to "BR",
        "mexico" to "MX"
    )

    private val isoByEnglishName: Map<String, String> by lazy {
        Locale.getISOCountries().associateBy { code ->
            Locale("", code).getDisplayCountry(Locale.ENGLISH).trim().lowercase(Locale.ENGLISH)
        }
    }

    fun isoCodeForCountry(country: String?): String? {
        val normalized = country?.trim()?.lowercase(Locale.ENGLISH)?.takeIf { it.isNotBlank() } ?: return null
        if (normalized == "all" || normalized == "overseas" || normalized == "global") return null

        countryAliasToIso[normalized]?.let { return it }

        if (normalized.length == 2 && normalized.all { it in 'a'..'z' }) {
            val upper = normalized.uppercase(Locale.ENGLISH)
            if (Locale.getISOCountries().contains(upper)) return upper
        }

        isoByEnglishName[normalized]?.let { return it }

        return isoByEnglishName.entries.firstOrNull { (name, _) ->
            name.contains(normalized) || normalized.contains(name)
        }?.value
    }

    fun isoCodeToFlagEmoji(isoCode: String): String {
        val clean = isoCode.trim().uppercase(Locale.ENGLISH)
        if (clean.length != 2 || !clean.all { it in 'A'..'Z' }) return "🌍"
        val first = Character.codePointAt(clean, 0) - 'A'.code + 0x1F1E6
        val second = Character.codePointAt(clean, 1) - 'A'.code + 0x1F1E6
        return String(Character.toChars(first)) + String(Character.toChars(second))
    }

    fun flagForCountry(country: String?): String {
        val iso = isoCodeForCountry(country) ?: return "🌍"
        return isoCodeToFlagEmoji(iso)
    }

    fun formatCountryWithFlag(country: String?, city: String? = null): String {
        val safeCountry = country?.trim().orEmpty()
        val safeCity = city?.trim().orEmpty()
        val flag = flagForCountry(safeCountry)
        val locationText = listOf(safeCity, safeCountry).filter { it.isNotBlank() }.joinToString(", ")
        return if (locationText.isBlank()) {
            "$flag Global"
        } else {
            "$flag $locationText"
        }
    }
}

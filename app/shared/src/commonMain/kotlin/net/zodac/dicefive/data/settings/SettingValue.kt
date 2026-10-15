package net.zodac.dicefive.data.settings

/** One setting the cloud save carries, as the type it is stored as. A null in its place means unset. */
sealed interface SettingValue {

    data class Text(val value: String) : SettingValue

    data class Number(val value: Int) : SettingValue

    data class Flag(val value: Boolean) : SettingValue
}

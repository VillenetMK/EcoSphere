package com.example.ecosphere.shared

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EcoSphereUserProfile(
    val username: String,
    @SerialName("first_name") @SerializedName("first_name") val firstName: String,
    @SerialName("last_name") @SerializedName("last_name") val lastName: String,
    @SerialName("full_name") @SerializedName("full_name") val fullName: String,
    val email: String,
    @SerialName("registration_method") @SerializedName("registration_method")
    val registrationMethod: String,
    val status: String,
    val role: String
)

@Serializable
data class RegistrationDraft(
    val username: String,
    val firstName: String,
    val lastName: String,
    val dni: String,
    val phone: String,
    val email: String,
    val provider: String
)

data class RegistrationValidation(
    val draft: RegistrationDraft,
    val errors: Map<String, String>
) {
    val isValid: Boolean get() = errors.isEmpty()
}

object AuthValidation {
    private val usernamePattern = Regex("^[A-Za-z][A-Za-z0-9._-]{2,31}$")
    private val personNamePattern = Regex("^[\\p{L}][\\p{L} .'’-]*[\\p{L}]$")
    private val phonePattern = Regex("^\\+[1-9][0-9]{7,14}$")
    private val emailPattern = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    fun normalizePhone(value: String): String {
        val raw = value.trim()
        val digits = raw.filter(Char::isDigit)
        return when {
            Regex("^9[0-9]{8}$").matches(digits) -> "+51$digits"
            raw.startsWith("+") -> "+$digits"
            else -> digits
        }
    }

    fun validateRegistration(
        username: String,
        firstName: String,
        lastName: String,
        dni: String,
        phone: String,
        email: String,
        provider: String
    ): RegistrationValidation {
        val normalizedUsername = username.trim()
        val normalizedFirstName = firstName.trim().replace(Regex("\\s+"), " ")
        val normalizedLastName = lastName.trim().replace(Regex("\\s+"), " ")
        val normalizedDni = dni.filter(Char::isDigit).take(8)
        val normalizedPhone = normalizePhone(phone)
        val normalizedEmail = email.trim().lowercase()
        val errors = linkedMapOf<String, String>()

        if (!usernamePattern.matches(normalizedUsername)) {
            errors["username"] = "Usa entre 3 y 32 caracteres: letras, números, punto, guion o guion bajo."
        }
        if (normalizedFirstName.length !in 2..80 || !personNamePattern.matches(normalizedFirstName)) {
            errors["firstName"] = "Ingresa tus nombres usando sólo letras, espacios, apóstrofes o guiones."
        }
        if (normalizedLastName.length !in 2..80 || !personNamePattern.matches(normalizedLastName)) {
            errors["lastName"] = "Ingresa tus apellidos usando sólo letras, espacios, apóstrofes o guiones."
        }
        if (!Regex("^[0-9]{8}$").matches(normalizedDni)) {
            errors["dni"] = "El DNI debe tener exactamente 8 dígitos."
        }
        if (!phonePattern.matches(normalizedPhone)) {
            errors["phone"] = "Ingresa un teléfono válido; por ejemplo, +51 999 999 999."
        }
        if (normalizedEmail.length > 254 || !emailPattern.matches(normalizedEmail)) {
            errors["email"] = "Ingresa un correo electrónico válido."
        }

        return RegistrationValidation(
            draft = RegistrationDraft(
                username = normalizedUsername,
                firstName = normalizedFirstName,
                lastName = normalizedLastName,
                dni = normalizedDni,
                phone = normalizedPhone,
                email = normalizedEmail,
                provider = provider
            ),
            errors = errors
        )
    }

    fun validatePassword(password: String, confirmation: String): Map<String, String> {
        val errors = linkedMapOf<String, String>()
        if (password.length < 12) {
            errors["password"] = "La contraseña debe tener al menos 12 caracteres."
        }
        if (password != confirmation) {
            errors["passwordConfirmation"] = "Las contraseñas no coinciden."
        }
        return errors
    }
}

package com.example.ecosphere.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthValidationTest {
    @Test
    fun `normaliza el telefono peruano y conserva nombres separados`() {
        val result = AuthValidation.validateRegistration(
            username = " usuario123 ",
            firstName = " Ana María ",
            lastName = " De la Cruz ",
            dni = "1234-5678",
            phone = "999 999 999",
            email = "USUARIO@EJEMPLO.COM",
            provider = "google"
        )

        assertTrue(result.errors.toString(), result.isValid)
        assertEquals("Ana María", result.draft.firstName)
        assertEquals("De la Cruz", result.draft.lastName)
        assertEquals("12345678", result.draft.dni)
        assertEquals("+51999999999", result.draft.phone)
        assertEquals("usuario@ejemplo.com", result.draft.email)
    }

    @Test
    fun `formatea y limita el celular peruano mientras se escribe`() {
        assertEquals("+51 999 888 777", AuthValidation.formatPhoneInput("+51 99988877712345"))
        assertEquals("+34612345678", AuthValidation.normalizePhone("+34 612 345 678"))
    }

    @Test
    fun `rechaza celulares peruanos con longitud o inicio incorrectos`() {
        fun validation(phone: String) = AuthValidation.validateRegistration(
            username = "usuario123",
            firstName = "Ana María",
            lastName = "De la Cruz",
            dni = "12345678",
            phone = phone,
            email = "usuario@example.com",
            provider = "email"
        )

        assertTrue(validation("+51 999 888 777").isValid)
        assertFalse(validation("+51 899 888 777").isValid)
        assertFalse(validation("+51 999 888 77").isValid)
    }

    @Test
    fun `rechaza identidades incompletas y contrasenas cortas`() {
        val identity = AuthValidation.validateRegistration(
            username = "x",
            firstName = "1",
            lastName = "",
            dni = "12",
            phone = "999",
            email = "sin-correo",
            provider = "email"
        )
        val passwordErrors = AuthValidation.validatePassword("corta", "diferente")

        assertFalse(identity.isValid)
        assertTrue(identity.errors.keys.containsAll(listOf("username", "firstName", "lastName", "dni", "phone", "email")))
        assertTrue(passwordErrors.keys.containsAll(listOf("password", "passwordConfirmation")))
    }
}

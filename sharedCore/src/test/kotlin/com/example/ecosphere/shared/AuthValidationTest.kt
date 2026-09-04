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

    @Test
    fun `descarta borradores personales vencidos o fechados en el futuro`() {
        val now = 1_800_000_000_000L
        val draft = RegistrationDraft(
            username = "usuario123",
            firstName = "Ana",
            lastName = "Vargas",
            dni = "12345678",
            phone = "+51999999999",
            email = "ana@example.com",
            provider = "email",
            savedAtEpochMs = now
        )

        assertTrue(AuthValidation.isRegistrationDraftCurrent(draft, now))
        assertTrue(AuthValidation.isRegistrationDraftCurrent(
            draft.copy(savedAtEpochMs = now - AuthValidation.REGISTRATION_DRAFT_TTL_MS),
            now
        ))
        assertFalse(AuthValidation.isRegistrationDraftCurrent(
            draft.copy(savedAtEpochMs = now - AuthValidation.REGISTRATION_DRAFT_TTL_MS - 1),
            now
        ))
        assertFalse(AuthValidation.isRegistrationDraftCurrent(
            draft.copy(savedAtEpochMs = now + 1),
            now
        ))
    }

    @Test
    fun `normaliza y valida identificadores temporales del controlador`() {
        assertEquals("A1B2C3D4E5F6", ControllerPairing.hardwareUidOrNull("a1b2-c3d4-e5f6"))
        assertEquals(
            "00112233445566778899AABB",
            ControllerPairing.claimProofOrNull("0011-2233-4455-6677-8899-aabb")
        )
        assertEquals("ABCDEF123456", ControllerPairing.pairingCodeOrNull("abcd-ef12-3456"))
        assertEquals(null, ControllerPairing.hardwareUidOrNull("A1B2-C3D4-E5G6"))
        assertEquals(null, ControllerPairing.claimProofOrNull("001122"))
        assertEquals(null, ControllerPairing.pairingCodeOrNull("ABCDEF1234567"))
    }

    @Test
    fun `los errores de autenticacion no exponen detalles internos`() {
        assertEquals(
            "No se pudo completar la operación.",
            AuthValidation.safeAuthErrorMessage("permission denied for table private.user_profiles")
        )
        assertEquals(
            "Usuario o contraseña incorrectos.",
            AuthValidation.safeAuthErrorMessage("Invalid login credentials")
        )
        assertEquals(
            "No se pudo conectar con EcoSphere. Revisa tu conexión e inténtalo nuevamente.",
            AuthValidation.safeAuthErrorMessage("network timeout")
        )
    }

    @Test
    fun `los errores operativos no exponen respuestas internas`() {
        assertEquals(
            "No se pudo actualizar el control",
            ClientErrorMessages.safe(
                "permission denied for table public.device_control",
                "No se pudo actualizar el control"
            )
        )
        assertEquals(
            "Tu cuenta no tiene permiso para realizar esta acción.",
            ClientErrorMessages.safe("HTTP 403 Client Error", "Error")
        )
    }
}

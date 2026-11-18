package com.nayakacctv.ui.utils

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

object GoogleAuthHelper {

    fun isConfigured(webClientId: String): Boolean {
        return webClientId.isNotBlank() &&
                !webClientId.startsWith("YOUR_GOOGLE_WEB_CLIENT_ID") &&
                !webClientId.startsWith("xxxxxxxx-")
    }

    suspend fun getGoogleIdToken(
        context: Context,
        webClientId: String
    ): Result<String> {
        if (!isConfigured(webClientId)) {
            return Result.failure(
                Exception("Google Sign-In belum dikonfigurasi. Harap isi WEB_CLIENT_ID dari Google Cloud Console di strings.xml.")
            )
        }

        return try {
            val credentialManager = CredentialManager.create(context)

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                request = request,
                context = context
            )
            val credential = result.credential

            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                Result.success(googleIdTokenCredential.idToken)
            } else {
                Result.failure(Exception("Tipe kredensial Google tidak dikenali."))
            }
        } catch (e: GetCredentialCancellationException) {
            Result.failure(Exception("Login Google dibatalkan."))
        } catch (e: GetCredentialException) {
            Result.failure(Exception("Gagal masuk dengan Google: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(Exception("Terjadi kesalahan Google Sign-In: ${e.localizedMessage ?: "Tidak diketahui"}"))
        }
    }
}

# Add project-specific ProGuard rules here.

# Credential Manager / Google ID token (Sign in with Google)
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** {
  *;
}

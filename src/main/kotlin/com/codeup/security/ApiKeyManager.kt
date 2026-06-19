package com.codeup.security

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe

object ApiKeyManager {
    private val ATTRIBUTES = CredentialAttributes(
        generateServiceName("Codeup", "anthropic-api-key")
    )

    fun getApiKey(): String? = PasswordSafe.instance.getPassword(ATTRIBUTES)

    fun setApiKey(key: String) {
        PasswordSafe.instance.setPassword(ATTRIBUTES, key)
    }

    fun clearApiKey() {
        PasswordSafe.instance.setPassword(ATTRIBUTES, null)
    }
}
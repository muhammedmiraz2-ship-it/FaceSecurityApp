package com.example.facesecurityapp

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object FaceStorage {

    private const val KEY_ALIAS = "FaceSecurityEncryptionKey"
    private const val PREFS_NAME = "face_security_storage"
    private const val EMBEDDING_KEY = "registered_embedding"

    @Volatile
    private var registeredEmbedding: FloatArray? = null

    fun initialize(context: Context) {
        registeredEmbedding = load(context)
    }

    fun getRegisteredEmbedding(): FloatArray? {
        return registeredEmbedding?.copyOf()
    }

    fun save(context: Context, embedding: FloatArray) {
        require(embedding.isNotEmpty()) {
            "Face embedding cannot be empty"
        }

        val buffer = ByteBuffer
            .allocate(embedding.size * 4)
            .order(ByteOrder.LITTLE_ENDIAN)

        embedding.forEach { buffer.putFloat(it) }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())

        val encrypted = cipher.doFinal(buffer.array())
        val combined = cipher.iv + encrypted

        val encoded = Base64.encodeToString(
            combined,
            Base64.NO_WRAP
        )

        val saved = context.applicationContext
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            .edit()
            .putString(EMBEDDING_KEY, encoded)
            .commit()

        check(saved) {
            "Could not save registered face"
        }

        registeredEmbedding = embedding.copyOf()
    }

    fun load(context: Context): FloatArray? {
        return try {
            val encoded = context.applicationContext
                .getSharedPreferences(
                    PREFS_NAME,
                    Context.MODE_PRIVATE
                )
                .getString(EMBEDDING_KEY, null)
                ?: return null

            val combined = Base64.decode(
                encoded,
                Base64.NO_WRAP
            )

            if (combined.size <= 12) return null

            val iv = combined.copyOfRange(0, 12)
            val encrypted = combined.copyOfRange(
                12,
                combined.size
            )

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")

            cipher.init(
                Cipher.DECRYPT_MODE,
                getSecretKey(),
                GCMParameterSpec(128, iv)
            )

            val decrypted = cipher.doFinal(encrypted)

            if (decrypted.isEmpty() || decrypted.size % 4 != 0) {
                return null
            }

            val buffer = ByteBuffer.wrap(decrypted)
                .order(ByteOrder.LITTLE_ENDIAN)

            FloatArray(decrypted.size / 4) {
                buffer.float
            }.also {
                registeredEmbedding = it.copyOf()
            }
        } catch (e: Exception) {
            registeredEmbedding = null
            null
        }
    }

    fun clear(context: Context) {
        val cleared = context.applicationContext
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            .edit()
            .remove(EMBEDDING_KEY)
            .commit()

        check(cleared) {
            "Could not clear registered face"
        }

        registeredEmbedding = null
    }

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)

        val existingKey = keyStore.getKey(KEY_ALIAS, null)

        if (existingKey is SecretKey) {
            return existingKey
        }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )

        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or
                        KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(
                    KeyProperties.ENCRYPTION_PADDING_NONE
                )
                .setRandomizedEncryptionRequired(true)
                .build()
        )

        return generator.generateKey()
    }
}
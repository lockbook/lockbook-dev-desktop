package lockbook.dev

import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util
import java.util.Base64

import javax.crypto.spec.{IvParameterSpec, PBEKeySpec, SecretKeySpec}
import javax.crypto.{Cipher, SecretKeyFactory}

import scala.util.{Failure, Success, Try}

case class EncryptedValue(garbage: String)
case class DecryptedValue(secret: String)
case class WrongPassword() extends Error("Incorrect password")

trait EncryptionHelper {
  def encrypt(value: DecryptedValue, password: Password): Try[EncryptedValue]
  def decrypt(garbage: EncryptedValue, password: Password): Try[DecryptedValue]
}

class EncryptionImpl extends EncryptionHelper {
  def encrypt(value: DecryptedValue, password: Password): Try[EncryptedValue] =
    Try(
      EncryptedValue(
        encryptHelper(value.secret, password.password)
      )
    )

  def decrypt(garbage: EncryptedValue, password: Password): Try[DecryptedValue] =
    try {
      Success(
        DecryptedValue(
          decryptHelper(garbage.garbage, password.password)
        )
      )
    } catch {
      case _: Exception =>
        Failure(WrongPassword())
    }

  private def encryptHelper(str: String, password: String): String = {
    val random = new SecureRandom
    val salt   = new Array[Byte](16)
    random.nextBytes(salt)

    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
    val spec    = new PBEKeySpec(password.toCharArray, salt, 65536, 256)
    val tmp     = factory.generateSecret(spec)
    val secret  = new SecretKeySpec(tmp.getEncoded, "AES")
    val cipher  = Cipher.getInstance("AES/CBC/PKCS5Padding")
    cipher.init(Cipher.ENCRYPT_MODE, secret)

    val params        = cipher.getParameters
    val iv            = params.getParameterSpec(classOf[IvParameterSpec]).getIV
    val encryptedText = cipher.doFinal(str.getBytes("UTF-8"))

    // concatenate salt + iv + ciphertext
    val outputStream = new ByteArrayOutputStream
    outputStream.write(salt)
    outputStream.write(iv)
    outputStream.write(encryptedText)

    // properly encode the complete ciphertext
    new String(Base64.getEncoder.encode(outputStream.toByteArray))
  }

  private def decryptHelper(str: String, password: String): String = {
    val ciphertext = Base64.getDecoder.decode(str)
    if (ciphertext.length < 48) return null
    val salt    = util.Arrays.copyOfRange(ciphertext, 0, 16)
    val iv      = util.Arrays.copyOfRange(ciphertext, 16, 32)
    val ct      = util.Arrays.copyOfRange(ciphertext, 32, ciphertext.length)
    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
    val spec    = new PBEKeySpec(password.toCharArray, salt, 65536, 256)
    val tmp     = factory.generateSecret(spec)
    val secret  = new SecretKeySpec(tmp.getEncoded, "AES")
    val cipher  = Cipher.getInstance("AES/CBC/PKCS5Padding")
    cipher.init(Cipher.DECRYPT_MODE, secret, new IvParameterSpec(iv))
    val plaintext = cipher.doFinal(ct)
    new String(plaintext, "UTF-8")
  }
}

package com.aisec.sa.test;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class Foo{
	byte[] key = "MARTIN_123456789".getBytes();
	byte[] iv = "1234567890123456".getBytes();
	byte[] decryptedData = null;

	private byte[] decrypt(byte[] raw, byte[] iv, byte[] encrypted) throws Exception {
	  SecretKeySpec skeySpec = new SecretKeySpec(raw, "AES");
	  Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
	  IvParameterSpec ivspec = new IvParameterSpec(iv);
	  cipher.init(Cipher.DECRYPT_MODE, skeySpec, ivspec);
	  byte[] decrypted = cipher.doFinal(encrypted);

	  return decrypted;
	}
}
// Copyright 2017 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
////////////////////////////////////////////////////////////////////////////////

package com.google.crypto.tink.subtle;

import static com.google.crypto.tink.subtle.Poly1305.MAC_TAG_SIZE_IN_BYTES;
import static com.google.crypto.tink.subtle.SnuffleCipher.KEY_SIZE_IN_BYTES;
import static com.google.crypto.tink.subtle.SnuffleCipherTest.twosCompByte;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.truth.Truth;
import com.google.crypto.tink.TestUtil;
import com.google.crypto.tink.WycheproofTestUtil;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.util.Arrays;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

/** Unit tests for static methods in {@link SnuffleCipherPoly1305}. */
@RunWith(Suite.class)
@SuiteClasses({
  SnuffleCipherPoly1305Test.ChaCha20Poly1305IetfTest.class,
  SnuffleCipherPoly1305Test.XSalsa20Poly1305NaclTest.class,
  SnuffleCipherPoly1305Test.XChaCha20Poly1305IetfTest.class
})
public class SnuffleCipherPoly1305Test {
  private static final Charset UTF_8 = Charset.forName("UTF-8");

  /** Unit test base class for SnuffleCipherPoly1305. */
  abstract static class Poly1305TestBase {

    protected abstract SnuffleCipherPoly1305 createInstance(byte[] key);

    @Test
    public void testRandomChaCha20Poly1305() throws GeneralSecurityException {
      for (int i = 0; i < 1000; i++) {
        byte[] expectedInput = Random.randBytes(new java.util.Random().nextInt(300));
        byte[] aad = Random.randBytes(MAC_TAG_SIZE_IN_BYTES);
        byte[] key = Random.randBytes(KEY_SIZE_IN_BYTES);
        SnuffleCipherPoly1305 cipher = createInstance(key);
        byte[] output = cipher.encrypt(expectedInput, aad);
        byte[] nonce =
            Arrays.copyOfRange(
                output, MAC_TAG_SIZE_IN_BYTES, cipher.nonceSizeInBytes() + MAC_TAG_SIZE_IN_BYTES);
        byte[] actualInput = null;
        try {
          actualInput = cipher.decrypt(output, aad);
          assertTrue(Arrays.equals(expectedInput, actualInput));
        } catch (Throwable e) {
          String error =
              String.format(
                  "\n\nIteration: %d\nMessage: %s\nAad: %s\nKey: %s\nNonce: %s\nOutput: %s\n"
                      + "Decrypted Msg: %s\n",
                  i,
                  TestUtil.hexEncode(expectedInput),
                  TestUtil.hexEncode(aad),
                  TestUtil.hexEncode(key),
                  TestUtil.hexEncode(nonce),
                  TestUtil.hexEncode(output),
                  actualInput == null ? "null" : TestUtil.hexEncode(actualInput));
          fail(error + e.getMessage());
        }
      }
    }

    @Test
    public void testEncryptingEmptyString() throws GeneralSecurityException {
      byte[] aad = Random.randBytes(MAC_TAG_SIZE_IN_BYTES);
      byte[] key = Random.randBytes(KEY_SIZE_IN_BYTES);
      SnuffleCipherPoly1305 cipher = createInstance(key);
      byte[] ciphertext = cipher.encrypt(new byte[0], aad);
      Truth.assertThat(cipher.decrypt(ciphertext, aad)).isEqualTo(new byte[0]);
    }

    @Test
    public void testSnuffleCipherPoly1305ThrowsIllegalArgExpWhenKeyLenIsGreaterThan32() {
      try {
        createInstance(new byte[KEY_SIZE_IN_BYTES + 1]);
        fail("Expected IllegalArgumentException.");
      } catch (IllegalArgumentException e) {
        Truth.assertThat(e).hasMessageThat().containsMatch("The key length in bytes must be 32.");
      }
    }

    @Test
    public void testSnuffleCipherPoly1305ThrowsIllegalArgExpWhenKeyLenIsLessThan32() {
      try {
        createInstance(new byte[KEY_SIZE_IN_BYTES - 1]);
        fail("Expected IllegalArgumentException.");
      } catch (IllegalArgumentException e) {
        Truth.assertThat(e).hasMessageThat().containsMatch("The key length in bytes must be 32.");
      }
    }

    @Test
    public void testDecryptThrowsGeneralSecurityExpWhenCiphertextIsTooShort() {
      SnuffleCipherPoly1305 cipher = createInstance(new byte[KEY_SIZE_IN_BYTES]);
      try {
        cipher.decrypt(new byte[27], new byte[1]);
        fail("Expected GeneralSecurityException.");
      } catch (GeneralSecurityException e) {
        Truth.assertThat(e).hasMessageThat().containsMatch("ciphertext too short");
      }
    }

    @Test
    public void testEncryptWithOutputArgThrowsWhenOutputIsTooSmall()
        throws GeneralSecurityException {
      SnuffleCipherPoly1305 cipher = createInstance(new byte[KEY_SIZE_IN_BYTES]);
      ByteBuffer buf = ByteBuffer.allocate(10);
      try {
        cipher.encrypt(buf, new byte[0], new byte[5]);
        fail("Expected IllegalArgumentException.");
      } catch (IllegalArgumentException e) {
        Truth.assertThat(e).hasMessageThat().containsMatch("Given ByteBuffer output is too small");
      }
    }
  }

  /** Unit test base class for ChaCha20Poly1305. */
  public static class ChaCha20Poly1305IetfTest extends Poly1305TestBase {

    @Override
    protected SnuffleCipherPoly1305 createInstance(byte[] key) {
      return SnuffleCipherPoly1305.constructChaCha20Poly1305Ietf(key);
    }

    /**
     * Tests against the test vectors in Section 2.6.2 of RFC 7539.
     * https://tools.ietf.org/html/rfc7539#section-2.6.2
     */
    @Test
    public void testChaChaPoly1305KeyGen() {
      byte[] key =
          TestUtil.hexDecode(
              "" + "808182838485868788898a8b8c8d8e8f" + "909192939495969798999a9b9c9d9e9f");
      byte[] nonce = TestUtil.hexDecode("000000000001020304050607");
      SnuffleCipher cipher = SnuffleCipher.chaCha20(key);
      Truth.assertThat(cipher.getAuthenticatorKey(nonce))
          .isEqualTo(
              TestUtil.hexDecode(
                  "" + "8ad5a08b905f81cc815040274ab29471" + "a833b637e3fd0da508dbb8e2fdd1a646"));
    }

    /**
     * Tests against the test vector 1 in Appendix A.4 of RFC 7539.
     * https://tools.ietf.org/html/rfc7539#appendix-A.4
     */
    @Test
    public void testChaChaPoly1305KeyGen1() {
      byte[] key =
          TestUtil.hexDecode(
              "" + "00000000000000000000000000000000" + "00000000000000000000000000000000");
      byte[] nonce = TestUtil.hexDecode("000000000000000000000000");
      SnuffleCipher cipher = SnuffleCipher.chaCha20(key);
      Truth.assertThat(cipher.getAuthenticatorKey(nonce))
          .isEqualTo(
              TestUtil.hexDecode(
                  "" + "76b8e0ada0f13d90405d6ae55386bd28" + "bdd219b8a08ded1aa836efcc8b770dc7"));
    }

    /**
     * Tests against the test vector 2 in Appendix A.4 of RFC 7539.
     * https://tools.ietf.org/html/rfc7539#appendix-A.4
     */
    @Test
    public void testChaChaPoly1305KeyGen2() {
      byte[] key =
          TestUtil.hexDecode(
              "" + "00000000000000000000000000000000" + "00000000000000000000000000000001");
      byte[] nonce = TestUtil.hexDecode("000000000000000000000002");
      SnuffleCipher cipher = SnuffleCipher.chaCha20(key);
      Truth.assertThat(cipher.getAuthenticatorKey(nonce))
          .isEqualTo(
              TestUtil.hexDecode(
                  "" + "ecfa254f845f647473d3cb140da9e876" + "06cb33066c447b87bc2666dde3fbb739"));
    }

    /**
     * Tests against the test vector 3 in Appendix A.4 of RFC 7539.
     * https://tools.ietf.org/html/rfc7539#appendix-A.4
     */
    @Test
    public void testChaChaPoly1305KeyGen3() {
      byte[] key =
          TestUtil.hexDecode(
              "" + "1c9240a5eb55d38af333888604f6b5f0" + "473917c1402b80099dca5cbc207075c0");
      byte[] nonce = TestUtil.hexDecode("000000000000000000000002");
      SnuffleCipher cipher = SnuffleCipher.chaCha20(key);
      Truth.assertThat(cipher.getAuthenticatorKey(nonce))
          .isEqualTo(
              TestUtil.hexDecode(
                  "" + "965e3bc6f9ec7ed9560808f4d229f94b" + "137ff275ca9b3fcbdd59deaad23310ae"));
    }

    @Test
    public void testWycheproofVectors() throws Exception {
      JSONObject json =
          WycheproofTestUtil.readJson("testdata/wycheproof/chacha20_poly1305_test.json");
      WycheproofTestUtil.checkAlgAndVersion(json, "ChaCha20Poly1305", "0.1");
      int numTests = json.getInt("numberOfTests");
      int cntTests = 0;
      int errors = 0;
      JSONArray testGroups = json.getJSONArray("testGroups");
      for (int i = 0; i < testGroups.length(); i++) {
        JSONObject group = testGroups.getJSONObject(i);
        JSONArray tests = group.getJSONArray("tests");
        for (int j = 0; j < tests.length(); j++) {
          cntTests++;
          JSONObject testcase = tests.getJSONObject(j);
          int tcid = testcase.getInt("tcId");
          String tc = "tcId: " + tcid + " " + testcase.getString("comment");
          byte[] iv = Hex.decode(testcase.getString("iv"));
          byte[] key = Hex.decode(testcase.getString("key"));
          byte[] msg = Hex.decode(testcase.getString("msg"));
          byte[] aad = Hex.decode(testcase.getString("aad"));
          byte[] ct = Hex.decode(testcase.getString("ct"));
          byte[] tag = Hex.decode(testcase.getString("tag"));
          byte[] ciphertext = Bytes.concat(iv, ct, tag);
          String result = testcase.getString("result");
          try {
            SnuffleCipherPoly1305 aead = createInstance(key);
            byte[] decrypted = aead.decrypt(ciphertext, aad);
            boolean eq = TestUtil.arrayEquals(decrypted, msg);
            if (result.equals("invalid")) {
              System.out.println("Decrypted invalid ciphertext " + tc + " eq:" + eq);
              errors++;
            } else {
              if (!eq) {
                System.out.println(
                    "Incorrect decryption " + tc + " decrypted:" + TestUtil.hexEncode(decrypted));
              }
            }
          } catch (GeneralSecurityException ex) {
            if (result.equals("valid")) {
              System.out.println("Failed to decrypt " + tc);
              errors++;
            }
          }
        }
      }
      assertEquals(0, errors);
      assertEquals(numTests, cntTests);
    }
  }

  /** Unit test base class for XSalsa20Poly1305. */
  public static class XSalsa20Poly1305NaclTest extends Poly1305TestBase {

    @Override
    protected SnuffleCipherPoly1305 createInstance(byte[] key) {
      return SnuffleCipherPoly1305.constructXSalsa20Poly1305Nacl(key);
    }

    private static byte[] sharedKey(byte[] privateKey, byte[] publicKey)
        throws InvalidKeyException {
      return X25519.computeSharedSecret(privateKey, publicKey);
    }

    /** Section 10, Example 1 in decrypt mode http://cr.yp.to/highspeed/naclcrypto-20090310.pdf */
    @Test
    public void testXSalsa20Poly1305Decrypt() throws GeneralSecurityException {
      byte[] sharedKey =
          sharedKey(
              twosCompByte(
                  new int[] {
                    0x77, 0x07, 0x6d, 0x0a, 0x73, 0x18, 0xa5, 0x7d,
                    0x3c, 0x16, 0xc1, 0x72, 0x51, 0xb2, 0x66, 0x45,
                    0xdf, 0x4c, 0x2f, 0x87, 0xeb, 0xc0, 0x99, 0x2a,
                    0xb1, 0x77, 0xfb, 0xa5, 0x1d, 0xb9, 0x2c, 0x2a
                  }),
              twosCompByte(
                  new int[] {
                    0xde, 0x9e, 0xdb, 0x7d, 0x7b, 0x7d, 0xc1, 0xb4,
                    0xd3, 0x5b, 0x61, 0xc2, 0xec, 0xe4, 0x35, 0x37,
                    0x3f, 0x83, 0x43, 0xc8, 0x5b, 0x78, 0x67, 0x4d,
                    0xad, 0xfc, 0x7e, 0x14, 0x6f, 0x88, 0x2b, 0x4f
                  }));
      SnuffleCipherPoly1305 cipher = createInstance(XSalsa20.hSalsa20(sharedKey));
      byte[] plaintext =
          cipher.decrypt(
              twosCompByte(
                  new int[] {
                    0x69, 0x69, 0x6e, 0xe9, 0x55, 0xb6, 0x2b, 0x73, 0xcd, 0x62, 0xbd, 0xa8, 0x75,
                    0xfc, 0x73, 0xd6, 0x82, 0x19, 0xe0, 0x03, 0x6b, 0x7a, 0x0b, 0x37, 0x8e, 0x99,
                    0x3b, 0x9f, 0x48, 0x68, 0x12, 0x73, 0xc2, 0x96, 0x50, 0xba, 0x32, 0xfc, 0x76,
                    0xce, 0x48, 0x33, 0x2e, 0xa7, 0x16, 0x4d, 0x96, 0xa4, 0x47, 0x6f, 0xb8, 0xc5,
                    0x31, 0xa1, 0x18, 0x6a, 0xc0, 0xdf, 0xc1, 0x7c, 0x98, 0xdc, 0xe8, 0x7b, 0x4d,
                    0xa7, 0xf0, 0x11, 0xec, 0x48, 0xc9, 0x72, 0x71, 0xd2, 0xc2, 0x0f, 0x9b, 0x92,
                    0x8f, 0xe2, 0x27, 0x0d, 0x6f, 0xb8, 0x63, 0xd5, 0x17, 0x38, 0xb4, 0x8e, 0xee,
                    0xe3, 0x14, 0xa7, 0xcc, 0x8a, 0xb9, 0x32, 0x16, 0x45, 0x48, 0xe5, 0x26, 0xae,
                    0x90, 0x22, 0x43, 0x68, 0x51, 0x7a, 0xcf, 0xea, 0xbd, 0x6b, 0xb3, 0x73, 0x2b,
                    0xc0, 0xe9, 0xda, 0x99, 0x83, 0x2b, 0x61, 0xca, 0x01, 0xb6, 0xde, 0x56, 0x24,
                    0x4a, 0x9e, 0x88, 0xd5, 0xf9, 0xb3, 0x79, 0x73, 0xf6, 0x22, 0xa4, 0x3d, 0x14,
                    0xa6, 0x59, 0x9b, 0x1f, 0x65, 0x4c, 0xb4, 0x5a, 0x74, 0xe3, 0x55, 0xa5, 0xf3,
                    0xff, 0xc7, 0x70, 0x3f, 0x94, 0x00, 0xe5, 0x2a, 0x7d, 0xfb, 0x4b, 0x3d, 0x33,
                    0x05, 0xd9
                  }),
              null);
      Truth.assertThat(plaintext)
          .isEqualTo(
              twosCompByte(
                  new int[] {
                    0xbe, 0x07, 0x5f, 0xc5, 0x3c, 0x81, 0xf2, 0xd5,
                    0xcf, 0x14, 0x13, 0x16, 0xeb, 0xeb, 0x0c, 0x7b,
                    0x52, 0x28, 0xc5, 0x2a, 0x4c, 0x62, 0xcb, 0xd4,
                    0x4b, 0x66, 0x84, 0x9b, 0x64, 0x24, 0x4f, 0xfc,
                    0xe5, 0xec, 0xba, 0xaf, 0x33, 0xbd, 0x75, 0x1a,
                    0x1a, 0xc7, 0x28, 0xd4, 0x5e, 0x6c, 0x61, 0x29,
                    0x6c, 0xdc, 0x3c, 0x01, 0x23, 0x35, 0x61, 0xf4,
                    0x1d, 0xb6, 0x6c, 0xce, 0x31, 0x4a, 0xdb, 0x31,
                    0x0e, 0x3b, 0xe8, 0x25, 0x0c, 0x46, 0xf0, 0x6d,
                    0xce, 0xea, 0x3a, 0x7f, 0xa1, 0x34, 0x80, 0x57,
                    0xe2, 0xf6, 0x55, 0x6a, 0xd6, 0xb1, 0x31, 0x8a,
                    0x02, 0x4a, 0x83, 0x8f, 0x21, 0xaf, 0x1f, 0xde,
                    0x04, 0x89, 0x77, 0xeb, 0x48, 0xf5, 0x9f, 0xfd,
                    0x49, 0x24, 0xca, 0x1c, 0x60, 0x90, 0x2e, 0x52,
                    0xf0, 0xa0, 0x89, 0xbc, 0x76, 0x89, 0x70, 0x40,
                    0xe0, 0x82, 0xf9, 0x37, 0x76, 0x38, 0x48, 0x64,
                    0x5e, 0x07, 0x05
                  }));
    }
  }

  /** Unit test base class for XChaCha20Poly1305. */
  public static class XChaCha20Poly1305IetfTest extends Poly1305TestBase {

    // From libsodium's test/default/aead_xchacha20poly1305.c
    // see test/default/aead_xchacha20poly1305.exp for ciphertext values.
    private static final byte[] KEY =
        TestUtil.hexDecode("808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9f");
    private static final String NONCE = "07000000404142434445464748494a4b0000000000000000";
    private static final byte[] AD = TestUtil.hexDecode("50515253c0c1c2c3c4c5c6c7");

    @Override
    protected SnuffleCipherPoly1305 createInstance(byte[] key) {
      return SnuffleCipherPoly1305.constructXChaCha20Poly1305Ietf(key);
    }

    @Test
    public void testXChaCha20Poly1305() throws GeneralSecurityException {
      String in =
          TestUtil.hexEncode(
              ("Ladies and Gentlemen of the class of '99: If I could offer you only one "
                      + "tip for the future, sunscreen would be it.")
                  .getBytes(UTF_8));
      SnuffleCipherPoly1305 cipher = SnuffleCipherPoly1305.constructXChaCha20Poly1305Ietf(KEY);
      byte[] c =
          TestUtil.hexDecode(
              NONCE
                  + "453c0693a7407f04ff4c56aedb17a3c0a1afff01174930fc22287c33dbcf0ac8"
                  + "b89ad929530a1bb3ab5e69f24c7f6070c8f840c9abb4f69fbfc8a7ff5126faee"
                  + "bbb55805ee9c1cf2ce5a57263287aec5780f04ec324c3514122cfc3231fc1a8b"
                  + "718a62863730a2702bb76366116bed09e0fd"
                  + "5c6d84b6b0c1abaf249d5dd0f7f5a7ea"); // tag
      Truth.assertThat(TestUtil.hexEncode(cipher.decrypt(c, AD))).isEqualTo(in);
    }

    @Test
    public void testXChaCha20Poly1305EmptyAd() throws GeneralSecurityException {
      String in =
          TestUtil.hexEncode(
              ("Ladies and Gentlemen of the class of '99: If I could offer you only one "
                      + "tip for the future, sunscreen would be it.")
                  .getBytes(UTF_8));
      SnuffleCipherPoly1305 cipher = SnuffleCipherPoly1305.constructXChaCha20Poly1305Ietf(KEY);
      byte[] c =
          TestUtil.hexDecode(
              NONCE
                  + "453c0693a7407f04ff4c56aedb17a3c0a1afff01174930fc22287c33dbcf0ac8"
                  + "b89ad929530a1bb3ab5e69f24c7f6070c8f840c9abb4f69fbfc8a7ff5126faee"
                  + "bbb55805ee9c1cf2ce5a57263287aec5780f04ec324c3514122cfc3231fc1a8b"
                  + "718a62863730a2702bb76366116bed09e0fd"
                  + "d4c860b7074be894fac9697399be5cc1"); // tag
      Truth.assertThat(TestUtil.hexEncode(cipher.decrypt(c, new byte[0]))).isEqualTo(in);
    }
  }
}

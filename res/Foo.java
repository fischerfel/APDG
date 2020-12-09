//import java.security.Key;
//import java.security.NoSuchAlgorithmException;
//import java.security.NoSuchProviderException;
//import java.security.spec.InvalidKeySpecException;
//import java.security.spec.KeySpec;
//import java.util.Arrays;
//
//import javax.crypto.SecretKeyFactory;
//import javax.crypto.spec.PBEKeySpec;


public class Foo {

//	/**
//	 * @param args
//	 * @throws NoSuchAlgorithmException 
//	 * @throws InvalidKeySpecException 
//	 * @throws NoSuchProviderException 
//	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

		//Mode 1
//		byte[] salt = new byte[32];

	    SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
	    KeySpec keyspec = new PBEKeySpec("password".toCharArray(), salt, 1000, 128);
	    Key key = factory.generateSecret(keyspec);
	    System.out.println(key.getClass().getName());
	    System.out.println(Arrays.toString(key.getEncoded()));
	    
//	    int i = 0;
//	    int j = i + 1;
//	    System.out.println(j + i);
//	    
//	    throw new Exception();
//	    throw new IllegalArgumentException();
//	    
//	    try {
//	    	System.out.println(i * j);
//	    } catch (Exception e) {
//	    	e.printStackTrace();
//	    }
	    

	    //Mode 2

//	    PBEParametersGenerator generator = new PKCS5S2ParametersGenerator();
//	    generator.init(PBEParametersGenerator.PKCS5PasswordToUTF8Bytes(("password").toCharArray()), salt, 1000);
//	    KeyParameter params = (KeyParameter)generator.generateDerivedParameters(128);
//	    System.out.println(Arrays.toString(params.getKey()));

	    //Mode 3

	    SecretKeyFactory factorybc = SecretKeyFactory.getInstance("PBEWITHHMACSHA1", "BC");
	    KeySpec keyspecbc = new PBEKeySpec("password".toCharArray(), salt, 1000, 128);
	    Key keybc = factorybc.generateSecret(keyspecbc);
	    System.out.println(keybc.getClass().getName());
	    System.out.println(Arrays.toString(keybc.getEncoded()));
	    System.out.println(keybc.getAlgorithm());
	}

}

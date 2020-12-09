package com.aisec.sa.io;

import java.util.Map;

import com.aisec.sa.apdg.APDG;
import com.aisec.sa.util.Logger;
import com.ibm.wala.ipa.slicer.Statement;

public class Label {
	public enum Type {
		NONE, CIPHER, TLS, HASH, IV, KEY, PBE, HNV, HNV_OR, TM, CIPHER_FIELD, TLS_FIELD, HASH_FIELD
	}
	
	public static boolean isLabeledSignature(String methodSignature, Statement.Kind kind, Label.Type n) {
		if (methodSignature == null)
			return false;
		if (methodSignature.contains("Cipher.getInstance(")
				&& kind.equals(Statement.Kind.NORMAL_RET_CALLER)
				&& (n.equals(Type.CIPHER) || n.equals(Type.CIPHER_FIELD))) {
			return true;
		}
		if (methodSignature.contains("SSLContext.getInstance(")
				&& kind.equals(Statement.Kind.NORMAL_RET_CALLER)
				&& (n.equals(Type.TLS) || n.equals(Type.TLS_FIELD))) {
			return true;
		}
		if (methodSignature.contains("MessageDigest.getInstance(")
				&& kind.equals(Statement.Kind.NORMAL_RET_CALLER)
				&& (n.equals(Type.HASH) || n.equals(Type.HASH_FIELD))) {
			return true;
		}
		if (methodSignature.contains("IvParameterSpec.<init>(") 
				&& kind.equals(Statement.Kind.NORMAL) 
				&& n.equals(Type.IV)) {
			return true;
		}
		if (methodSignature.contains("SecretKeySpec.<init>(") 
				&& kind.equals(Statement.Kind.NORMAL)
				&& n.equals(Type.KEY)) {
			return true;
		}
		if (methodSignature.contains("PBEKeySpec.<init>(") 
				&& kind.equals(Statement.Kind.NORMAL)
				&& n.equals(Type.PBE)) {
			return true;
		}
		if (methodSignature.contains("SSLSocketFactory.setHostnameVerifier(") 
				&& kind.equals(Statement.Kind.NORMAL)
				&& n.equals(Type.HNV)) {
			return true;
		}
		if (methodSignature.contains("HttpsURLConnection.setHostnameVerifier(") 
				&& kind.equals(Statement.Kind.NORMAL)
				&& n.equals(Type.HNV)) {
			return true;
		}
		if (methodSignature.contains("HttpsURLConnection.setDefaultHostnameVerifier(") 
				&& kind.equals(Statement.Kind.NORMAL)
				&& n.equals(Type.HNV)) {
			return true;
		}
//		if (methodSignature.contains("setHostnameVerifier(") 
//				&& kind.equals(Statement.Kind.NORMAL)
//				&& n.equals(Name.HNV)) {
//			return true;
//		}
		if (methodSignature.contains("HostnameVerifier.verify(")
				&& n.equals(Type.HNV_OR)) {
			return true;
		}
		if (methodSignature.contains("checkServerTrusted")
				&& n.equals(Type.TM)) {
			return true;
		}

		return false;
	}
	
	public static boolean isLabeledSignature(String methodSignature, Statement.Kind kind, Label.Type n, Map<String, Map<Integer, Integer> > annotations) {
		if (methodSignature == null)
			return false;
		
		if (annotations == null)
			return Label.isLabeledSignature(methodSignature, kind, n);
		
		
		
		return false;
	}

	public static boolean isLabeledType(String type) {
		if (type == null)
			return false;
		if (type.contains("SecretKeySpec"))
			return true;
		if (type.contains("IvParameterSpec"))
			return true;
		if (type.contains("PBEKeySpec"))
			return true;

		return false;
	}

	public static boolean isLabeledForBackwardSlice(String methodSignature, Statement.Kind kind, Label.Type n) {
		if (methodSignature == null)
			return false;
		
		if (methodSignature.contains("Cipher.getInstance(")
				&& kind.equals(Statement.Kind.NORMAL_RET_CALLER)
				&& n.equals(Type.CIPHER_FIELD)) {
			return true;
		}
		if (methodSignature.contains("SSLContext.getInstance(")
				&& kind.equals(Statement.Kind.NORMAL_RET_CALLER)
				&& n.equals(Type.TLS_FIELD)) {
			return true;
		}
		if (methodSignature.contains("MessageDigest.getInstance(")
				&& kind.equals(Statement.Kind.NORMAL_RET_CALLER)
				&& n.equals(Type.HASH_FIELD)) {
			return true;
		}

		if (methodSignature.contains("IvParameterSpec.<init>(") 
				&& kind.equals(Statement.Kind.NORMAL) 
				&& n.equals(Type.IV)) {
			return true;
		}
		if (methodSignature.contains("SecretKeySpec.<init>(") 
				&& kind.equals(Statement.Kind.NORMAL)
				&& n.equals(Type.KEY)) {
			return true;
		}
		if (methodSignature.contains("PBEKeySpec.<init>(") 
				&& kind.equals(Statement.Kind.NORMAL)
				&& (n.equals(Type.PBE))) {
			return true;
		}

		return false;
	}
	
	public static boolean isLabeledForOffsprings(String methodSignature, Statement.Kind kind, Label.Type n) {
		if (methodSignature == null)
			return true;
		
		if (methodSignature.contains("IvParameterSpec.<init>(") 
				&& kind.equals(Statement.Kind.NORMAL) 
				&& n.equals(Type.IV)) {
			return false;
		}
		if (methodSignature.contains("SecretKeySpec.<init>(") 
				&& kind.equals(Statement.Kind.NORMAL)
				&& n.equals(Type.KEY)) {
			return false;
		}
		if (methodSignature.contains("PBEKeySpec.<init>(") 
				&& kind.equals(Statement.Kind.NORMAL)
				&& (n.equals(Type.PBE))) {
			return false;
		}
		
		return true;
	}
}

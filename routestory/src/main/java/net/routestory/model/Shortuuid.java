package net.routestory.model;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.UUID;

public class Shortuuid {
	private static final String alphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"; 
	
	public static String uuid() {
		UUID uuid = UUID.randomUUID();
		byte[] bytes = ByteBuffer.allocate(17).put((byte)0).putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits()).array();
		BigInteger value = new BigInteger(bytes), len = BigInteger.valueOf(alphabet.length());
		String shortuuid = "";
		while (value.compareTo(BigInteger.ZERO) > 0) {
	        BigInteger[] div = value.divideAndRemainder(len);
	        shortuuid += alphabet.charAt((int)(div[1].longValue()));
	        value = div[0];
		}
		return shortuuid;
	}
}

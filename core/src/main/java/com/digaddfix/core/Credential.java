package com.digaddfix.core;

import java.security.*;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Versioned, bounded credential records. No plaintext credential is persisted. */
public final class Credential {
    private Credential() {}
    public static final int ITERATIONS=600000;
    public static boolean validPin(String pin) {
        if(pin==null || !pin.matches("[0-9]{6,12}")) return false;
        boolean same=true;for(int i=1;i<pin.length();i++) same&=pin.charAt(i)==pin.charAt(0);
        return !same && !"01234567890123456789".contains(pin) && !"98765432109876543210".contains(pin);
    }
    public static String recoveryCode() {
        byte[] bytes=new byte[16];new SecureRandom().nextBytes(bytes);
        String raw=hex(bytes).toUpperCase(Locale.ROOT);StringBuilder code=new StringBuilder();
        for(int i=0;i<raw.length();i+=4) {if(i>0) code.append('-');code.append(raw,i,i+4);}return code.toString();
    }
    public static String normalizeRecovery(String input) {
        if(input==null || input.length()>64) return "";
        String clean=input.replace("-","").replace(" ","").toUpperCase(Locale.ROOT);
        return clean.matches("[0-9A-F]{32}")?clean:"";
    }
    public static String create(char[] secret) throws GeneralSecurityException {
        if(secret==null || secret.length<6 || secret.length>64) throw new IllegalArgumentException("Credential length");
        byte[] salt=new byte[16];new SecureRandom().nextBytes(salt);
        return "p1$"+ITERATIONS+"$"+hex(salt)+"$"+hex(derive(secret,salt,ITERATIONS));
    }
    public static boolean verify(char[] secret,String record) throws GeneralSecurityException {
        if(secret==null || secret.length<6 || secret.length>64 || record==null || record.length()!=107) return false;
        String[] parts=record.split("\\$",-1);
        if(parts.length!=4 || !parts[0].equals("p1") || !parts[1].equals(Integer.toString(ITERATIONS)) ||
            !parts[2].matches("[0-9a-f]{32}") || !parts[3].matches("[0-9a-f]{64}")) return false;
        return MessageDigest.isEqual(unhex(parts[3]),derive(secret,unhex(parts[2]),ITERATIONS));
    }
    private static byte[] derive(char[] secret,byte[] salt,int iterations) throws GeneralSecurityException {
        PBEKeySpec spec=new PBEKeySpec(secret,salt,iterations,256);
        try {return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}
        finally {spec.clearPassword();}
    }
    private static String hex(byte[] bytes) {
        StringBuilder out=new StringBuilder();for(byte b:bytes) out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();
    }
    private static byte[] unhex(String value) {
        byte[] result=new byte[value.length()/2];
        for(int i=0;i<result.length;i++) result[i]=(byte)Integer.parseInt(value.substring(i*2,i*2+2),16);return result;
    }
}

package org.matrix.androidsdk.ssl;

import android.util.Base64;

import org.json.JSONException;
import org.json.JSONObject;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;

/**
 * Represents a X509 Certificate fingerprint.
 */
public class Fingerprint {
    public enum HashType { SHA1, SHA256 }

    private byte[] mBytes;
    private HashType mHashType;
    private String mDisplayableHexRepr;

    public Fingerprint(byte[] bytes, HashType hashType) {
        this.mBytes = bytes;
        this.mHashType = hashType;
        this.mDisplayableHexRepr = null;
    }

    public static Fingerprint newSha256Fingerprint(X509Certificate cert) throws CertificateException {
        return new Fingerprint(
                CertUtil.generateSha256Fingerprint(cert),
                HashType.SHA256
        );
    }

    public static Fingerprint newSha1Fingerprint(X509Certificate cert) throws CertificateException {
        return new Fingerprint(
                CertUtil.generateSha1Fingerprint(cert),
                HashType.SHA1
        );
    }

    public HashType getType() {
        return mHashType;
    }

    public byte[] getBytes() {
        return mBytes;
    }

    public String getBytesAsHexString() {
        if (mDisplayableHexRepr != null) {
            mDisplayableHexRepr = CertUtil.fingerprintToHexString(mBytes);
        }

        return mDisplayableHexRepr;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("bytes", Base64.encodeToString(getBytes(), Base64.DEFAULT));
        obj.put("hash_ type", mHashType.toString());
        return obj;
    }

    public static Fingerprint fromJson(JSONObject obj) throws JSONException {
        String hashTypeStr = obj.getString("hash_type");
        byte[] fingerprintBytes = Base64.decode(obj.getString("bytes"), Base64.DEFAULT);

        HashType hashType;
        try {
            hashType = HashType.valueOf(hashTypeStr);
        } catch (Exception e) {
            throw new JSONException("Unrecognized hash type: " + hashTypeStr);
        }

        return new Fingerprint(fingerprintBytes, hashType);
    }

    public boolean matchesCert(X509Certificate cert) throws CertificateException {
        Fingerprint o = null;
        switch (mHashType) {
            case SHA256:
                o = Fingerprint.newSha256Fingerprint(cert);
                break;
            case SHA1:
                o = Fingerprint.newSha1Fingerprint(cert);
                break;
        }

        return equals(o);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Fingerprint that = (Fingerprint) o;

        if (!Arrays.equals(mBytes, that.mBytes)) return false;
        return mHashType == that.mHashType;

    }

    @Override
    public int hashCode() {
        int result = mBytes != null ? Arrays.hashCode(mBytes) : 0;
        result = 31 * result + (mHashType != null ? mHashType.hashCode() : 0);
        return result;
    }
}

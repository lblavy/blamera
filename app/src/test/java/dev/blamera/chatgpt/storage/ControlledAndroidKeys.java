package dev.blamera.chatgpt.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.Key;
import java.security.KeyStoreSpi;
import java.security.Provider;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

/** A scoped JCA fixture, never an emulation or proof of Android hardware security. */
public final class ControlledAndroidKeys extends Provider {
    private final List<String> requestedAliases = new ArrayList<>();
    private boolean failLoads;

    public ControlledAndroidKeys(Map<String, Key> keys) {
        super("BlameraSyntheticAndroidKeys", 1.0, "Synthetic test keys only");
        KeyStoreSpi spi = new KeyStoreSpi() {
            @Override public Key engineGetKey(String alias, char[] password) {
                if (password != null) throw new AssertionError("Unexpected key password");
                requestedAliases.add(alias);
                if (!keys.containsKey(alias)) throw new AssertionError("Unseeded synthetic alias");
                return keys.get(alias);
            }
            @Override public void engineLoad(InputStream stream, char[] password) throws IOException {
                if (stream != null || password != null) throw new AssertionError("Unexpected input");
                if (failLoads) throw new IOException("SECRET synthetic backend location");
            }
            @Override public Certificate[] engineGetCertificateChain(String alias) { return null; }
            @Override public Certificate engineGetCertificate(String alias) { return null; }
            @Override public Date engineGetCreationDate(String alias) { return null; }
            @Override public Enumeration<String> engineAliases() {
                return Collections.enumeration(keys.keySet());
            }
            @Override public boolean engineContainsAlias(String alias) { return keys.containsKey(alias); }
            @Override public int engineSize() { return keys.size(); }
            @Override public boolean engineIsKeyEntry(String alias) { return keys.containsKey(alias); }
            @Override public boolean engineIsCertificateEntry(String alias) { return false; }
            @Override public String engineGetCertificateAlias(Certificate certificate) { return null; }
            @Override public void engineSetKeyEntry(String alias, Key key, char[] password, Certificate[] chain) {
                throw new AssertionError("Fixture keys cannot be modified");
            }
            @Override public void engineSetKeyEntry(String alias, byte[] key, Certificate[] chain) {
                throw new AssertionError("Fixture keys cannot be modified");
            }
            @Override public void engineSetCertificateEntry(String alias, Certificate certificate) {
                throw new AssertionError("Fixture keys cannot be modified");
            }
            @Override public void engineDeleteEntry(String alias) {
                throw new AssertionError("Fixture keys cannot be modified");
            }
            @Override public void engineStore(OutputStream stream, char[] password) {
                throw new AssertionError("Fixture backend cannot be exported");
            }
        };
        putService(new Service(this, "KeyStore", "AndroidKeyStore", "synthetic", null, null) {
            @Override public Object newInstance(Object parameter) { return spi; }
        });
    }

    public List<String> getRequestedAliases() { return new ArrayList<>(requestedAliases); }
    public void failLoads() { failLoads = true; }
}

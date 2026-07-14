package io.aegis.tenant.service;

import java.util.Hashtable;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import org.springframework.stereotype.Component;

/**
 * Looks up DNS TXT records via the JDK {@code dns:} JNDI provider (no extra dependency). Used to prove
 * domain ownership: the tenant publishes {@code _aegis-challenge.<domain>} TXT = the verification token.
 */
@Component
public class DnsVerifier {

    /** The challenge is published at this subdomain of the claimed host. */
    public static String challengeHost(String domain) {
        return "_aegis-challenge." + domain;
    }

    /**
     * True when a TXT record at {@code _aegis-challenge.<domain>} carries {@code expectedToken}. Any
     * lookup failure (NXDOMAIN, no TXT, resolver error) is treated as "not found" → false; the caller
     * turns that into a clear 400.
     */
    public boolean txtRecordPresent(String domain, String expectedToken) {
        String host = challengeHost(domain);
        Hashtable<String, String> env = new Hashtable<>();
        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        env.put("java.naming.provider.url", "dns:");
        InitialDirContext ctx = null;
        try {
            ctx = new InitialDirContext(env);
            Attributes attrs = ctx.getAttributes(host, new String[] {"TXT"});
            Attribute txt = attrs.get("TXT");
            if (txt == null) {
                return false;
            }
            for (int i = 0; i < txt.size(); i++) {
                Object value = txt.get(i);
                if (value != null && unquote(value.toString()).equals(expectedToken)) {
                    return true;
                }
            }
            return false;
        } catch (NamingException e) {
            return false;
        } finally {
            if (ctx != null) {
                try {
                    ctx.close();
                } catch (NamingException ignored) {
                    // best effort
                }
            }
        }
    }

    /** DNS TXT strings often come back wrapped in quotes; strip a single surrounding pair. */
    private static String unquote(String s) {
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
}

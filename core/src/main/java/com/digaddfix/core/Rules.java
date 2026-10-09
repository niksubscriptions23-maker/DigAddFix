package com.digaddfix.core;

import java.io.*;
import java.net.IDN;
import java.net.URI;
import java.util.*;

/** Immutable bundled policy; no network classification or browsing history. */
public final class Rules {
    public static final String BRAVE_PACKAGE = "com.brave.browser";
    public static final class Rule {
        public final String key, label, reason;
        Rule(String key, String label, String reason) { this.key=key; this.label=label; this.reason=reason; }
    }
    private final Map<String,Rule> apps, domains;
    public Rules(Reader appSource, Reader domainSource) throws IOException {
        apps=load(appSource, false); domains=load(domainSource, true);
        if (apps.containsKey(BRAVE_PACKAGE)) throw new IOException("Brave must not be an app block");
    }
    private static Map<String,Rule> load(Reader source, boolean host) throws IOException {
        Map<String,Rule> out=new LinkedHashMap<>();
        try (BufferedReader reader=new BufferedReader(source)) {
            String line; int number=0;
            while ((line=reader.readLine())!=null) {
                number++; line=line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts=line.split("\\|", -1);
                if (parts.length!=3 || parts[1].trim().isEmpty() || parts[2].trim().isEmpty())
                    throw new IOException("Invalid policy row "+number);
                String key=host?normalizeHost(parts[0]):parts[0].trim();
                if (key.isEmpty() || (!host && !key.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")))
                    throw new IOException("Invalid policy key at row "+number);
                if (out.put(key,new Rule(key,parts[1].trim(),parts[2].trim()))!=null)
                    throw new IOException("Duplicate policy key "+key);
            }
        }
        return Collections.unmodifiableMap(out);
    }
    public Rule app(String packageName) { return apps.get(packageName); }
    public Rule domain(String hostname) {
        String host=normalizeHost(hostname);
        while (!host.isEmpty()) {
            Rule rule=domains.get(host); if(rule!=null) return rule;
            int dot=host.indexOf('.'); if(dot<0) break; host=host.substring(dot+1);
        }
        return null;
    }
    public Collection<Rule> apps() { return apps.values(); }
    public Collection<Rule> domains() { return domains.values(); }
    public static String normalizeHost(String value) {
        if(value==null) return "";
        value=value.trim().toLowerCase(Locale.ROOT);
        while(value.endsWith(".")) value=value.substring(0,value.length()-1);
        if(value.isEmpty() || value.contains("/") || value.contains(":") || value.contains("@")) return "";
        try {
            String host=IDN.toASCII(value,IDN.USE_STD3_ASCII_RULES);
            if(host.length()>253) return "";
            for(String label:host.split("\\.",-1)) if(label.isEmpty() || label.length()>63) return "";
            return host;
        } catch(IllegalArgumentException e) { return ""; }
    }
    /** Only hostnames; a search query or an unparseable address is not a website. */
    public static String hostFromAddress(String value) {
        if(value==null) return "";
        value=value.trim();
        if(value.isEmpty() || value.matches(".*\\s+.*")) return "";
        try {
            URI uri=new URI(value.contains("://")?value:"https://"+value);
            if(!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) return "";
            String authority=uri.getRawAuthority();
            if(authority==null || authority.contains("@")) return "";
            String host=uri.getHost();
            if(host==null) { // URI#getHost is null for a Unicode hostname.
                int colon=authority.lastIndexOf(':');
                host=colon>=0?authority.substring(0,colon):authority;
            }
            host=normalizeHost(host);
            return host.contains(".")?host:"";
        } catch(Exception e) { return ""; }
    }
    public static boolean relatedHost(String a,String b) {
        a=normalizeHost(a); b=normalizeHost(b);
        return !a.isEmpty() && !b.isEmpty() && (a.equals(b) || a.endsWith("."+b) || b.endsWith("."+a));
    }
}

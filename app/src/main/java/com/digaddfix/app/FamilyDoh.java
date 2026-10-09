package com.digaddfix.app;

import android.content.Context;
import android.net.*;
import com.digaddfix.core.FilterEngine;
import java.io.*;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;

/** Uses an underlying Internet network and normal TLS certificate/hostname validation. */
final class FamilyDoh implements FilterEngine.Transport {
    private final ConnectivityManager connectivity;
    FamilyDoh(Context context) { connectivity=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE); }
    private Network underlying() throws IOException {
        Network active=connectivity.getActiveNetwork();
        NetworkCapabilities caps=active==null?null:connectivity.getNetworkCapabilities(active);
        if(caps!=null && !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return active;
        Network fallback=null;
        for(Network network:connectivity.getAllNetworks()) {
            caps=connectivity.getNetworkCapabilities(network);
            if(caps==null || caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue;
            if(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) return network;
            fallback=network;
        }
        if(fallback==null) throw new IOException("No underlying network");
        return fallback;
    }
    @Override public byte[] exchange(byte[] query) throws IOException {
        HttpsURLConnection connection=(HttpsURLConnection)underlying().openConnection(new URL("https://family.cloudflare-dns.com/dns-query"));
        connection.setConnectTimeout(5000);connection.setReadTimeout(5000);
        connection.setRequestMethod("POST");connection.setDoOutput(true);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Content-Type","application/dns-message");
        connection.setRequestProperty("Accept","application/dns-message");
        connection.setFixedLengthStreamingMode(query.length);
        try {
            try(OutputStream out=connection.getOutputStream()) {out.write(query);}
            if(connection.getResponseCode()!=200) throw new IOException("Family resolver HTTP failure");
            String type=connection.getContentType();
            if(type==null || !type.toLowerCase(java.util.Locale.ROOT).startsWith("application/dns-message")) throw new IOException("Invalid resolver content type");
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            try(InputStream in=connection.getInputStream()) {
                byte[] buffer=new byte[2048];int count;
                while((count=in.read(buffer))!=-1) {
                    if(out.size()+count>16384) throw new IOException("Oversized resolver response");
                    out.write(buffer,0,count);
                }
            }
            if(out.size()<12) throw new IOException("Short resolver response");
            return out.toByteArray();
        } finally {connection.disconnect();}
    }
}

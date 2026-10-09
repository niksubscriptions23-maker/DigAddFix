package com.digaddfix.core;

import java.io.IOException;
import java.util.*;

public final class FilterEngine {
    public interface Transport { byte[] exchange(byte[] question) throws IOException; }
    public static final class Result {
        public final byte[] response;
        public final String host,reason;
        public final boolean blocked,failed;
        Result(byte[] response,String host,String reason,boolean blocked,boolean failed) {
            this.response=response;this.host=host;this.reason=reason;this.blocked=blocked;this.failed=failed;
        }
    }
    private final Rules rules;
    private final Transport transport;
    private static final Set<String> GOOGLE=new HashSet<>(Arrays.asList(
        "google.com","www.google.com","google.co.in","www.google.co.in",
        "google.co.uk","www.google.co.uk","google.com.au","www.google.com.au",
        "google.ca","www.google.ca","google.de","www.google.de",
        "google.fr","www.google.fr","google.co.jp","www.google.co.jp"));
    public FilterEngine(Rules rules,Transport transport) { this.rules=rules;this.transport=transport; }
    public Result resolve(byte[] request) {
        Dns.Question q=Dns.question(request);
        Dns.udpLimit(request);
        if(q.clazz!=Dns.IN) return fail(request,q.name,"Unsupported DNS class");
        Rules.Rule rule=rules.domain(q.name);
        if(rule!=null) return new Result(Dns.error(request,Dns.NXDOMAIN),q.name,rule.reason,true,false);
        try {
            byte[] reply;
            if(GOOGLE.contains(q.name)) {
                if(q.type!=Dns.A && q.type!=Dns.AAAA)
                    reply=Dns.response(q,0,Collections.<byte[]>emptyList()); // Prevent SVCB/HTTPS alternate endpoints.
                else {
                    byte[] safeQuery=Dns.query("forcesafesearch.google.com",q.type,q.id);
                    byte[] safeReply=transport.exchange(safeQuery);Dns.validateReply(safeQuery,safeReply);
                    if((safeReply[3]&15)!=0) return fail(request,q.name,"SafeSearch resolution unavailable");
                    List<byte[]> addresses=Dns.addresses(safeReply,q.type);
                    if(Dns.providerBlocked(safeReply) || (q.type==Dns.A && addresses.isEmpty()))
                        return fail(request,q.name,"SafeSearch resolution unavailable");
                    reply=Dns.response(q,0,addresses);
                }
            } else {
                reply=transport.exchange(request);Dns.validateReply(request,reply);
                if(Dns.providerBlocked(reply))
                    return new Result(Dns.error(request,Dns.NXDOMAIN),q.name,"Blocked by the family DNS filter",true,false);
                if((reply[3]&15)==Dns.SERVFAIL) return fail(request,q.name,"Family resolver unavailable");
            }
            return new Result(reply,q.name,"",false,false);
        } catch(IOException|IllegalArgumentException e) { return fail(request,q.name,"Filtering unavailable; no unfiltered fallback"); }
    }
    private Result fail(byte[] request,String host,String reason) { return new Result(Dns.error(request,Dns.SERVFAIL),host,reason,false,true); }
}

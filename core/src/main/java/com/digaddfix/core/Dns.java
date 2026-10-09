package com.digaddfix.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bounded DNS wire-format parser. Rejects invalid names and compression loops. */
public final class Dns {
    private Dns() {}
    public static final int A=1, AAAA=28, IN=1, SERVFAIL=2, NXDOMAIN=3;
    public static final class Question {
        public final int id,flags,type,clazz;
        public final String name;
        Question(int id,int flags,String name,int type,int clazz) {
            this.id=id;this.flags=flags;this.name=name;this.type=type;this.clazz=clazz;
        }
    }
    public static final class Record {
        public final int type,clazz;
        public final byte[] data;
        Record(int type,int clazz,byte[] data) { this.type=type;this.clazz=clazz;this.data=data; }
    }
    public static int u16(byte[] b,int at) {
        require(b,at,2); return (b[at]&255)<<8 | b[at+1]&255;
    }
    private static void require(byte[] b,int at,int count) {
        if(at<0 || count<0 || at>b.length-count) throw new IllegalArgumentException("Truncated DNS message");
    }
    private static String name(byte[] b,int[] cursor) {
        int p=cursor[0], jumps=0, consumed=-1, textLength=0;
        StringBuilder out=new StringBuilder();
        Set<Integer> visited=new HashSet<>();
        while(true) {
            require(b,p,1);
            if(!visited.add(p) || ++jumps>128) throw new IllegalArgumentException("DNS compression loop");
            int length=b[p++]&255;
            if((length&0xc0)==0xc0) {
                require(b,p,1); int offset=((length&63)<<8)|(b[p++]&255);
                if(consumed<0) consumed=p;
                if(offset<12 || offset>=b.length) throw new IllegalArgumentException("Invalid DNS pointer");
                p=offset; continue;
            }
            if((length&0xc0)!=0 || length>63) throw new IllegalArgumentException("Invalid DNS label");
            if(length==0) break;
            require(b,p,length);
            if(out.length()>0) out.append('.');
            for(int i=0;i<length;i++) {
                int c=b[p+i]&255;
                if(c<33 || c>126 || c=='.') throw new IllegalArgumentException("Invalid hostname label");
                out.append((char)c);
            }
            textLength+=length+1;
            if(textLength>254) throw new IllegalArgumentException("DNS name too long");
            p+=length;
        }
        cursor[0]=consumed<0?p:consumed;
        return out.toString().toLowerCase(Locale.ROOT);
    }
    public static Question question(byte[] b) {
        require(b,0,12);
        int flags=u16(b,2);
        if(u16(b,4)!=1 || (flags&0x7800)!=0) throw new IllegalArgumentException("Unsupported DNS question");
        int[] at={12}; String host=name(b,at);
        int type=u16(b,at[0]), clazz=u16(b,at[0]+2);
        if(host.isEmpty()) throw new IllegalArgumentException("Empty DNS hostname");
        return new Question(u16(b,0),flags,host,type,clazz);
    }
    public static void validateReply(byte[] query,byte[] reply) {
        Question a=question(query), b=question(reply);
        if((b.flags&0x8000)==0 || a.id!=b.id || !a.name.equals(b.name) || a.type!=b.type || a.clazz!=b.clazz)
            throw new IllegalArgumentException("Mismatched DNS reply");
        answers(reply); // Bounds-check the entire message before forwarding.
    }
    public static byte[] query(String name,int type,int id) { return message(new Question(id,0x100,name,type,IN),0,false,Collections.<byte[]>emptyList()); }
    public static byte[] error(byte[] query,int rcode) { return response(question(query),rcode,Collections.<byte[]>emptyList()); }
    public static byte[] response(Question q,int rcode,List<byte[]> addresses) { return message(q,rcode,true,addresses); }
    private static byte[] message(Question q,int rcode,boolean reply,List<byte[]> addresses) {
        try {
            ByteArrayOutputStream out=new ByteArrayOutputStream(); DataOutputStream d=new DataOutputStream(out);
            d.writeShort(q.id);
            d.writeShort(reply?(0x8080|(q.flags&0x100)|rcode):(q.flags&0x100));
            d.writeShort(1); d.writeShort(addresses.size()); d.writeShort(0); d.writeShort(0);
            String normalized=Rules.normalizeHost(q.name);
            if(normalized.isEmpty()) throw new IllegalArgumentException("Invalid DNS hostname");
            for(String label:normalized.split("\\.")) {
                byte[] text=label.getBytes(StandardCharsets.US_ASCII); d.writeByte(text.length);d.write(text);
            }
            d.writeByte(0);d.writeShort(q.type);d.writeShort(q.clazz);
            for(byte[] address:addresses) {
                if(!((q.type==A && address.length==4)||(q.type==AAAA && address.length==16)))
                    throw new IllegalArgumentException("DNS address family mismatch");
                d.writeShort(0xc00c);d.writeShort(q.type);d.writeShort(IN);d.writeInt(60);d.writeShort(address.length);d.write(address);
            }
            return out.toByteArray();
        } catch(IOException e) { throw new AssertionError(e); }
    }
    public static List<Record> answers(byte[] b) {
        question(b);
        int[] at={12}; name(b,at); require(b,at[0],4);at[0]+=4;
        int answerCount=u16(b,6), other=u16(b,8)+u16(b,10);
        if(answerCount+other>512) throw new IllegalArgumentException("Excessive DNS records");
        List<Record> records=new ArrayList<>();
        for(int i=0;i<answerCount+other;i++) {
            name(b,at);require(b,at[0],10);
            int type=u16(b,at[0]),clazz=u16(b,at[0]+2),length=u16(b,at[0]+8);
            at[0]+=10;require(b,at[0],length);
            if(i<answerCount) records.add(new Record(type,clazz,Arrays.copyOfRange(b,at[0],at[0]+length)));
            at[0]+=length;
        }
        if(at[0]!=b.length) throw new IllegalArgumentException("Trailing DNS bytes");
        return records;
    }
    public static List<byte[]> addresses(byte[] b,int type) {
        List<byte[]> result=new ArrayList<>();
        for(Record r:answers(b)) if(r.clazz==IN && r.type==type && r.data.length==(type==A?4:16)) result.add(r.data);
        return result;
    }
    public static boolean providerBlocked(byte[] b) {
        for(Record r:answers(b)) {
            if(r.clazz!=IN || !((r.type==A && r.data.length==4)||(r.type==AAAA && r.data.length==16))) continue;
            boolean zero=true;for(byte n:r.data) if(n!=0) {zero=false;break;}
            if(zero) return true;
        }
        return false;
    }
    public static int udpLimit(byte[] query) {
        // Advertise 512 by default. Inspect EDNS OPT records without trusting unbounded lengths.
        Question q=question(query);
        int[] at={12};name(query,at);at[0]+=4;
        int records=u16(query,6)+u16(query,8)+u16(query,10);
        if(records>32) throw new IllegalArgumentException("Excessive query records");
        int limit=512;
        for(int i=0;i<records;i++) {
            name(query,at);require(query,at[0],10);
            int type=u16(query,at[0]),clazz=u16(query,at[0]+2),len=u16(query,at[0]+8);
            at[0]+=10;require(query,at[0],len);at[0]+=len;
            if(type==41) limit=Math.min(4096,Math.max(512,clazz));
        }
        if(at[0]!=query.length || (q.flags&0x8000)!=0) throw new IllegalArgumentException("Invalid query");
        return limit;
    }
    public static byte[] forUdp(byte[] query,byte[] reply) {
        if(reply.length<=udpLimit(query)) return reply;
        byte[] truncated=response(question(query),0,Collections.<byte[]>emptyList());
        truncated[2]|=2; // TC: client retries using TCP, which the tunnel also supports.
        return truncated;
    }
}

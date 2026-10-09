package com.digaddfix.core;

import java.util.Arrays;

/** IPv4/IPv6 UDP and TCP packets for the two virtual DNS addresses only. */
public final class IpPacket {
    public final int version,protocol,sourcePort,destinationPort,flags;
    public final long sequence,acknowledgment;
    public final byte[] source,destination,payload;
    private IpPacket(int version,int protocol,byte[] source,byte[] destination,int sp,int dp,int flags,long seq,long ack,byte[] data) {
        this.version=version;this.protocol=protocol;this.source=source;this.destination=destination;
        sourcePort=sp;destinationPort=dp;this.flags=flags;sequence=seq;acknowledgment=ack;payload=data;
    }
    public static IpPacket parse(byte[] b,int size) {
        if(size<20 || size>b.length) throw new IllegalArgumentException("Short IP packet");
        int version=(b[0]&255)>>>4,offset,end,protocol;byte[] src,dst;
        if(version==4) {
            offset=(b[0]&15)*4;end=Dns.u16(b,2);
            if(offset<20 || end>size || end<offset || (Dns.u16(b,6)&0x3fff)!=0) throw new IllegalArgumentException("Fragmented/invalid IPv4");
            if(checksum(b,0,offset,0)!=0) throw new IllegalArgumentException("Invalid IPv4 checksum");
            protocol=b[9]&255;src=Arrays.copyOfRange(b,12,16);dst=Arrays.copyOfRange(b,16,20);
        } else if(version==6) {
            if(size<40) throw new IllegalArgumentException("Short IPv6");
            offset=40;end=40+Dns.u16(b,4);protocol=b[6]&255;
            if(end>size) throw new IllegalArgumentException("Short IPv6 payload");
            src=Arrays.copyOfRange(b,8,24);dst=Arrays.copyOfRange(b,24,40);
            // This endpoint does not accept IPv6 extension headers/fragments.
        } else throw new IllegalArgumentException("Unsupported IP version");
        if(protocol!=17 && protocol!=6) throw new IllegalArgumentException("Unsupported transport");
        int minimum=protocol==17?8:20;
        if(end-offset<minimum) throw new IllegalArgumentException("Short transport");
        int sp=Dns.u16(b,offset),dp=Dns.u16(b,offset+2),flags=0;long seq=0,ack=0;
        if(protocol==17) {
            int length=Dns.u16(b,offset+4);
            if(length<8 || length!=end-offset) throw new IllegalArgumentException("Invalid UDP length");
            int sum=Dns.u16(b,offset+6);
            if((version==6 || sum!=0) && checksum(b,offset,end-offset,pseudo(src,dst,protocol,end-offset))!=0)
                throw new IllegalArgumentException("Invalid UDP checksum");
            offset+=8;
        } else {
            if(checksum(b,offset,end-offset,pseudo(src,dst,protocol,end-offset))!=0)
                throw new IllegalArgumentException("Invalid TCP checksum");
            int header=((b[offset+12]&255)>>>4)*4;
            if(header<20 || header>end-offset) throw new IllegalArgumentException("Invalid TCP header");
            seq=u32(b,offset+4);ack=u32(b,offset+8);flags=b[offset+13]&255;offset+=header;
        }
        return new IpPacket(version,protocol,src,dst,sp,dp,flags,seq,ack,Arrays.copyOfRange(b,offset,end));
    }
    public String connectionKey() { return Arrays.toString(source)+":"+sourcePort+":"+Arrays.toString(destination); }
    public byte[] reply(byte[] data) { return build(version,protocol,destination,source,destinationPort,sourcePort,0,0,0,data); }
    public byte[] tcpReply(int flags,long seq,long ack,byte[] data) {
        return build(version,6,destination,source,destinationPort,sourcePort,flags,seq,ack,data);
    }
    public static byte[] build(int version,int protocol,byte[] src,byte[] dst,int sp,int dp,int flags,long seq,long ack,byte[] data) {
        int ip=version==4?20:40,transport=protocol==17?8:20,length=transport+data.length;
        if(ip+length>65535 || src.length!=(version==4?4:16) || dst.length!=src.length)
            throw new IllegalArgumentException("Invalid packet size/family");
        byte[] b=new byte[ip+length];
        if(version==4) { b[0]=0x45;put16(b,2,b.length);b[8]=64;b[9]=(byte)protocol;System.arraycopy(src,0,b,12,4);System.arraycopy(dst,0,b,16,4);put16(b,10,checksum(b,0,20,0)); }
        else { b[0]=0x60;put16(b,4,length);b[6]=(byte)protocol;b[7]=64;System.arraycopy(src,0,b,8,16);System.arraycopy(dst,0,b,24,16); }
        put16(b,ip,sp);put16(b,ip+2,dp);
        if(protocol==17) put16(b,ip+4,length);
        else { put32(b,ip+4,seq);put32(b,ip+8,ack);b[ip+12]=0x50;b[ip+13]=(byte)flags;put16(b,ip+14,65535); }
        System.arraycopy(data,0,b,ip+transport,data.length);
        int c=checksum(b,ip,length,pseudo(src,dst,protocol,length));
        put16(b,ip+(protocol==17?6:16),c==0?65535:c);
        return b;
    }
    private static int pseudo(byte[] src,byte[] dst,int protocol,int length) {
        long sum=protocol+length;
        for(int i=0;i<src.length;i+=2) sum+=Dns.u16(src,i)+Dns.u16(dst,i);
        return (int)sum;
    }
    public static int checksum(byte[] b,int start,int length,int initial) {
        long sum=initial;int end=start+length,i=start;
        for(;i+1<end;i+=2) sum+=Dns.u16(b,i);
        if(i<end) sum+=(b[i]&255)<<8;
        while((sum>>>16)!=0) sum=(sum&65535)+(sum>>>16);
        return (int)(~sum)&65535;
    }
    public static long u32(byte[] b,int at) { return ((long)Dns.u16(b,at)<<16)|Dns.u16(b,at+2); }
    public static void put16(byte[] b,int at,int n) { b[at]=(byte)(n>>>8);b[at+1]=(byte)n; }
    public static void put32(byte[] b,int at,long n) { put16(b,at,(int)(n>>>16));put16(b,at+2,(int)n); }
}

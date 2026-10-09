package com.digaddfix.core;

import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.*;

/** Small bounded TCP endpoint for DNS at the virtual resolver, not a traffic proxy. */
public final class TcpDns {
    public interface Sink { void write(byte[] packet); }
    public interface Resolver { void resolve(byte[] query, Completion completion); }
    public interface Completion { void finish(byte[] response); }
    private static final long MASK=0xffffffffL;
    private static final byte[] EMPTY=new byte[0];
    private final Sink sink;
    private final Resolver resolver;
    private final SecureRandom random=new SecureRandom();
    private final Map<String,Session> sessions=new HashMap<>();
    private static final class Session {
        IpPacket packet;
        long clientInitial,expected,initial,next,lastSeen,lastStart;
        byte[] last=EMPTY;
        final ByteArrayOutputStream input=new ByteArrayOutputStream();
        boolean busy,peerFin,finSent;
    }
    public TcpDns(Sink sink,Resolver resolver) { this.sink=sink;this.resolver=resolver; }
    public synchronized void receive(IpPacket p,long now) {
        if(p.protocol!=6 || p.destinationPort!=53) return;
        Iterator<Session> expired=sessions.values().iterator();
        while(expired.hasNext()) if(now-expired.next().lastSeen>30000) expired.remove();
        String key=p.connectionKey(); Session s=sessions.get(key);
        if((p.flags&4)!=0) { sessions.remove(key);return; }
        if((p.flags&2)!=0) {
            if(s==null || s.clientInitial!=p.sequence) {
                if(sessions.size()>=64) return;
                s=new Session();s.packet=p;s.clientInitial=p.sequence;s.expected=add(p.sequence,1);
                s.initial=random.nextInt()&MASK;s.next=add(s.initial,1);sessions.put(key,s);
            }
            s.lastSeen=now;sink.write(p.tcpReply(0x12,s.initial,s.expected,EMPTY));return;
        }
        if(s==null) { sink.write(p.tcpReply(0x14,p.acknowledgment,add(p.sequence,p.payload.length+((p.flags&1)!=0?1:0)),EMPTY));return; }
        s.lastSeen=now;s.packet=p;
        if((p.flags&16)==0) return;
        // Reject ACKs ahead of anything sent. Serial-number arithmetic handles wraparound.
        if(after(p.acknowledgment,s.next)) return;
        if(p.acknowledgment==s.next) {
            s.last=EMPTY;
            if(s.finSent) { sessions.remove(key);return; }
        }
        if(p.sequence!=s.expected) {
            sink.write(p.tcpReply(0x10,s.next,s.expected,EMPTY));
            if(s.last.length>0) sendData(s,s.lastStart,s.last);
            return;
        }
        if(p.payload.length>0) {
            if(s.finSent || s.input.size()+p.payload.length>8192) { reset(key,s);return; }
            s.input.write(p.payload,0,p.payload.length);s.expected=add(s.expected,p.payload.length);
            sink.write(p.tcpReply(0x10,s.next,s.expected,EMPTY));
        }
        if((p.flags&1)!=0) {
            s.peerFin=true;s.expected=add(s.expected,1);
            sink.write(p.tcpReply(0x10,s.next,s.expected,EMPTY));
        }
        pump(key,s);
        if(s.peerFin && !s.busy && s.input.size()==0) finish(s);
    }
    private void pump(final String key,final Session s) {
        if(s.busy || s.finSent || s.last.length>0) return;
        byte[] buffer=s.input.toByteArray();
        if(buffer.length<2) { if(s.peerFin && buffer.length>0) reset(key,s);return; }
        int length=Dns.u16(buffer,0);
        if(length<12 || length>4096) { reset(key,s);return; }
        if(buffer.length<length+2) { if(s.peerFin) reset(key,s);return; }
        final byte[] query=Arrays.copyOfRange(buffer,2,length+2);
        s.input.reset();s.input.write(buffer,length+2,buffer.length-length-2);s.busy=true;
        resolver.resolve(query,new Completion() {
            @Override public void finish(byte[] response) {
                synchronized(TcpDns.this) {
                    if(sessions.get(key)!=s) return;
                    s.busy=false;
                    if(response==null || response.length>16384) { reset(key,s);return; }
                    byte[] framed=new byte[response.length+2];IpPacket.put16(framed,0,response.length);
                    System.arraycopy(response,0,framed,2,response.length);
                    s.last=framed;s.lastStart=s.next;sendData(s,s.next,framed);s.next=add(s.next,framed.length);
                    // Wait for an ACK before processing another frame, avoiding overwriting retransmission data.
                    if(s.peerFin && s.input.size()==0) TcpDns.this.finish(s);
                }
            }
        });
    }
    private void sendData(Session s,long start,byte[] framed) {
        for(int at=0;at<framed.length;at+=512) {
            byte[] part=Arrays.copyOfRange(framed,at,Math.min(at+512,framed.length));
            sink.write(s.packet.tcpReply(0x18,add(start,at),s.expected,part));
        }
    }
    private void finish(Session s) {
        if(s.finSent) return;
        sink.write(s.packet.tcpReply(0x11,s.next,s.expected,EMPTY));s.next=add(s.next,1);s.finSent=true;
    }
    private void reset(String key,Session s) { sink.write(s.packet.tcpReply(0x14,s.next,s.expected,EMPTY));sessions.remove(key); }
    private static long add(long a,long b) { return (a+b)&MASK; }
    private static boolean after(long a,long b) { long d=(a-b)&MASK;return d>0 && d<0x80000000L; }
}

package com.digaddfix.core;

import java.io.*;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

/** Dependency-free executable regression tests with malformed/protocol fixtures. */
public final class CoreChecks {
    private static int checks;
    private static void check(boolean value,String name) {checks++;if(!value) throw new AssertionError(name);}
    private static void rejects(Runnable operation,String name) {
        boolean rejected=false;try {operation.run();} catch(IllegalArgumentException expected) {rejected=true;}
        check(rejected,name);
    }
    private static Reader reader(Path root,String file) throws IOException {
        return Files.newBufferedReader(root.resolve("app/src/main/assets/"+file),StandardCharsets.UTF_8);
    }
    public static void main(String[] args) throws Exception {
        checks+=TamperChecks.run();
        Path root=Paths.get(args[0]);Rules rules=new Rules(reader(root,"blocked-apps.tsv"),reader(root,"blocked-domains.tsv"));
        check(rules.domain("www.INSTAGRAM.com.")!=null,"case/trailing-dot/subdomain");
        check(rules.domain("notinstagram.com")==null,"prefix lookalike allowed");
        check(rules.domain("instagram.com.example.org")==null,"suffix lookalike allowed");
        check(rules.app("com.instagram.android")!=null,"package block");
        check(rules.app("com.instagram.android.clone")==null,"exact package boundary");
        for(Browsers.Browser browser:Browsers.all()) {
            check(rules.app(browser.packageName)==null,browser.label+" is never app-blocked");
            boolean rejected=false;
            try {new Rules(new StringReader(browser.packageName+"|Browser|Block\n"),new StringReader(""));}
            catch(IOException expected) {rejected=true;}
            check(rejected,"reject contradictory supported-browser app rule: "+browser.label);
        }
        List<String> supported=Arrays.asList("com.brave.browser","com.android.chrome","org.mozilla.firefox");
        for(int mask=0;mask<8;mask++) {
            List<String> installed=new ArrayList<>(),expected=new ArrayList<>();
            for(int i=0;i<3;i++) if((mask&(1<<i))!=0) {installed.add(supported.get(i));expected.add(supported.get(i));}
            installed.addAll(Arrays.asList("com.instagram.android","com.microsoft.emmx","com.android.chrome.clone","org.mozilla.firefox_beta"));
            check(Browsers.installedScope(installed).equals(expected),"all browser installation combinations isolate non-browser and unsupported packages: "+mask);
        }
        check(Browsers.installedScope(Collections.singletonList("com.android.chrome")).equals(Collections.singletonList("com.android.chrome")),"Chrome-only scope does not require Brave");
        check(Browsers.installedScope(Collections.singletonList("org.mozilla.firefox")).equals(Collections.singletonList("org.mozilla.firefox")),"Firefox-only scope does not require Brave");
        check(Browsers.installedScope(Collections.<String>emptyList()).isEmpty(),"missing supported browsers cannot imply all-app scope");
        check(Browsers.find("com.android.chrome").isAddressNode("com.android.chrome:id/url_bar"),"Chrome committed address node");
        check(Browsers.find("org.mozilla.firefox").isAddressNode("org.mozilla.firefox:id/mozac_browser_toolbar_url_view"),"Firefox committed address node");
        check(!Browsers.find("org.mozilla.firefox").isAddressNode("com.android.chrome:id/url_bar"),"never read another browser's node as Firefox");
        check(!Browsers.find("com.android.chrome").isAddressNode("com.android.chrome:id/password"),"never read arbitrary text or input nodes");
        for(String blocked:Arrays.asList("com.microsoft.emmx","com.sec.android.app.sbrowser","com.opera.browser","com.opera.mini.native","com.duckduckgo.mobile.android","com.vivaldi.browser","org.torproject.torbrowser","com.UCMobile.intl","com.mi.globalbrowser","com.yandex.browser","com.brave.browser_beta","com.chrome.beta","org.mozilla.firefox_beta"))
            check(rules.app(blocked)!=null,"other known browser on app blocklist: "+blocked);
        check(Rules.hostFromAddress("https://instagram.com/watch?x=1").equals("instagram.com"),"address extraction");
        check(Rules.hostFromAddress("example.org:443/path").equals("example.org"),"bare host with port");
        check(Rules.hostFromAddress("find an instagram page").isEmpty(),"typed search is not a host");
        check(Rules.hostFromAddress("https://instagram.com@evil.example").isEmpty(),"reject misleading userinfo");
        check(Rules.normalizeHost("bücher.example").equals("xn--bcher-kva.example"),"IDN normalization");
        check(!Rules.relatedHost("bad.example","good.example"),"unrelated background request");
        check(Rules.relatedHost("www.bad.example","bad.example"),"related navigation host");

        byte[] q=Dns.query("example.org",Dns.A,42);
        check(Dns.question(q).name.equals("example.org") && Dns.udpLimit(q)==512,"DNS query fixture");
        byte[] answer=Dns.response(Dns.question(q),0,Collections.singletonList(new byte[]{93,(byte)184,(byte)216,34}));
        Dns.validateReply(q,answer);check(!Dns.providerBlocked(answer),"ordinary DNS address");
        byte[] sink=Dns.response(Dns.question(q),0,Collections.singletonList(new byte[4]));
        check(Dns.providerBlocked(sink),"IPv4 family sinkhole");
        byte[] q6=Dns.query("example.org",Dns.AAAA,43);
        check(Dns.providerBlocked(Dns.response(Dns.question(q6),0,Collections.singletonList(new byte[16]))),"IPv6 family sinkhole");
        rejects(()->Dns.validateReply(q,Dns.response(Dns.question(Dns.query("evil.example",Dns.A,42)),0,Collections.<byte[]>emptyList())),"mismatched reply host");
        byte[] badId=answer.clone();badId[1]=99;rejects(()->Dns.validateReply(q,badId),"mismatched reply ID");
        byte[] loop=q.clone();loop[12]=(byte)0xc0;loop[13]=12;rejects(()->Dns.question(loop),"compression cycle");
        rejects(()->Dns.question(new byte[11]),"truncated header");
        byte[] trailing=Arrays.copyOf(answer,answer.length+1);rejects(()->Dns.answers(trailing),"trailing garbage");
        byte[] nx=Dns.error(q,Dns.NXDOMAIN);check(!Dns.providerBlocked(nx),"NXDOMAIN is not labelled adult content");
        check((Dns.error(q,Dns.SERVFAIL)[3]&15)==2,"failure response");
        List<byte[]> large=new ArrayList<>();for(int i=0;i<60;i++) large.add(new byte[]{1,2,3,4});
        byte[] truncated=Dns.forUdp(q,Dns.response(Dns.question(q),0,large));
        check((truncated[2]&2)!=0 && truncated.length<512,"large UDP answer requests TCP retry");
        Random random=new Random(53);
        for(int i=0;i<5000;i++) {
            byte[] fuzz=new byte[random.nextInt(300)];random.nextBytes(fuzz);
            try {Dns.question(fuzz);Dns.answers(fuzz);} catch(IllegalArgumentException expected) {}
        }
        check(true,"5000 malformed DNS fixtures terminate with bounded parsing");

        int[] upstreamCalls={0};
        FilterEngine.Transport upstream=request->{
            upstreamCalls[0]++;Dns.Question x=Dns.question(request);
            if(x.name.equals("forcesafesearch.google.com")) return Dns.response(x,0,Collections.singletonList(new byte[]{(byte)216,(byte)239,38,120}));
            return Dns.response(x,0,Collections.singletonList(new byte[4]));
        };
        FilterEngine engine=new FilterEngine(rules,upstream);
        FilterEngine.Result local=engine.resolve(Dns.query("www.instagram.com",Dns.A,1));
        check(local.blocked && !local.failed && upstreamCalls[0]==0,"local block never reaches provider");
        FilterEngine.Result provider=engine.resolve(q);
        check(provider.blocked && (provider.response[3]&15)==3,"family sinkhole becomes a hard block");
        FilterEngine.Result safe=engine.resolve(Dns.query("www.google.co.in",Dns.A,1));
        check(!safe.blocked && Dns.addresses(safe.response,Dns.A).get(0)[2]==38,"Google mapped to SafeSearch");
        int before=upstreamCalls[0];
        FilterEngine.Result svcb=engine.resolve(Dns.query("www.google.com",65,9));
        check(Dns.answers(svcb.response).isEmpty() && upstreamCalls[0]==before,"HTTPS alternate endpoint suppressed");
        FilterEngine.Result outage=new FilterEngine(rules,request->{throw new IOException("Offline");}).resolve(q);
        check(outage.failed && !outage.blocked && (outage.response[3]&15)==2,"outage is not mislabelled and has no fallback");
        FilterEngine.Result normalNx=new FilterEngine(rules,request->Dns.error(request,3)).resolve(q);
        check(!normalNx.blocked && !normalNx.failed,"nonexistent hostname retains ordinary NXDOMAIN");
        check(new FilterEngine(rules,request->badId).resolve(q).failed,"provider mismatch fails closed");

        byte[] client4={10,111,0,2},dns4={10,111,0,1};
        byte[] packet=IpPacket.build(4,17,client4,dns4,32123,53,0,0,0,q);
        IpPacket parsed=IpPacket.parse(packet,packet.length);
        check(Arrays.equals(parsed.payload,q) && parsed.destinationPort==53,"IPv4 DNS packet");
        IpPacket reply=IpPacket.parse(parsed.reply(answer),parsed.reply(answer).length);
        check(reply.destinationPort==32123 && Arrays.equals(reply.destination,client4),"IPv4 response endpoint/checksums");
        byte[] client6=InetAddress.getByName("fd7f:da:f1::2").getAddress(),dns6=InetAddress.getByName("fd7f:da:f1::1").getAddress();
        byte[] packet6=IpPacket.build(6,17,client6,dns6,12345,53,0,0,0,q6);
        check(IpPacket.parse(packet6,packet6.length).version==6,"IPv6 UDP and mandatory checksum");
        byte[] corrupt=packet6.clone();corrupt[corrupt.length-1]^=1;rejects(()->IpPacket.parse(corrupt,corrupt.length),"corrupt UDP rejected");
        byte[] fragment=packet.clone();fragment[6]=0x20;rejects(()->IpPacket.parse(fragment,fragment.length),"fragment rejected");
        byte[] odd=IpPacket.build(4,17,client4,dns4,32123,53,0,0,0,new byte[]{1,2,3});
        check(IpPacket.parse(odd,odd.length).payload.length==3,"odd-length checksum");
        for(int i=0;i<5000;i++) {
            byte[] fuzz=new byte[random.nextInt(300)];random.nextBytes(fuzz);
            try {IpPacket.parse(fuzz,fuzz.length);} catch(IllegalArgumentException expected) {}
        }
        check(true,"5000 malformed IP fixtures terminate");

        List<IpPacket> sent=new ArrayList<>();
        TcpDns tcp=new TcpDns(bytes->sent.add(IpPacket.parse(bytes,bytes.length)),(request,done)->done.finish(Dns.error(request,3)));
        byte[] syn=IpPacket.build(4,6,client4,dns4,32123,53,2,100,0,new byte[0]);
        tcp.receive(IpPacket.parse(syn,syn.length),0);
        check(sent.size()==1 && sent.get(0).flags==0x12 && sent.get(0).acknowledgment==101,"TCP SYN/ACK");
        long serverNext=(sent.get(0).sequence+1)&0xffffffffL;
        byte[] framed=new byte[q.length+2];IpPacket.put16(framed,0,q.length);System.arraycopy(q,0,framed,2,q.length);
        byte[] part1=IpPacket.build(4,6,client4,dns4,32123,53,0x18,101,serverNext,Arrays.copyOfRange(framed,0,5));
        tcp.receive(IpPacket.parse(part1,part1.length),1);
        check(sent.get(sent.size()-1).payload.length==0,"TCP incomplete frame waits");
        byte[] part2=IpPacket.build(4,6,client4,dns4,32123,53,0x18,106,serverNext,Arrays.copyOfRange(framed,5,framed.length));
        tcp.receive(IpPacket.parse(part2,part2.length),2);
        IpPacket tcpAnswer=sent.get(sent.size()-1);
        check(tcpAnswer.flags==0x18 && Dns.u16(tcpAnswer.payload,0)==tcpAnswer.payload.length-2,"TCP segmented DNS reply framing");
        byte[] parsedDns=Arrays.copyOfRange(tcpAnswer.payload,2,tcpAnswer.payload.length);
        check((parsedDns[3]&15)==3,"TCP uses same blocking policy response");

        OverlayClock overlay=new OverlayClock();
        check(overlay.start("app:a",1000) && overlay.seconds(1000)==5,"five-second overlay starts");
        check(!overlay.start("app:a",3000) && overlay.remaining(5999)==1,"duplicate does not extend overlay");
        check(overlay.remaining(6000)==0 && overlay.seconds(6000)==0,"overlay closes at exactly five seconds");
        check(overlay.start("site:b",6000) && overlay.remaining(6000)==5000,"new block gets its own five seconds");
        overlay.clear();check(overlay.remaining(6000)==0,"overlay cleanup");

        Document manifest=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(root.resolve("app/src/main/AndroidManifest.xml").toFile());
        Element application=(Element)manifest.getElementsByTagName("application").item(0);
        check("false".equals(application.getAttribute("android:allowBackup")) && "false".equals(application.getAttribute("android:fullBackupContent")),"credentials excluded from legacy backup");
        check("@xml/data_extraction_rules".equals(application.getAttribute("android:dataExtractionRules")),"Android 12+ transfer exclusions are connected");
        Document extraction=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(root.resolve("app/src/main/res/xml/data_extraction_rules.xml").toFile());
        for(String mode:Arrays.asList("cloud-backup","device-transfer")) {
            Element section=(Element)extraction.getElementsByTagName(mode).item(0);
            check(section!=null && section.getElementsByTagName("include").getLength()==0,"no credential transfer includes: "+mode);
            Set<String> excluded=new HashSet<>();NodeList exclusions=section.getElementsByTagName("exclude");
            for(int i=0;i<exclusions.getLength();i++) {Element exclusion=(Element)exclusions.item(i);if(".".equals(exclusion.getAttribute("path"))) excluded.add(exclusion.getAttribute("domain"));}
            check(excluded.containsAll(Arrays.asList("root","file","database","sharedpref","external","device_root","device_file","device_database","device_sharedpref")),"all app data domains excluded from "+mode);
        }
        Set<String> visiblePackages=new HashSet<>();NodeList packages=manifest.getElementsByTagName("package");
        for(int i=0;i<packages.getLength();i++) visiblePackages.add(((Element)packages.item(i)).getAttribute("android:name"));
        for(Rules.Rule rule:rules.apps()) check(visiblePackages.contains(rule.key),"installed-package visibility: "+rule.key);
        for(Browsers.Browser browser:Browsers.all()) check(visiblePackages.contains(browser.packageName),"supported-browser visibility: "+browser.label);
        String vpn=new String(Files.readAllBytes(root.resolve("app/src/main/java/com/digaddfix/app/BrowserDnsService.java")),StandardCharsets.UTF_8);
        check(vpn.contains("for(String packageName:scope) builder.addAllowedApplication(packageName)") && !vpn.contains(".addRoute(\"0.0.0.0\",0)") && !vpn.contains(".addRoute(\"::\",0)"),"only installed supported packages use DNS-only VPN routing");
        check(vpn.contains("if(scope.isEmpty()) throw") && vpn.indexOf("if(scope.isEmpty()) throw")<vpn.indexOf("new Builder()"),"empty supported scope never establishes an all-app VPN");
        System.out.println("PASS: "+checks+" checks (including 10,000 malformed DNS/IP fixtures)");
    }
}

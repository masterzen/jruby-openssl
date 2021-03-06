/***** BEGIN LICENSE BLOCK *****
 * Version: CPL 1.0/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Common Public
 * License Version 1.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.eclipse.org/legal/cpl-v10.html
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 * Copyright (C) 2006, 2007 Ola Bini <ola@ologix.com>
 * 
 * Alternatively, the contents of this file may be used under the terms of
 * either of the GNU General Public License Version 2 or later (the "GPL"),
 * or the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the CPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the CPL, the GPL or the LGPL.
 ***** END LICENSE BLOCK *****/
package org.jruby.ext.openssl;

import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.DERBitString;
import org.bouncycastle.asn1.DERIA5String;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.DERObjectIdentifier;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.jce.netscape.NetscapeCertRequest;
import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyModule;
import org.jruby.RubyObject;
import org.jruby.RubyString;
import org.jruby.anno.JRubyMethod;
import org.jruby.runtime.Block;
import org.jruby.runtime.ObjectAllocator;
import org.jruby.runtime.builtin.IRubyObject;
import org.jvyamlb.util.Base64Coder;

/**
 * @author <a href="mailto:ola.bini@ki.se">Ola Bini</a>
 */
public class NetscapeSPKI extends RubyObject {
    private static ObjectAllocator NETSCAPESPKI_ALLOCATOR = new ObjectAllocator() {
        public IRubyObject allocate(Ruby runtime, RubyClass klass) {
            return new NetscapeSPKI(runtime, klass);
        }
    };
    
    public static void createNetscapeSPKI(Ruby runtime, RubyModule ossl) {
        RubyModule mNetscape = ossl.defineModuleUnder("Netscape");
        RubyClass cSPKI = mNetscape.defineClassUnder("SPKI",runtime.getObject(),NETSCAPESPKI_ALLOCATOR);
        RubyClass openSSLError = ossl.getClass("OpenSSLError");
        mNetscape.defineClassUnder("SPKIError",openSSLError,openSSLError.getAllocator());

        cSPKI.defineAnnotatedMethods(NetscapeSPKI.class);
    }

    public NetscapeSPKI(Ruby runtime, RubyClass type) {
        super(runtime,type);
    }

    private IRubyObject public_key;
    private IRubyObject challenge;

    private NetscapeCertRequest cert;

    @JRubyMethod(name="initialize", rest=true)
    public IRubyObject _initialize(IRubyObject[] args) throws Exception {
        if(args.length > 0) {
            byte[] b = args[0].convertToString().getBytes();
            try {
                b = Base64Coder.decode(b);
            } catch(Exception e) {
            }
            final byte[] b2 = b;

            final String[] result1 = new String[1];
            final byte[][] result2 = new byte[1][];

            OpenSSLReal.doWithBCProvider(new Runnable() {
                    public void run() {
                        try {
                            cert = new NetscapeCertRequest(b2); //Uses "BC" as provider
                            challenge = getRuntime().newString(cert.getChallenge()); //Uses "BC" as provider
                            result1[0] = cert.getPublicKey().getAlgorithm(); //Uses "BC" as provider
                            result2[0] = cert.getPublicKey().getEncoded(); //Uses "BC" as provider
                        } catch(java.io.IOException e) {
                        }
                    }
                });

            String algo = result1[0];
            byte[] enc = result2[0];

            if("RSA".equalsIgnoreCase(algo)) {
                this.public_key = ((RubyModule)(getRuntime().getModule("OpenSSL").getConstant("PKey"))).getClass("RSA").callMethod(getRuntime().getCurrentContext(),"new",RubyString.newString(getRuntime(), enc));
            } else if("DSA".equalsIgnoreCase(algo)) {
                this.public_key = ((RubyModule)(getRuntime().getModule("OpenSSL").getConstant("PKey"))).getClass("DSA").callMethod(getRuntime().getCurrentContext(),"new",RubyString.newString(getRuntime(), enc));
            } else {
                throw getRuntime().newLoadError("not implemented algo for public key: " + algo);
            }
        }
        return this;
    }

    @JRubyMethod
    public IRubyObject to_der() throws Exception {
        DERSequence b = (DERSequence)cert.toASN1Object();
        DERObjectIdentifier encType = null;
        DERBitString publicKey = new DERBitString(((PKey)public_key).to_der().convertToString().getBytes());
        DERIA5String challenge = new DERIA5String(this.challenge.toString());
        DERObjectIdentifier sigAlg = null;
        DERBitString sig = null;
        encType = (DERObjectIdentifier)((DERSequence)((DERSequence)((DERSequence)b.getObjectAt(0)).getObjectAt(0)).getObjectAt(0)).getObjectAt(0);
        sigAlg = ((AlgorithmIdentifier)b.getObjectAt(1)).getObjectId();
        sig = (DERBitString)b.getObjectAt(2);

        ASN1EncodableVector v1 = new ASN1EncodableVector();
        ASN1EncodableVector v1_2 = new ASN1EncodableVector();
        ASN1EncodableVector v2 = new ASN1EncodableVector();
        ASN1EncodableVector v3 = new ASN1EncodableVector();
        ASN1EncodableVector v4 = new ASN1EncodableVector();
        v4.add(encType);
        v4.add(new DERNull());
        v3.add(new DERSequence(v4));
        v3.add(publicKey);
        v2.add(new DERSequence(v3));
        v2.add(challenge);
        v1.add(new DERSequence(v2));
        v1_2.add(sigAlg);
        v1_2.add(new DERNull());
        v1.add(new DERSequence(v1_2));
        v1.add(sig);
        return RubyString.newString(getRuntime(), new DERSequence(v1).getEncoded());
    }

    @JRubyMethod(name={"to_pem","to_s"})
    public IRubyObject to_pem() throws Exception {
        return getRuntime().newString(Base64Coder.encode(to_der().toString()));
    }

    @JRubyMethod
    public IRubyObject to_text() {
        System.err.println("WARNING: calling unimplemented method: to_text");
        return getRuntime().getNil();
    }

    @JRubyMethod
    public IRubyObject public_key() {
        return this.public_key;
    }

    @JRubyMethod(name="public_key=")
    public IRubyObject set_public_key(IRubyObject arg) {
        this.public_key = arg;
        return arg;
    }

    @JRubyMethod
    public IRubyObject sign(final IRubyObject key, IRubyObject digest) throws Exception {
        String keyAlg = ((PKey)key).getAlgorithm();
        String digAlg = ((Digest)digest).getAlgorithm();
        DERObjectIdentifier alg = (DERObjectIdentifier)(ASN1.getOIDLookup(getRuntime()).get(keyAlg.toLowerCase() + "-" + digAlg.toLowerCase()));
        cert = new NetscapeCertRequest(challenge.toString(),new AlgorithmIdentifier(alg),((PKey)public_key).getPublicKey());

        OpenSSLReal.doWithBCProvider(new Runnable() {
                public void run() {
                    try {
                        cert.sign(((PKey)key).getPrivateKey());
                    } catch(java.security.GeneralSecurityException e) {}
                }
            });
        return this;
    }

    @JRubyMethod
    public IRubyObject verify(final IRubyObject pkey) throws Exception {
        cert.setPublicKey(((PKey)pkey).getPublicKey());

        final boolean[] result = new boolean[1];
        OpenSSLReal.doWithBCProvider(new Runnable() {
                public void run() {
                    try {
                        result[0] = cert.verify(challenge.toString());
                    } catch(java.security.GeneralSecurityException e) {}
                }
            });

        return result[0] ? getRuntime().getTrue() : getRuntime().getFalse();
    }

    @JRubyMethod
    public IRubyObject challenge() {
        return this.challenge;
    }

    @JRubyMethod(name="challenge=")
    public IRubyObject set_challenge(IRubyObject arg) {
        this.challenge = arg;
        return arg;
    }
}// NetscapeSPKI

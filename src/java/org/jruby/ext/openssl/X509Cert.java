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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.DERObjectIdentifier;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.x509.X509V3CertificateGenerator;
import org.jruby.Ruby;
import org.jruby.RubyArray;
import org.jruby.RubyClass;
import org.jruby.RubyModule;
import org.jruby.RubyNumeric;
import org.jruby.RubyObject;
import org.jruby.RubyString;
import org.jruby.RubyTime;
import org.jruby.anno.JRubyMethod;
import org.jruby.exceptions.RaiseException;
import org.jruby.ext.openssl.x509store.PEMInputOutput;
import org.jruby.ext.openssl.x509store.X509AuxCertificate;
import org.jruby.runtime.Arity;
import org.jruby.runtime.Block;
import org.jruby.runtime.ObjectAllocator;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.ByteList;

/**
 * @author <a href="mailto:ola.bini@ki.se">Ola Bini</a>
 */
public class X509Cert extends RubyObject {
    private static ObjectAllocator X509CERT_ALLOCATOR = new ObjectAllocator() {
        public IRubyObject allocate(Ruby runtime, RubyClass klass) {
            return new X509Cert(runtime, klass);
        }
    };
    
    public static void createX509Cert(Ruby runtime, RubyModule mX509) {
        RubyClass cX509Cert = mX509.defineClassUnder("Certificate",runtime.getObject(),X509CERT_ALLOCATOR);
        RubyClass openSSLError = runtime.getModule("OpenSSL").getClass("OpenSSLError");
        mX509.defineClassUnder("CertificateError",openSSLError,openSSLError.getAllocator());

        cX509Cert.defineAnnotatedMethods(X509Cert.class);
    }

    public X509Cert(Ruby runtime, RubyClass type) {
        super(runtime,type);
    }

    private IRubyObject serial;
    private IRubyObject not_before;
    private IRubyObject not_after;
    private IRubyObject issuer;
    private IRubyObject subject;
    private IRubyObject public_key;

    private IRubyObject sig_alg;
    private IRubyObject version;

    private List<IRubyObject> extensions;

    private boolean changed = true;

    private X509V3CertificateGenerator generator = new X509V3CertificateGenerator();
    private X509Certificate cert;

    X509AuxCertificate getAuxCert() {
        if(null == cert) {
            return null;
        }
        if(cert instanceof X509AuxCertificate) {
            return (X509AuxCertificate)cert;
        }
        return new X509AuxCertificate(cert);
    }

    public static IRubyObject wrap(Ruby runtime, Certificate c) throws CertificateEncodingException {
        RubyClass cr = (RubyClass)(((RubyModule)(runtime.getModule("OpenSSL").getConstant("X509"))).getConstant("Certificate"));
        return cr.callMethod(runtime.getCurrentContext(),"new",RubyString.newString(runtime, c.getEncoded()));
    }

    @JRubyMethod(name="initialize", optional = 1, frame=true)
    public IRubyObject initialize(ThreadContext context, IRubyObject[] args, Block unusedBlock) {
        Ruby runtime = context.getRuntime();
        extensions = new ArrayList<IRubyObject>();
        if(args.length == 0) {
            return this;
        }
        ThreadContext tc = runtime.getCurrentContext();
        IRubyObject arg = OpenSSLImpl.to_der_if_possible(args[0]);
        ByteArrayInputStream bis = new ByteArrayInputStream(arg.convertToString().getBytes());
        CertificateFactory cf;
        
        RubyModule ossl = runtime.getModule("OpenSSL");
        RubyModule x509 = (RubyModule)ossl.getConstant("X509");
        IRubyObject x509Name = x509.getConstant("Name");
        RubyModule pkey = (RubyModule)ossl.getConstant("PKey");

        try {
            cf = CertificateFactory.getInstance("X.509",OpenSSLReal.PROVIDER);
            cert = (X509Certificate)cf.generateCertificate(bis);
        } catch (CertificateException ex) {
            throw newCertificateError(runtime, ex);
        }

        set_serial(RubyNumeric.str2inum(runtime,runtime.newString(cert.getSerialNumber().toString()),10));
        set_not_before(RubyTime.newTime(runtime,cert.getNotBefore().getTime()));
        set_not_after(RubyTime.newTime(runtime,cert.getNotAfter().getTime()));
        set_subject(x509Name.callMethod(tc,"new",RubyString.newString(runtime, cert.getSubjectX500Principal().getEncoded())));
        set_issuer(x509Name.callMethod(tc,"new",RubyString.newString(runtime, cert.getIssuerX500Principal().getEncoded())));

        String algorithm = cert.getPublicKey().getAlgorithm();
        if ("RSA".equalsIgnoreCase(algorithm)) {
            set_public_key(pkey.getConstant("RSA").callMethod(tc,"new",RubyString.newString(runtime, cert.getPublicKey().getEncoded())));
        } else if ("DSA".equalsIgnoreCase(algorithm)) {
            set_public_key(pkey.getConstant("DSA").callMethod(tc,"new",RubyString.newString(runtime, cert.getPublicKey().getEncoded())));
        } else {
            throw newCertificateError(runtime, "The algorithm " + algorithm + " is unsupported for public keys");
        }

        IRubyObject extFact = ((RubyClass)(x509.getConstant("ExtensionFactory"))).callMethod(tc,"new");
        extFact.callMethod(tc,"subject_certificate=",this);

        Set crit = cert.getCriticalExtensionOIDs();
        if(crit != null) {
            for(Iterator iter = crit.iterator();iter.hasNext();) {
                String critOid = (String)iter.next();
                byte[] value = cert.getExtensionValue(critOid);
                IRubyObject rValue = ASN1.decode(ossl.getConstant("ASN1"),RubyString.newString(runtime, value)).callMethod(tc,"value");
                if(critOid.equals("2.5.29.17")) {
                    add_extension(extFact.callMethod(tc,"create_ext", new IRubyObject[]{runtime.newString(critOid),runtime.newString(rValue.toString()),runtime.getTrue()}));
                } else {
                    add_extension(extFact.callMethod(tc,"create_ext", new IRubyObject[]{runtime.newString(critOid),runtime.newString(rValue.toString().substring(2)),runtime.getTrue()}));
                }
            }
        }

        Set ncrit = cert.getNonCriticalExtensionOIDs();
        if(ncrit != null) {
            for(Iterator iter = ncrit.iterator();iter.hasNext();) {
                String ncritOid = (String)iter.next();
                byte[] value = cert.getExtensionValue(ncritOid);
                IRubyObject rValue = ASN1.decode(ossl.getConstant("ASN1"),RubyString.newString(runtime, value)).callMethod(tc,"value");

                if(ncritOid.equals("2.5.29.17")) {
                    add_extension(extFact.callMethod(tc,"create_ext", new IRubyObject[]{runtime.newString(ncritOid),runtime.newString(rValue.toString()),runtime.getFalse()}));
                } else {
                    byte[] dest = new byte[value.length - 4];
                    System.arraycopy(value, 4, dest, 0, value.length - 4);
                    add_extension(extFact.callMethod(tc,"create_ext", new IRubyObject[]{runtime.newString(ncritOid),runtime.newString(new ByteList(dest, false)),runtime.getFalse()}));
                }
            }
        }
        changed = false;

        return this;
    }

    public static RaiseException newCertificateError(Ruby runtime, Exception ex) {
        return newCertificateError(runtime, ex.getMessage());
    }

    public static RaiseException newCertificateError(Ruby runtime, String message) {
        throw new RaiseException(runtime, (RubyClass)runtime.getClassFromPath("OpenSSL::X509::CertificateError"), message, true);
    }

    @JRubyMethod
    public IRubyObject initialize_copy(IRubyObject obj) {
        if(this == obj) {
            return this;
        }
        checkFrozen();
        return this;
    }

    @JRubyMethod
    public IRubyObject to_der() {
        try {
            return RubyString.newString(getRuntime(), cert.getEncoded());
        } catch (CertificateEncodingException ex) {
            throw newCertificateError(getRuntime(), ex);
        }
    }

    @JRubyMethod(name={"to_pem","to_s"})
    public IRubyObject to_pem() {
        try {
            StringWriter w = new StringWriter();
            PEMInputOutput.writeX509Certificate(w, getAuxCert());
            w.close();
            return getRuntime().newString(w.toString());
        } catch (IOException ex) {
            throw getRuntime().newIOErrorFromException(ex);
        }
    }

    @JRubyMethod
    public IRubyObject to_text() {
        return getRuntime().getNil();
    }

    @JRubyMethod
    public IRubyObject inspect() {
        return getRuntime().getNil();
    }

    @JRubyMethod
    public IRubyObject version() {
        return version;
    }

    @JRubyMethod(name="version=")
    public IRubyObject set_version(IRubyObject arg) {
        if(!arg.equals(this.version)) {
            changed = true;
        }
        this.version = arg;
        return arg;
    }

    @JRubyMethod
    public IRubyObject signature_algorithm() {
        return sig_alg;
    }

    @JRubyMethod
    public IRubyObject serial() {
        return serial;
    }

    @JRubyMethod(name="serial=")
    public IRubyObject set_serial(IRubyObject num) {
        if(!num.equals(this.serial)) {
            changed = true;
        }
        serial = num;
        generator.setSerialNumber(new BigInteger(serial.toString()));
        return num;
    }

    @JRubyMethod
    public IRubyObject subject() {
        return subject;
    }

    @JRubyMethod(name="subject=")
    public IRubyObject set_subject(IRubyObject arg) {
        if(!arg.equals(this.subject)) {
            changed = true;
        }
        subject = arg;
        generator.setSubjectDN(((X509Name)subject).getRealName());
        return arg;
    }

    @JRubyMethod
    public IRubyObject issuer() {
        return issuer;
    }

    @JRubyMethod(name="issuer=")
    public IRubyObject set_issuer(IRubyObject arg) {
        if(!arg.equals(this.issuer)) {
            changed = true;
        }
        issuer = arg;
        generator.setIssuerDN(((X509Name)issuer).getRealName());
        return arg;
    }

    @JRubyMethod
    public IRubyObject not_before() {
        return not_before;
    }

    @JRubyMethod(name="not_before=")
    public IRubyObject set_not_before(IRubyObject arg) {
        changed = true;
        not_before = arg.callMethod(getRuntime().getCurrentContext(),"getutc");
        ((RubyTime)not_before).setMicroseconds(0);
        generator.setNotBefore(((RubyTime)not_before).getJavaDate());
        return arg;
    }

    @JRubyMethod
    public IRubyObject not_after() {
        return not_after;
    }

    @JRubyMethod(name="not_after=")
    public IRubyObject set_not_after(IRubyObject arg) {
        changed = true;
        not_after = arg.callMethod(getRuntime().getCurrentContext(),"getutc");
        ((RubyTime)not_after).setMicroseconds(0);
        generator.setNotAfter(((RubyTime)not_after).getJavaDate());
        return arg;
    }

    @JRubyMethod
    public IRubyObject public_key() {
        return public_key;
    }

    @JRubyMethod(name="public_key=")
    public IRubyObject set_public_key(IRubyObject arg) {
        if(!arg.equals(this.public_key)) {
            changed = true;
        }
        public_key = arg;
        generator.setPublicKey(((PKey)public_key).getPublicKey());
        return arg;
    }

    @JRubyMethod
    public IRubyObject sign(ThreadContext context, final IRubyObject key, IRubyObject digest) {
        Ruby runtime = context.getRuntime();
        
        // Have to obey some artificial constraints of the OpenSSL implementation. Stupid.
        String keyAlg = ((PKey)key).getAlgorithm();
        String digAlg = ((Digest)digest).getAlgorithm();
        
        if(("DSA".equalsIgnoreCase(keyAlg) && "MD5".equalsIgnoreCase(digAlg)) || 
           ("RSA".equalsIgnoreCase(keyAlg) && "DSS1".equals(((Digest)digest).name().toString())) ||
           ("DSA".equalsIgnoreCase(keyAlg) && "SHA1".equals(((Digest)digest).name().toString()))) {
            throw new RaiseException(runtime, (RubyClass)(((RubyModule)(runtime.getModule("OpenSSL").getConstant("X509"))).getConstant("CertificateError")), null, true);
        }

        for(Iterator<IRubyObject> iter = extensions.iterator();iter.hasNext();) {
            X509Extensions.Extension ag = (X509Extensions.Extension)iter.next();
            try {
                byte[] bytes = ag.getRealValueBytes();
                generator.addExtension(ag.getRealOid(),ag.getRealCritical(),bytes);
            } catch (IOException ioe) {
                throw runtime.newIOErrorFromException(ioe);
            }
        }

        sig_alg = runtime.newString(digAlg);
        generator.setSignatureAlgorithm(digAlg + "WITH" + keyAlg);

        OpenSSLReal.doWithBCProvider(new Runnable() {
                public void run() {
                    try {
                        cert = generator.generate(((PKey)key).getPrivateKey(),"BC");
                    } catch(GeneralSecurityException e) {
                    }
                }
            });

        changed = false;
        return this;
    }

    @JRubyMethod
    public IRubyObject verify(IRubyObject key) {
        if(changed) {
            return getRuntime().getFalse();
        }
        try {
            cert.verify(((PKey)key).getPublicKey());
            return getRuntime().getTrue();
        } catch (CertificateException ce) {
            throw newCertificateError(getRuntime(), ce);
        } catch (NoSuchAlgorithmException nsae) {
            throw newCertificateError(getRuntime(), nsae);
        } catch (NoSuchProviderException nspe) {
            throw newCertificateError(getRuntime(), nspe);
        } catch (SignatureException se) {
            throw newCertificateError(getRuntime(), se);
        } catch(InvalidKeyException e) {
            return getRuntime().getFalse();
        }
    }

    @JRubyMethod
    public IRubyObject check_private_key(IRubyObject arg) {
        PKey key = (PKey)arg;
        PublicKey pkey = key.getPublicKey();
        PublicKey certPubKey = getAuxCert().getPublicKey();
        if (certPubKey.equals(pkey))
            return getRuntime().getTrue();
        return getRuntime().getFalse();
    }

    @JRubyMethod
    public IRubyObject extensions() {
        return getRuntime().newArray(extensions);
    }

    @SuppressWarnings("unchecked")
    @JRubyMethod(name="extensions=")
    public IRubyObject set_extensions(IRubyObject arg) {
        extensions = ((RubyArray)arg).getList();
        return arg;
    }

    @JRubyMethod
    public IRubyObject add_extension(IRubyObject arg) {
        changed = true;
        DERObjectIdentifier oid = ((X509Extensions.Extension)arg).getRealOid();
        if(oid.equals(new DERObjectIdentifier("2.5.29.17"))) {
            boolean one = true;
            for(Iterator iter = extensions.iterator();iter.hasNext();) {
                X509Extensions.Extension ag = (X509Extensions.Extension)iter.next();
                if(ag.getRealOid().equals(new DERObjectIdentifier("2.5.29.17"))) {
                    ASN1EncodableVector v1 = new ASN1EncodableVector();
                    
                    try {
                        GeneralName[] n1 = GeneralNames.getInstance(new ASN1InputStream(ag.getRealValueBytes()).readObject()).getNames();
                        GeneralName[] n2 = GeneralNames.getInstance(new ASN1InputStream(((X509Extensions.Extension)arg).getRealValueBytes()).readObject()).getNames();

                        for(int i=0;i<n1.length;i++) {
                            v1.add(n1[i]);
                        }
                        for(int i=0;i<n2.length;i++) {
                            v1.add(n2[i]);
                        }
                    } catch (IOException ex) {
                        throw getRuntime().newIOErrorFromException(ex);
                    }

                    ag.setRealValue(new String(ByteList.plain(new GeneralNames(new DERSequence(v1)).getDEREncoded())));
                    one = false;
                    break;
                }
            }
            if(one) {
                extensions.add(arg);
            }
        } else {
            extensions.add(arg);
        }
        return arg;
    }
}// X509Cert


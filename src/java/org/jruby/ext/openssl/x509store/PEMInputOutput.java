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
 * Copyright (C) 2006 Ola Bini <ola@ologix.com>
 * Copyright (C) 2007 William N Dortch <bill.dortch@gmail.com>
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
package org.jruby.ext.openssl.x509store;

import java.io.IOException;
import java.io.Writer;
import java.io.BufferedWriter;
import java.io.BufferedReader;
import java.io.Reader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import java.math.BigInteger;

import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.security.cert.X509CRL;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CRLException;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.DSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.InvalidParameterSpecException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.spec.DHParameterSpec;

import org.jruby.ext.openssl.OpenSSLReal;
import org.jruby.ext.openssl.PKCS10CertificationRequestExt;

import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1OutputStream;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERInteger;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERUTF8String;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERTaggedObject;
import org.bouncycastle.asn1.DERObjectIdentifier;
import org.bouncycastle.asn1.DERObject;
import org.bouncycastle.asn1.x509.DSAParameter;
import org.bouncycastle.asn1.cms.ContentInfo;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.pkcs.RSAPrivateKeyStructure;
import org.bouncycastle.asn1.x509.RSAPublicKeyStructure;
import org.bouncycastle.crypto.PBEParametersGenerator;
import org.bouncycastle.crypto.generators.OpenSSLPBEParametersGenerator;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.util.encoders.Base64;
import org.bouncycastle.util.encoders.Hex;
import org.bouncycastle.cms.CMSSignedData;

import java.security.Key;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.spec.DSAPrivateKeySpec;
import java.security.spec.DSAPublicKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.StringTokenizer;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Helper class to read and write PEM files correctly.
 *
 * @author <a href="mailto:ola.bini@ki.se">Ola Bini</a>
 */
public class PEMInputOutput {
    public static final String BEF = "-----";
    public static final String AFT = "-----";
    public static final String BEF_G = BEF+"BEGIN ";
    public static final String BEF_E = BEF+"END ";
    public static final String PEM_STRING_X509_OLD="X509 CERTIFICATE";
    public static final String PEM_STRING_X509="CERTIFICATE";
    public static final String PEM_STRING_X509_PAIR="CERTIFICATE PAIR";
    public static final String PEM_STRING_X509_TRUSTED="TRUSTED CERTIFICATE";
    public static final String PEM_STRING_X509_REQ_OLD="NEW CERTIFICATE REQUEST";
    public static final String PEM_STRING_X509_REQ="CERTIFICATE REQUEST";
    public static final String PEM_STRING_X509_CRL="X509 CRL";
    public static final String PEM_STRING_EVP_PKEY="ANY PRIVATE KEY";
    public static final String PEM_STRING_PUBLIC="PUBLIC KEY";
    public static final String PEM_STRING_RSA="RSA PRIVATE KEY";
    public static final String PEM_STRING_RSA_PUBLIC="RSA PUBLIC KEY";
    public static final String PEM_STRING_DSA="DSA PRIVATE KEY";
    public static final String PEM_STRING_DSA_PUBLIC="DSA PUBLIC KEY";
    public static final String PEM_STRING_PKCS7="PKCS7";
    public static final String PEM_STRING_PKCS8="ENCRYPTED PRIVATE KEY";
    public static final String PEM_STRING_PKCS8INF="PRIVATE KEY";
    public static final String PEM_STRING_DHPARAMS="DH PARAMETERS";
    public static final String PEM_STRING_SSL_SESSION="SSL SESSION PARAMETERS";
    public static final String PEM_STRING_DSAPARAMS="DSA PARAMETERS";
    public static final String PEM_STRING_ECDSA_PUBLIC="ECDSA PUBLIC KEY";
    public static final String PEM_STRING_ECPARAMETERS="EC PARAMETERS";
    public static final String PEM_STRING_ECPRIVATEKEY="EC PRIVATE KEY";
    
    private static final Pattern DH_PARAM_PATTERN = Pattern.compile(
            "(-----BEGIN DH PARAMETERS-----)(.*)(-----END DH PARAMETERS-----)",
            Pattern.MULTILINE);
    private static final int DH_PARAM_GROUP = 2; // the group above containing encoded params

    private static BufferedReader makeBuffered(Reader in) {
        if(in instanceof BufferedReader) {
            return (BufferedReader)in;
        }
        return new BufferedReader(in);
    }

    private static BufferedWriter makeBuffered(Writer out) {
        if(out instanceof BufferedWriter) {
            return (BufferedWriter)out;
        }
        return new BufferedWriter(out);
    }

    public static Object readPEM(Reader in,char[] f) throws IOException {
        BufferedReader _in = makeBuffered(in);
        String  line;
        while ((line = _in.readLine()) != null) {
            if(line.indexOf(BEF_G+PEM_STRING_PUBLIC) != -1) {
                try {
                    return readPublicKey(_in,BEF_E+PEM_STRING_PUBLIC);
                } catch (Exception e) {
                    throw new IOException("problem creating public key: " + e.toString());
                }
            } else if(line.indexOf(BEF_G+PEM_STRING_DSA) != -1) {
                try {
                    return readKeyPair(_in,f, "DSA", BEF_E+PEM_STRING_DSA);
                } catch (Exception e) {
                    throw new IOException("problem creating DSA private key: " + e.toString());
                }
            } else if(line.indexOf(BEF_G+PEM_STRING_RSA_PUBLIC) != -1) {
                try {
                    return readPublicKey(_in,BEF_E+PEM_STRING_RSA_PUBLIC);
                } catch (Exception e) {
                    throw new IOException("problem creating RSA public key: " + e.toString());
                }
            } else if(line.indexOf(BEF_G+PEM_STRING_X509_OLD) != -1) {
                try {
                    return readAuxCertificate(_in,BEF_E+PEM_STRING_X509_OLD);
                } catch (Exception e) {
                    throw new IOException("problem creating X509 Aux certificate: " + e.toString());
                }
            } else if(line.indexOf(BEF_G+PEM_STRING_X509) != -1) {
                try {
                    return readAuxCertificate(_in,BEF_E+PEM_STRING_X509);
                } catch (Exception e) {
                    throw new IOException("problem creating X509 Aux certificate: " + e.toString());
                }
            } else if(line.indexOf(BEF_G+PEM_STRING_X509_TRUSTED) != -1) {
                try {
                    return readAuxCertificate(_in,BEF_E+PEM_STRING_X509_TRUSTED);
                } catch (Exception e) {
                    throw new IOException("problem creating X509 Aux certificate: " + e.toString());
                }
            } else if(line.indexOf(BEF_G+PEM_STRING_X509_CRL) != -1) {
                try {
                    return readCRL(_in,BEF_E+PEM_STRING_X509_CRL);
                } catch (Exception e) {
                    throw new IOException("problem creating X509 CRL: " + e.toString());
                }
            } else if(line.indexOf(BEF_G+PEM_STRING_X509_REQ) != -1) {
                try {
                    return readCertificateRequest(_in,BEF_E+PEM_STRING_X509_REQ);
                } catch (Exception e) {
                    throw new IOException("problem creating X509 REQ: " + e.toString());
                }
            }
        }
        return null; 
    }

    public static DSAPublicKey readDSAPubKey(Reader in, char[] f) throws IOException {
        //        System.out.println("WARNING: read_DSA_PUBKEY");
        return null;
    }
    public static DSAPublicKey readDSAPublicKey(Reader in, char[] f) throws IOException {
        BufferedReader _in = makeBuffered(in);
        String  line;
        while ((line = _in.readLine()) != null) {
            if(line.indexOf(BEF_G+PEM_STRING_PUBLIC) != -1) {
                try {
                    return (DSAPublicKey)readPublicKey(_in,"DSA",BEF_E+PEM_STRING_PUBLIC);
                } catch (Exception e) {
                    throw new IOException("problem creating DSA public key: " + e.toString());
                }
            }
        }
        return null; 
    }
    public static KeyPair readDSAPrivateKey(Reader in, char[] f) throws IOException {
        BufferedReader _in = makeBuffered(in);
        String  line;
        while ((line = _in.readLine()) != null) {
            if(line.indexOf(BEF_G+PEM_STRING_DSA) != -1) {
                try {
                    return readKeyPair(_in,f, "DSA", BEF_E+PEM_STRING_DSA);
                } catch (Exception e) {
                    throw new IOException("problem creating DSA private key: " + e.toString());
                }
            }
        }
        return null; 
    }
    /** reads an RSA public key encoded in an SubjectPublicKeyInfo RSA structure. */
    public static RSAPublicKey readRSAPubKey(Reader in, char[] f) throws IOException {
        BufferedReader _in = makeBuffered(in);
        String  line;
        while ((line = _in.readLine()) != null) {
            if(line.indexOf(BEF_G+PEM_STRING_PUBLIC) != -1) {
                try {
                    return readRSAPublicKey(_in,BEF_E+PEM_STRING_PUBLIC);
                } catch (Exception e) {
                    throw new IOException("problem creating RSA public key: " + e.toString());
                }
            } else if(line.indexOf(BEF_G+PEM_STRING_RSA_PUBLIC) != -1) {
                try {
                    return readRSAPublicKey(_in,BEF_E+PEM_STRING_RSA_PUBLIC);
                } catch (Exception e) {
                    throw new IOException("problem creating RSA public key: " + e.toString());
                }
            }
        }
        return null; 
    }
    /** reads an RSA public key encoded in an PKCS#1 RSA structure. */
    public static RSAPublicKey readRSAPublicKey(Reader in, char[] f) throws IOException {
        BufferedReader _in = makeBuffered(in);
        String  line;
        while ((line = _in.readLine()) != null) {
            if(line.indexOf(BEF_G+PEM_STRING_PUBLIC) != -1) {
                try {
                    return (RSAPublicKey)readPublicKey(_in,"RSA",BEF_E+PEM_STRING_PUBLIC);
                } catch (Exception e) {
                    throw new IOException("problem creating RSA public key: " + e.toString());
                }
            } else if(line.indexOf(BEF_G+PEM_STRING_RSA_PUBLIC) != -1) {
                try {
                    return (RSAPublicKey)readPublicKey(_in,"RSA",BEF_E+PEM_STRING_RSA_PUBLIC);
                } catch (Exception e) {
                    throw new IOException("problem creating RSA public key: " + e.toString());
                }
            }
        }
        return null; 
    }
    public static KeyPair readRSAPrivateKey(Reader in, char[] f) throws IOException {
        BufferedReader _in = makeBuffered(in);
        String  line;
        while ((line = _in.readLine()) != null) {
            if(line.indexOf(BEF_G+PEM_STRING_RSA) != -1) {
                try {
                    return readKeyPair(_in,f, "RSA", BEF_E+PEM_STRING_RSA);
                } catch (Exception e) {
                    throw new IOException("problem creating RSA private key: " + e.toString());
                }
            }
        }
        return null;
    }
    public static CMSSignedData readPKCS7(Reader in, char[] f) throws IOException {
        BufferedReader _in = makeBuffered(in);
        String  line;
        while ((line = _in.readLine()) != null) {
            if(line.indexOf(BEF_G+PEM_STRING_PKCS7) != -1) {
                try {
                    return readPKCS7(_in,f, BEF_E+PEM_STRING_PKCS7);
                } catch (Exception e) {
                    throw new IOException("problem creating PKCS7: " + e.toString());
                }
            }
        }
        return null;
    }
    public static X509AuxCertificate readX509Certificate(Reader in, char[] f) throws IOException {
        BufferedReader _in = makeBuffered(in);
        String  line;
        while ((line = _in.readLine()) != null) {
            if(line.indexOf(BEF_G+PEM_STRING_X509_OLD) != -1) {
                try {
                    return new X509AuxCertificate(readCertificate(_in,BEF_E+PEM_STRING_X509_OLD));
                } catch (Exception e) {
                    throw new IOException("problem creating X509 certificate: " + e.toString());
                }
            } else if(line.indexOf(BEF_G+PEM_STRING_X509) != -1) {
                try {
                    return new X509AuxCertificate(readCertificate(_in,BEF_E+PEM_STRING_X509));
                } catch (Exception e) {
                    throw new IOException("problem creating X509 certificate: " + e.toString());
                }
            } else if(line.indexOf(BEF_G+PEM_STRING_X509_TRUSTED) != -1) {
                try {
                    return new X509AuxCertificate(readCertificate(_in,BEF_E+PEM_STRING_X509_TRUSTED));
                } catch (Exception e) {
                    throw new IOException("problem creating X509 certificate: " + e.toString());
                }
            }
        }
        return null;
    }
    public static X509AuxCertificate readX509Aux(Reader in, char[] f) throws IOException {
        BufferedReader _in = makeBuffered(in);
        String  line;
        while ((line = _in.readLine()) != null) {
            if(line.indexOf(BEF_G+PEM_STRING_X509_OLD) != -1) {
                try {
                    return readAuxCertificate(_in,BEF_E+PEM_STRING_X509_OLD);
                } catch (Exception e) {
                    throw new IOException("problem creating X509 Aux certificate: " + e.toString());
                }
            } else if(line.indexOf(BEF_G+PEM_STRING_X509) != -1) {
                try {
                    return readAuxCertificate(_in,BEF_E+PEM_STRING_X509);
                } catch (Exception e) {
                    throw new IOException("problem creating X509 Aux certificate: " + e.toString());
                }
            } else if(line.indexOf(BEF_G+PEM_STRING_X509_TRUSTED) != -1) {
                try {
                    return readAuxCertificate(_in,BEF_E+PEM_STRING_X509_TRUSTED);
                } catch (Exception e) {
                    throw new IOException("problem creating X509 Aux certificate: " + e.toString());
                }
            }
        }
        return null;
    }
    public static X509CRL readX509CRL(Reader in, char[] f) throws IOException {
        BufferedReader _in = makeBuffered(in);
        String  line;
        while ((line = _in.readLine()) != null) {
            if(line.indexOf(BEF_G+PEM_STRING_X509_CRL) != -1) {
                try {
                    return readCRL(_in,BEF_E+PEM_STRING_X509_CRL);
                } catch (Exception e) {
                    throw new IOException("problem creating X509 CRL: " + e.toString());
                }
            }
        }
        return null;
    }
    public static PKCS10CertificationRequestExt readX509Request(Reader in, char[] f) throws IOException {
        BufferedReader _in = makeBuffered(in);
        String  line;
        while ((line = _in.readLine()) != null) {
            if(line.indexOf(BEF_G+PEM_STRING_X509_REQ) != -1) {
                try {
                    return readCertificateRequest(_in,BEF_E+PEM_STRING_X509_REQ);
                } catch (Exception e) {
                    throw new IOException("problem creating X509 REQ: " + e.toString());
                }
            }
        }
        return null;
    }

    public static DHParameterSpec readDHParameters(Reader _in)
    throws IOException, InvalidParameterSpecException {
        BufferedReader in = makeBuffered(_in);
        String line;
        StringBuilder buf = new StringBuilder();
        while ((line = in.readLine()) != null) {
            if (line.indexOf(BEF_G + PEM_STRING_DHPARAMS) >= 0) {
                do {
                    buf.append(line.trim());
                } while (line.indexOf(BEF_E + PEM_STRING_DHPARAMS) < 0 &&
                        (line = in.readLine()) != null);
                break;
            }
        }
        Matcher m = DH_PARAM_PATTERN.matcher(buf.toString());
        if (m.find()) {
            try {
                byte[] decoded = Base64.decode(m.group(DH_PARAM_GROUP));
                ASN1InputStream aIn = new ASN1InputStream(new ByteArrayInputStream(decoded));
                ASN1Sequence seq = (ASN1Sequence)aIn.readObject();
                BigInteger p = ((DERInteger)seq.getObjectAt(0)).getValue();
                BigInteger g = ((DERInteger)seq.getObjectAt(1)).getValue();
                return new DHParameterSpec(p, g);
            } catch (Exception e) {}
        }
        // probably not exactly the intended use of this exception, but
        // close enough for internal throw/catch
        throw new InvalidParameterSpecException("invalid " + PEM_STRING_DHPARAMS);
    }

    public static void writeDSAPublicKey(Writer _out, DSAPublicKey obj) throws IOException {
        BufferedWriter out = makeBuffered(_out);
        byte[] encoding = obj.getEncoded();
        out.write(BEF_G + PEM_STRING_PUBLIC + AFT);
        out.newLine();
        writeEncoded(out,encoding);
        out.write(BEF_E + PEM_STRING_PUBLIC + AFT);
        out.newLine();
        out.flush();
    }
    /** writes an RSA public key encoded in an PKCS#1 RSA structure. */
    public static void writeRSAPublicKey(Writer _out, RSAPublicKey obj) throws IOException {
        BufferedWriter out = makeBuffered(_out);
        byte[] encoding = obj.getEncoded();
        out.write(BEF_G + PEM_STRING_PUBLIC + AFT);
        out.newLine();
        writeEncoded(out,encoding);
        out.write(BEF_E + PEM_STRING_PUBLIC + AFT);
        out.newLine();
        out.flush();
    }
    public static void writePKCS7(Writer _out, ContentInfo obj) throws IOException {
        BufferedWriter out = makeBuffered(_out);
        byte[] encoding = obj.getEncoded();
        out.write(BEF_G + PEM_STRING_PKCS7 + AFT);
        out.newLine();
        writeEncoded(out,encoding);
        out.write(BEF_E + PEM_STRING_PKCS7 + AFT);
        out.newLine();
        out.flush();
    }
    public static void writePKCS7(Writer _out, CMSSignedData obj) throws IOException {
        BufferedWriter out = makeBuffered(_out);
        byte[] encoding = obj.getEncoded();
        out.write(BEF_G + PEM_STRING_PKCS7 + AFT);
        out.newLine();
        writeEncoded(out,encoding);
        out.write(BEF_E + PEM_STRING_PKCS7 + AFT);
        out.newLine();
        out.flush();
    }
    public static void writeX509Certificate(Writer _out, X509Certificate obj) throws IOException {
        BufferedWriter out = makeBuffered(_out);
        try {
            byte[] encoding = obj.getEncoded();
            out.write(BEF_G + PEM_STRING_X509 + AFT);
            out.newLine();
            writeEncoded(out,encoding);
            out.write(BEF_E + PEM_STRING_X509 + AFT);
            out.newLine();
            out.flush();
        } catch(CertificateEncodingException e) {
            throw new IOException("problem with encoding object in write_X509");
        }
    }
    public static void writeX509Aux(Writer _out, X509AuxCertificate obj) throws IOException {
        BufferedWriter out = makeBuffered(_out);
        byte[] encoding = null;
        try {
            if(obj.getAux() == null) {
                encoding = obj.getEncoded();
            } else {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] ymp = obj.getEncoded();
                baos.write(ymp,0,ymp.length);
            
                X509Aux aux = obj.getAux();
                ASN1EncodableVector a1 = new ASN1EncodableVector();
                if(aux.trust.size()>0) {
                    ASN1EncodableVector a2 = new ASN1EncodableVector();
                    for(String trust : aux.trust) {
                        a2.add(new DERObjectIdentifier(trust));
                    }
                    a1.add(new DERSequence(a2));
                }
                if(aux.reject.size()>0) {
                    ASN1EncodableVector a2 = new ASN1EncodableVector();
                    for(String reject : aux.reject) {
                        a2.add(new DERObjectIdentifier(reject));
                    }
                    a1.add(new DERTaggedObject(0,new DERSequence(a2)));
                }
                if(aux.alias != null) {
                    a1.add(new DERUTF8String(aux.alias));
                }
                if(aux.keyid != null) {
                    a1.add(new DEROctetString(aux.keyid));
                }
                if(aux.other.size()>0) {
                    ASN1EncodableVector a2 = new ASN1EncodableVector();
                    for(DERObject other : aux.other) {
                        a2.add(other);
                    }
                    a1.add(new DERTaggedObject(1,new DERSequence(a2)));
                }
                ymp = new DERSequence(a1).getEncoded();
                baos.write(ymp,0,ymp.length);
                encoding = baos.toByteArray();
            }
        } catch(CertificateEncodingException e) {
            throw new IOException("problem with encoding object in write_X509_AUX");
        }
        out.write(BEF_G + PEM_STRING_X509_TRUSTED + AFT);
        out.newLine();
        writeEncoded(out,encoding);
        out.write(BEF_E + PEM_STRING_X509_TRUSTED + AFT);
        out.newLine();
        out.flush();
    }
    public static void writeX509CRL(Writer _out, X509CRL obj) throws IOException {
        BufferedWriter out = makeBuffered(_out);
        try {
            byte[] encoding = obj.getEncoded();
            out.write(BEF_G + PEM_STRING_X509_CRL + AFT);
            out.newLine();
            writeEncoded(out,encoding);
            out.write(BEF_E + PEM_STRING_X509_CRL + AFT);
            out.newLine();
            out.flush();
        } catch(CRLException e) {
            throw new IOException("problem with encoding object in write_X509_CRL");
        }
    }
    public static void writeX509Request(Writer _out, PKCS10CertificationRequestExt obj) throws IOException {
        BufferedWriter out = makeBuffered(_out);
        byte[] encoding = obj.getEncoded();
        out.write(BEF_G + PEM_STRING_X509_REQ + AFT);
        out.newLine();
        writeEncoded(out,encoding);
        out.write(BEF_E + PEM_STRING_X509_REQ + AFT);
        out.newLine();
        out.flush();
    }

    private static SecureRandom random;
    static {
        try {
            random = SecureRandom.getInstance("SHA1PRNG");
        } catch(Exception e) {
            random = null;
        }
    }

    public static void writeDSAPrivateKey(Writer _out, DSAPrivateKey obj, String algo, char[] f) throws IOException {
        BufferedWriter out = makeBuffered(_out);
        ByteArrayInputStream    bIn = new ByteArrayInputStream(obj.getEncoded());
        ASN1InputStream         aIn = new ASN1InputStream(bIn);
        PrivateKeyInfo          info = new PrivateKeyInfo((ASN1Sequence)aIn.readObject());
        ByteArrayOutputStream   bOut = new ByteArrayOutputStream();
        ASN1OutputStream        aOut = new ASN1OutputStream(bOut);

        DSAParameter        p = DSAParameter.getInstance(info.getAlgorithmId().getParameters());
        ASN1EncodableVector v = new ASN1EncodableVector();
                
        v.add(new DERInteger(0));
        v.add(new DERInteger(p.getP()));
        v.add(new DERInteger(p.getQ()));
        v.add(new DERInteger(p.getG()));
                
        BigInteger x = ((DSAPrivateKey)obj).getX();
        BigInteger y = p.getG().modPow(x, p.getP());
                
        v.add(new DERInteger(y));
        v.add(new DERInteger(x));
                
        aOut.writeObject(new DERSequence(v));
        byte[] encoding = bOut.toByteArray();

        if(algo != null && f != null) {
            byte[] salt = new byte[8];
            byte[] encData = null;
            random.nextBytes(salt);
            OpenSSLPBEParametersGenerator pGen = new OpenSSLPBEParametersGenerator();
            pGen.init(PBEParametersGenerator.PKCS5PasswordToBytes(f), salt);
            SecretKey secretKey = null;
            if(algo.equalsIgnoreCase("DESEDE")) {
                // generate key
                int keyLength = 24;
                secretKey = new SecretKeySpec(((KeyParameter)pGen.generateDerivedParameters(keyLength * 8)).getKey(), algo);
            } else {
                throw new IOException("unknown algorithm in write_DSAPrivateKey");
            }

            // cipher  
            try {
                Cipher  c = Cipher.getInstance("DESede/CBC/PKCS5Padding", OpenSSLReal.PROVIDER);
                c.init(Cipher.ENCRYPT_MODE, secretKey, new IvParameterSpec(salt));
                encData = c.doFinal(encoding);
            } catch (Exception e) {
                throw new IOException("exception using cipher: " + e.toString());
            }
       
            // write the data
            out.write(BEF_G + PEM_STRING_DSA + AFT);
            out.newLine();
            out.write("Proc-Type: 4,ENCRYPTED");
            out.newLine();
            out.write("DEK-Info: DES-EDE3-CBC,");
            writeHexEncoded(out,salt);
            out.newLine();
            out.newLine();
            writeEncoded(out,encData);
            out.write(BEF_E + PEM_STRING_DSA + AFT);   
            out.flush();
        } else {
            out.write(BEF_G + PEM_STRING_DSA + AFT);
            out.newLine();
            writeEncoded(out,encoding);
            out.write(BEF_E + PEM_STRING_DSA + AFT);
            out.newLine();
            out.flush();
        }
    }

    public static void writeRSAPrivateKey(Writer _out, RSAPrivateCrtKey obj, String algo, char[] f) throws IOException {
        BufferedWriter out = makeBuffered(_out);
        RSAPrivateKeyStructure keyStruct = new RSAPrivateKeyStructure(
                obj.getModulus(),
                obj.getPublicExponent(),
                obj.getPrivateExponent(),
                obj.getPrimeP(),
                obj.getPrimeQ(),
                obj.getPrimeExponentP(),
                obj.getPrimeExponentQ(),
                obj.getCrtCoefficient());
       
        // convert to bytearray
        ByteArrayOutputStream bOut = new ByteArrayOutputStream();
        ASN1OutputStream      aOut = new ASN1OutputStream(bOut);
            
        aOut.writeObject(keyStruct);
        aOut.close();
        
        byte[] encoding = bOut.toByteArray();

        if(algo != null && f != null) {
            byte[] salt = new byte[8];
            byte[] encData = null;
            random.nextBytes(salt);
            OpenSSLPBEParametersGenerator pGen = new OpenSSLPBEParametersGenerator();
            pGen.init(PBEParametersGenerator.PKCS5PasswordToBytes(f), salt);
            SecretKey secretKey = null;
            if(algo.equalsIgnoreCase("DESEDE")) {
                // generate key
                int keyLength = 24;
                secretKey = new SecretKeySpec(((KeyParameter)pGen.generateDerivedParameters(keyLength * 8)).getKey(), algo);
            } else {
                throw new IOException("unknown algorithm in write_DSAPrivateKey");
            }

            // cipher  
            try {
                Cipher  c = Cipher.getInstance("DESede/CBC/PKCS5Padding", OpenSSLReal.PROVIDER);
                c.init(Cipher.ENCRYPT_MODE, secretKey, new IvParameterSpec(salt));
                encData = c.doFinal(encoding);
            } catch (Exception e) {
                throw new IOException("exception using cipher: " + e.toString());
            }
       
            // write the data
            out.write(BEF_G + PEM_STRING_RSA + AFT);
            out.newLine();
            out.write("Proc-Type: 4,ENCRYPTED");
            out.newLine();
            out.write("DEK-Info: DES-EDE3-CBC,");
            writeHexEncoded(out,salt);
            out.newLine();
            out.newLine();
            writeEncoded(out,encData);
            out.write(BEF_E + PEM_STRING_RSA + AFT);   
            out.flush();
        } else {
            out.write(BEF_G + PEM_STRING_RSA + AFT);
            out.newLine();
            writeEncoded(out,encoding);
            out.write(BEF_E + PEM_STRING_RSA + AFT);
            out.newLine();
            out.flush();
        }
    }
    
    public static void writeDHParameters(Writer _out, DHParameterSpec params) throws IOException {
        BufferedWriter out = makeBuffered(_out);
        ByteArrayOutputStream bOut = new ByteArrayOutputStream();
        ASN1OutputStream aOut = new ASN1OutputStream(bOut);

        ASN1EncodableVector v = new ASN1EncodableVector();

        BigInteger value;
        if ((value = params.getP()) != null) {
            v.add(new DERInteger(value));
        }
        if ((value = params.getG()) != null) {
            v.add(new DERInteger(value));
        }

        aOut.writeObject(new DERSequence(v));
        byte[] encoding = bOut.toByteArray();

        out.write(BEF_G + PEM_STRING_DHPARAMS + AFT);
        out.newLine();
        writeEncoded(out,encoding);
        out.write(BEF_E + PEM_STRING_DHPARAMS + AFT);
        out.newLine();
        out.flush();
    }
    
    private static byte[] readBytes(BufferedReader in, String endMarker) throws IOException {
        String          line;
        StringBuffer    buf = new StringBuffer();
  
        while ((line = in.readLine()) != null) {
            if (line.indexOf(endMarker) != -1) {
                break;
            }
            buf.append(line.trim());
        }

        if (line == null) {
            throw new IOException(endMarker + " not found");
        }

        return Base64.decode(buf.toString());
    }

    /**
     * create the secret key needed for this object, fetching the password
     */
    private static SecretKey getKey(char[] k1, String  algorithm,int     keyLength, byte[]  salt) throws IOException {
        char[]      password = k1;

        if (password == null) {
            throw new IOException("Password is null, but a password is required");
        }
        
        OpenSSLPBEParametersGenerator   pGen = new OpenSSLPBEParametersGenerator();

        pGen.init(PBEParametersGenerator.PKCS5PasswordToBytes(password), salt);

        return new javax.crypto.spec.SecretKeySpec(((KeyParameter)pGen.generateDerivedParameters(keyLength * 8)).getKey(), algorithm);
    }

    private static RSAPublicKey readRSAPublicKey(BufferedReader in, String endMarker) throws IOException {
        ByteArrayInputStream bAIS = new ByteArrayInputStream(readBytes(in,endMarker));
        ASN1InputStream ais = new ASN1InputStream(bAIS);
        Object asnObject = ais.readObject();
        ASN1Sequence sequence = (ASN1Sequence) asnObject;
        RSAPublicKeyStructure rsaPubStructure = new RSAPublicKeyStructure(sequence);
        RSAPublicKeySpec keySpec = new RSAPublicKeySpec(
                    rsaPubStructure.getModulus(), 
                    rsaPubStructure.getPublicExponent());

        try {
            KeyFactory keyFact = KeyFactory.getInstance("RSA",OpenSSLReal.PROVIDER);      
            return (RSAPublicKey) keyFact.generatePublic(keySpec);
        } catch (NoSuchAlgorithmException e) { 
                // ignore
        } catch (InvalidKeySpecException e) { 
                // ignore
        }

        return  null;
    }

    private static PublicKey readPublicKey(BufferedReader in, String alg, String endMarker) throws IOException {
        KeySpec keySpec = new X509EncodedKeySpec(readBytes(in,endMarker));
        try {
            KeyFactory keyFact = KeyFactory.getInstance(alg,OpenSSLReal.PROVIDER);
            PublicKey pubKey = keyFact.generatePublic(keySpec);
            return pubKey;
        } catch (NoSuchAlgorithmException e) { 
            // ignore
        } catch (InvalidKeySpecException e) { 
            // ignore
        }
        return null;
    }

    private static PublicKey readPublicKey(BufferedReader in, String endMarker) throws IOException {
        KeySpec keySpec = new X509EncodedKeySpec(readBytes(in,endMarker));
        String[] algs = {"RSA","DSA"};
        for(int i=0;i<algs.length;i++) {
            try {
                KeyFactory keyFact = KeyFactory.getInstance(algs[i],OpenSSLReal.PROVIDER);
                PublicKey pubKey = keyFact.generatePublic(keySpec);
                return pubKey;
            } catch (NoSuchAlgorithmException e) { 
                // ignore
            } catch (InvalidKeySpecException e) { 
                // ignore
            }
        }
        return null;
    }

    /**
     * Read a Key Pair
     */
    private static KeyPair readKeyPair(BufferedReader _in, char[] passwd, String type,String endMarker)
        throws Exception {
        boolean         isEncrypted = false;
        String          line = null;
        String          dekInfo = null;
        StringBuffer    buf = new StringBuffer();

        while ((line = _in.readLine()) != null) {
            if (line.startsWith("Proc-Type: 4,ENCRYPTED")) {
                isEncrypted = true;
            } else if (line.startsWith("DEK-Info:")) {
                dekInfo = line.substring(10);
            } else if (line.indexOf(endMarker) != -1) {
                break;
            } else {
                buf.append(line.trim());
            }
        }
        byte[]  keyBytes = null;
        if (isEncrypted) {
            StringTokenizer tknz = new StringTokenizer(dekInfo, ",");
            String          encoding = tknz.nextToken();

            if (encoding.equals("DES-EDE3-CBC")) {
                String  alg = "DESede";
                byte[]  iv = Hex.decode(tknz.nextToken());
                Key     sKey = getKey(passwd,alg, 24, iv);
                Cipher  c = Cipher.getInstance("DESede/CBC/PKCS5Padding", OpenSSLReal.PROVIDER);
                c.init(Cipher.DECRYPT_MODE, sKey, new IvParameterSpec(iv));
                keyBytes = c.doFinal(Base64.decode(buf.toString()));
            } else if (encoding.equals("DES-CBC")) {
                String  alg = "DES";
                byte[]  iv = Hex.decode(tknz.nextToken());
                Key     sKey = getKey(passwd,alg, 8, iv);
                Cipher  c = Cipher.getInstance(
                                               "DES/CBC/PKCS5Padding", OpenSSLReal.PROVIDER);

                c.init(Cipher.DECRYPT_MODE, sKey, new IvParameterSpec(iv));
                keyBytes = c.doFinal(Base64.decode(buf.toString()));
            } else {
                throw new IOException("unknown encryption with private key");
            }
        } else {
            keyBytes = Base64.decode(buf.toString());
        }

        KeySpec                 pubSpec, privSpec;
        ByteArrayInputStream    bIn = new ByteArrayInputStream(keyBytes);
        ASN1InputStream         aIn = new ASN1InputStream(bIn);
        ASN1Sequence            seq = (ASN1Sequence)aIn.readObject();

        if (type.equals("RSA"))
        {
            //DERInteger              v = (DERInteger)seq.getObjectAt(0);
            DERInteger              mod = (DERInteger)seq.getObjectAt(1);
            DERInteger              pubExp = (DERInteger)seq.getObjectAt(2);
            DERInteger              privExp = (DERInteger)seq.getObjectAt(3);
            DERInteger              p1 = (DERInteger)seq.getObjectAt(4);
            DERInteger              p2 = (DERInteger)seq.getObjectAt(5);
            DERInteger              exp1 = (DERInteger)seq.getObjectAt(6);
            DERInteger              exp2 = (DERInteger)seq.getObjectAt(7);
            DERInteger              crtCoef = (DERInteger)seq.getObjectAt(8);

            pubSpec = new RSAPublicKeySpec(
                        mod.getValue(), pubExp.getValue());
            privSpec = new RSAPrivateCrtKeySpec(
                    mod.getValue(), pubExp.getValue(), privExp.getValue(),
                    p1.getValue(), p2.getValue(),
                    exp1.getValue(), exp2.getValue(),
                    crtCoef.getValue());
        }
        else    // "DSA"
        {
            //DERInteger              v = (DERInteger)seq.getObjectAt(0);
            DERInteger              p = (DERInteger)seq.getObjectAt(1);
            DERInteger              q = (DERInteger)seq.getObjectAt(2);
            DERInteger              g = (DERInteger)seq.getObjectAt(3);
            DERInteger              y = (DERInteger)seq.getObjectAt(4);
            DERInteger              x = (DERInteger)seq.getObjectAt(5);

            privSpec = new DSAPrivateKeySpec(
                        x.getValue(), p.getValue(),
                            q.getValue(), g.getValue());
            pubSpec = new DSAPublicKeySpec(
                        y.getValue(), p.getValue(),
                            q.getValue(), g.getValue());
        }

        KeyFactory          fact = KeyFactory.getInstance(type, OpenSSLReal.PROVIDER);

        return new KeyPair(
                    fact.generatePublic(pubSpec),
                    fact.generatePrivate(privSpec));
    }

    /**
     * Reads in a X509Certificate.
     *
     * @return the X509Certificate
     * @throws IOException if an I/O error occured
     */
    private static X509Certificate readCertificate(BufferedReader in,String  endMarker) throws IOException {
        String          line;
        StringBuffer    buf = new StringBuffer();
  
        while ((line = in.readLine()) != null)
        {
            if (line.indexOf(endMarker) != -1)
            {
                break;
            }
            buf.append(line.trim());
        }

        if (line == null)
        {
            throw new IOException(endMarker + " not found");
        }

        ByteArrayInputStream    bIn = new ByteArrayInputStream(
                                                Base64.decode(buf.toString()));

        try
        {
            CertificateFactory certFact
                    = CertificateFactory.getInstance("X.509", OpenSSLReal.PROVIDER);

            return (X509Certificate)certFact.generateCertificate(bIn);
        }
        catch (Exception e)
        {
            throw new IOException("problem parsing cert: " + e.toString());
        }
    }

    private static X509AuxCertificate readAuxCertificate(BufferedReader in,String  endMarker) throws IOException {
        String          line;
        StringBuffer    buf = new StringBuffer();
  
        while ((line = in.readLine()) != null) {
            if (line.indexOf(endMarker) != -1) {
                break;
            }
            buf.append(line.trim());
        }

        if (line == null) {
            throw new IOException(endMarker + " not found");
        }

        ASN1InputStream try1 = new ASN1InputStream(Base64.decode(buf.toString()));
        ByteArrayInputStream bIn = new ByteArrayInputStream(((DERObject)try1.readObject()).getEncoded());

        try {
            CertificateFactory certFact = CertificateFactory.getInstance("X.509", OpenSSLReal.PROVIDER);
            X509Certificate bCert = (X509Certificate)certFact.generateCertificate(bIn);
            DERSequence aux = (DERSequence)try1.readObject();
            X509Aux ax = null;
            if(aux != null) {
                ax = new X509Aux();
                int ix = 0;
                if(aux.size() > ix && aux.getObjectAt(ix) instanceof DERSequence) {
                    DERSequence trust = (DERSequence)aux.getObjectAt(ix++);
                    for(int i=0;i<trust.size();i++) {
                        ax.trust.add(((DERObjectIdentifier)trust.getObjectAt(i)).getId());
                    }
                }
                if(aux.size() > ix && aux.getObjectAt(ix) instanceof DERTaggedObject && ((DERTaggedObject)aux.getObjectAt(ix)).getTagNo() == 0) {
                    DERSequence reject = (DERSequence)((DERTaggedObject)aux.getObjectAt(ix++)).getObject();
                    for(int i=0;i<reject.size();i++) {
                        ax.reject.add(((DERObjectIdentifier)reject.getObjectAt(i)).getId());
                    }
                }
                if(aux.size()>ix && aux.getObjectAt(ix) instanceof DERUTF8String) {
                    ax.alias = ((DERUTF8String)aux.getObjectAt(ix++)).getString();
                }
                if(aux.size()>ix && aux.getObjectAt(ix) instanceof DEROctetString) {
                    ax.keyid = ((DEROctetString)aux.getObjectAt(ix++)).getOctets();
                }
                if(aux.size() > ix && aux.getObjectAt(ix) instanceof DERTaggedObject && ((DERTaggedObject)aux.getObjectAt(ix)).getTagNo() == 1) {
                    DERSequence other = (DERSequence)((DERTaggedObject)aux.getObjectAt(ix++)).getObject();
                    for(int i=0;i<other.size();i++) {
                        ax.other.add((DERObject)(other.getObjectAt(i)));
                    }
                }
            }
            return new X509AuxCertificate(bCert,ax);
        } catch (Exception e) {
            throw new IOException("problem parsing cert: " + e.toString());
        }
    }

    /**
     * Reads in a X509CRL.
     *
     * @return the X509CRL
     * @throws IOException if an I/O error occured
     */
    private static X509CRL readCRL(BufferedReader in, String  endMarker) throws IOException {
        String          line;
        StringBuffer    buf = new StringBuffer();
  
        while ((line = in.readLine()) != null)
        {
            if (line.indexOf(endMarker) != -1)
            {
                break;
            }
            buf.append(line.trim());
        }

        if (line == null)
        {
            throw new IOException(endMarker + " not found");
        }

        ByteArrayInputStream    bIn = new ByteArrayInputStream(
                                                Base64.decode(buf.toString()));

        try
        {
            CertificateFactory certFact
                    = CertificateFactory.getInstance("X.509", OpenSSLReal.PROVIDER);

            return (X509CRL)certFact.generateCRL(bIn);
        }
        catch (Exception e)
        {
            throw new IOException("problem parsing cert: " + e.toString());
        }
    }

    /**
     * Reads in a PKCS10 certification request.
     *
     * @return the certificate request.
     * @throws IOException if an I/O error occured
     */
    private static PKCS10CertificationRequestExt readCertificateRequest(BufferedReader in, String  endMarker) throws IOException {
        String          line;
        StringBuffer    buf = new StringBuffer();
  
        while ((line = in.readLine()) != null)
        {
            if (line.indexOf(endMarker) != -1)
            {
                break;
            }
            buf.append(line.trim());
        }

        if (line == null)
        {
            throw new IOException(endMarker + " not found");
        }

        try
        {
            return new PKCS10CertificationRequestExt(Base64.decode(buf.toString()));
        }
        catch (Exception e)
        {
            throw new IOException("problem parsing cert: " + e.toString());
        }
    }

    private static void writeHexEncoded(BufferedWriter out, byte[] bytes) throws IOException {
        bytes = Hex.encode(bytes);
        for (int i = 0; i != bytes.length; i++) {
            out.write((char)bytes[i]);
        }
    }

    private static void writeEncoded(BufferedWriter out, byte[] bytes) throws IOException {
        char[]  buf = new char[64];
        bytes = Base64.encode(bytes);
        for (int i = 0; i < bytes.length; i += buf.length) {
            int index = 0;
            
            while (index != buf.length) {
                if ((i + index) >= bytes.length) {
                    break;
                }
                buf[index] = (char)bytes[i + index];
                index++;
            }
            out.write(buf, 0, index);
            out.newLine();
        }
    }

    /**
     * Reads in a PKCS7 object. This returns a ContentInfo object suitable for use with the CMS
     * API.
     *
     * @return the X509Certificate
     * @throws IOException if an I/O error occured
     */
    private static CMSSignedData readPKCS7(BufferedReader in, char[] p, String  endMarker) throws IOException {
        String                                  line;
        StringBuffer                        buf = new StringBuffer();
        ByteArrayOutputStream    bOut = new ByteArrayOutputStream();
  
        while ((line = in.readLine()) != null) {
            if (line.indexOf(endMarker) != -1) {
                break;
            }
            line = line.trim();
            buf.append(line.trim());
            Base64.decode(buf.substring(0, (buf.length() / 4) * 4), bOut);
            buf.delete(0, (buf.length() / 4) * 4);
        }
        if (buf.length() != 0) {
            throw new RuntimeException("base64 data appears to be truncated");
        }
        if (line == null) {
            throw new IOException(endMarker + " not found");
        }
        ByteArrayInputStream    bIn = new ByteArrayInputStream(bOut.toByteArray());
        try {
            ASN1InputStream aIn = new ASN1InputStream(bIn);
            return new CMSSignedData(ContentInfo.getInstance(aIn.readObject()));
        } catch (Exception e) {
            throw new IOException("problem parsing PKCS7 object: " + e.toString());
        }
    }
}// PEM

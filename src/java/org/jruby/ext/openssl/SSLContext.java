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



import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.net.ssl.SSLEngine;
import org.jruby.Ruby;
import org.jruby.RubyArray;
import org.jruby.RubyClass;
import org.jruby.RubyModule;
import org.jruby.RubyNumeric;
import org.jruby.RubyObject;
import org.jruby.anno.JRubyMethod;
import org.jruby.common.IRubyWarnings.ID;
import org.jruby.ext.openssl.x509store.Store;
import org.jruby.ext.openssl.x509store.StoreContext;
import org.jruby.ext.openssl.x509store.X509AuxCertificate;
import org.jruby.runtime.Block;
import org.jruby.runtime.ObjectAllocator;
import org.jruby.runtime.builtin.IRubyObject;

/**
 * @author <a href="mailto:ola.bini@ki.se">Ola Bini</a>
 */
public class SSLContext extends RubyObject {
    private final static String[] ctx_attrs = {
    "cert", "key", "client_ca", "ca_file", "ca_path",
    "timeout", "verify_mode", "verify_depth",
    "verify_callback", "options", "cert_store", "extra_chain_cert",
    "client_cert_cb", "tmp_dh_callback", "session_id_context"};

    private static ObjectAllocator SSLCONTEXT_ALLOCATOR = new ObjectAllocator() {
        public IRubyObject allocate(Ruby runtime, RubyClass klass) {
            return new SSLContext(runtime, klass);
        }
    };
    
    public static void createSSLContext(Ruby runtime, RubyModule mSSL) {
        RubyClass cSSLContext = mSSL.defineClassUnder("SSLContext",runtime.getObject(),SSLCONTEXT_ALLOCATOR);
        for(int i=0;i<ctx_attrs.length;i++) {
            cSSLContext.attr_accessor(runtime.getCurrentContext(),new IRubyObject[]{runtime.newSymbol(ctx_attrs[i])});
        }

        cSSLContext.defineAnnotatedMethods(SSLContext.class);
    }

    public SSLContext(Ruby runtime, RubyClass type) {
        super(runtime,type);
    }

    private IRubyObject ciphers;
    private PKey t_key = null;
    private X509Cert t_cert = null;
    
    private java.security.cert.X509Certificate peer_cert;

    public void setPeer(java.security.cert.X509Certificate p) {
        this.peer_cert = p;
    }

    public java.security.cert.X509Certificate getPeer() {
        return this.peer_cert;
    }

    private void initFromCallback(IRubyObject cb) {
        IRubyObject out = cb.callMethod(getRuntime().getCurrentContext(),"call",this);
        t_cert = (X509Cert)(((RubyArray)out).getList().get(0));
        t_key = (PKey)(((RubyArray)out).getList().get(1));
    }

    public PKey getCallbackKey() {
        IRubyObject cb = callMethod(getRuntime().getCurrentContext(),"client_cert_cb");
        if(t_key == null && !cb.isNil()) {
            initFromCallback(cb);
        }
        return t_key;
    }

    public X509Cert getCallbackCert() {
        IRubyObject cb = callMethod(getRuntime().getCurrentContext(),"client_cert_cb");
        if(t_cert == null && !cb.isNil()) {
            initFromCallback(cb);
        }
        return t_cert;
    }

    @JRubyMethod(rest=true)
    public IRubyObject initialize(IRubyObject[] args) {
        ciphers = getRuntime().getNil();
        return this;
    }

    @JRubyMethod
    public IRubyObject ciphers() {
        System.err.println("fetching ciphers");
        return this.ciphers;
    }

    @JRubyMethod(name="ciphers=")
    public IRubyObject set_ciphers(IRubyObject val) {
        this.ciphers = val;
        return val;
    }

    String[] getCipherSuites(SSLEngine engine) {
        List<CipherStrings.Def> ciphs = null;
        if(this.ciphers.isNil()) {
            ciphs = CipherStrings.getMatchingCiphers(CipherStrings.SSL_DEFAULT_CIPHER_LIST, engine.getSupportedCipherSuites());
        } else if(this.ciphers instanceof RubyArray) {
            StringBuilder builder = new StringBuilder();
            String sep = "";
            for(Iterator iter = ((RubyArray)this.ciphers).getList().iterator();iter.hasNext();) {
                builder.append(sep).append(iter.next().toString());
                sep = ":";
            }
            ciphs = CipherStrings.getMatchingCiphers(builder.toString(), engine.getSupportedCipherSuites());
        } else {
            ciphs = CipherStrings.getMatchingCiphers(this.ciphers.toString(), engine.getSupportedCipherSuites());
        }
        String[] result = new String[ciphs.size()];
        for(int i=0;i<result.length;i++) {
            result[i] = ciphs.get(i).cipherSuite;
        }
        return result;
    }

    KM getKM() {
        return new KM(this);
    }

    TM getTM() {
        return new TM(this);
    }

    private static class KM extends javax.net.ssl.X509ExtendedKeyManager {
        private SSLContext ctt;
        public KM(SSLContext ctt) {
            super();
            this.ctt = ctt;
        }

        public String chooseEngineClientAlias(String[] keyType, java.security.Principal[] issuers, javax.net.ssl.SSLEngine engine) {
            PKey k = null;
            if(!ctt.callMethod(ctt.getRuntime().getCurrentContext(),"key").isNil()) {
                k = (PKey)ctt.callMethod(ctt.getRuntime().getCurrentContext(),"key");
            } else {
                k = ctt.getCallbackKey();
            }
            if(k == null) {
                return null;
            }
            for(int i=0;i<keyType.length;i++) {
                if(keyType[i].equalsIgnoreCase(k.getAlgorithm())) {
                    return keyType[i];
                }
            }
            return null;
        }

        public String chooseEngineServerAlias(String keyType, java.security.Principal[] issuers, javax.net.ssl.SSLEngine engine) {
            PKey k = null;
            if(!ctt.callMethod(ctt.getRuntime().getCurrentContext(),"key").isNil()) {
                k = (PKey)ctt.callMethod(ctt.getRuntime().getCurrentContext(),"key");
            } else {
                k = ctt.getCallbackKey();
            }
            if(k == null) {
                return null;
            }
            if(keyType.equalsIgnoreCase(k.getAlgorithm())) {
                return keyType;
            }
            return null;
        }
        public String 	chooseClientAlias(String[] keyType, java.security.Principal[] issuers, java.net.Socket socket) {
            return null;
        }
        public String 	chooseServerAlias(String keyType, java.security.Principal[] issuers, java.net.Socket socket) {
            return null;
        }
        public java.security.cert.X509Certificate[] 	getCertificateChain(String alias) {
            X509Cert c = null;
            if(!ctt.callMethod(ctt.getRuntime().getCurrentContext(),"cert").isNil()) {
                c = (X509Cert)ctt.callMethod(ctt.getRuntime().getCurrentContext(),"cert");
            } else {
                c = ctt.getCallbackCert();
            }
            if(c == null) {
                return null;
            }
            return new java.security.cert.X509Certificate[]{c.getAuxCert()};
        }
        public String[] 	getClientAliases(String keyType, java.security.Principal[] issuers) {
            return null;
        }
        public java.security.PrivateKey 	getPrivateKey(String alias) {
            PKey k = null;
            if(!ctt.callMethod(ctt.getRuntime().getCurrentContext(),"key").isNil()) {
                k = (PKey)ctt.callMethod(ctt.getRuntime().getCurrentContext(),"key");
            } else {
                k = ctt.getCallbackKey();
            }
            if(k == null) {
                return null;
            }
            return k.getPrivateKey();
        }
        public String[] 	getServerAliases(String keyType, java.security.Principal[] issuers) {
            return null;
        }
    }

    private static class TM implements javax.net.ssl.X509TrustManager {
        private SSLContext ctt;
        public TM(SSLContext ctt) {
            this.ctt = ctt;
        }

        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {
            if(chain != null && chain.length > 0) {
                ctt.setPeer(chain[0]);
            }
        }

        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
            if(ctt.callMethod(ctt.getRuntime().getCurrentContext(),"verify_mode").isNil()) {
                if(chain != null && chain.length > 0) {
                    ctt.setPeer(chain[0]);
                }
                return;
            }

            int verify_mode = RubyNumeric.fix2int(ctt.callMethod(ctt.getRuntime().getCurrentContext(),"verify_mode"));
            if(chain != null && chain.length > 0) {
                ctt.setPeer(chain[0]);
                if((verify_mode & 0x1) != 0) { // verify_peer
                    X509AuxCertificate x = StoreContext.ensureAux(chain[0]);
                    StoreContext ctx = new StoreContext();
                    IRubyObject str = ctt.callMethod(ctt.getRuntime().getCurrentContext(),"cert_store");
                    Store store = null;
                    if(!str.isNil()) {
                        store = ((X509Store)str).getStore();
                    }
                    if(ctx.init(store,x,StoreContext.ensureAux(chain)) == 0) {
                        throw new CertificateException("couldn't initialize store");
                    }

                    ctx.setDefault("ssl_client");

                    IRubyObject val = ctt.callMethod(ctt.getRuntime().getCurrentContext(),"ca_file");
                    String ca_file = val.isNil() ? null : val.convertToString().toString();
                    val = ctt.callMethod(ctt.getRuntime().getCurrentContext(),"ca_path");
                    String ca_path = val.isNil() ? null : val.convertToString().toString();

                    if(ca_file != null || ca_path != null) {
                        if(ctx.loadVerifyLocations(ca_file, ca_path) == 0) {
                            ctt.getRuntime().getWarnings().warn(ID.MISCELLANEOUS, "can't set verify locations");
                        }
                    }

                    try {
                        if(ctx.verifyCertificate() == 0) {
                            throw new CertificateException("certificate verify failed");
                        }
                    } catch(Exception e) {
                        throw new CertificateException("certificate verify failed");
                    }
                }
            } else {
                if((verify_mode & 0x2) != 0) { // fail if no peer cer
                    throw new CertificateException("no peer certificate");
                }
            }
        }

        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
            return new java.security.cert.X509Certificate[0];
        }
    }
}// SSLContext

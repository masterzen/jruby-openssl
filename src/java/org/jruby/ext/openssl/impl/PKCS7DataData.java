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
 * Copyright (C) 2008 Ola Bini <ola.bini@gmail.com>
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
package org.jruby.ext.openssl.impl;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.DEREncodable;
import org.bouncycastle.asn1.DEROctetString;

/**
 *
 * @author <a href="mailto:ola.bini@gmail.com">Ola Bini</a>
 */
public class PKCS7DataData extends PKCS7Data {
    /* NID_pkcs7_data */
    private ASN1OctetString data;

    public PKCS7DataData() {
        this(new DEROctetString(new byte[0]));
    }

    public PKCS7DataData(ASN1OctetString data) {
        this.data = data;
    }

    public int getType() {
        return ASN1Registry.NID_pkcs7_data;
    }

    public void setData(ASN1OctetString data) {
        this.data = data;
    }

    public ASN1OctetString getData() {
        return this.data;
    }

    public boolean isData() {
        return true;
    }

    @Override
    public String toString() {
        return "#<Data " + new String(data.getOctets()) + ">";
    }

    /**
     * Data ::= OCTET STRING
     */
    public static PKCS7DataData fromASN1(DEREncodable content) {
        if(content == null) {
            return new PKCS7DataData();
        }
        return new PKCS7DataData((ASN1OctetString)content);
    }

    public ASN1Encodable asASN1() {
        if(data == null) {
            return new DEROctetString(new byte[0]).toASN1Object();
        }
        return data.toASN1Object();
    }
}// PKCS7DataData

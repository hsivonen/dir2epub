/*
 * Copyright 2012-2013 Henri Sivonen
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dir2epub;

import org.w3c.dom.Element;

public class EncryptionXml extends Resource {

    private static final String NS = "http://www.w3.org/2001/04/xmlenc#";
    
    private boolean empty = true;
    
    public EncryptionXml(Reporter reporter) {
        super("META-INF/encryption.xml",
                "urn:oasis:names:tc:opendocument:xmlns:container",
                "encryption", reporter);
    }

    public void addObfuscatedResource(Resource res) {
        empty = false;
        // The schema referenced from EPUB 3.0 indicates that the descendants
        // of EncryptedData don't repeat, so I guess all this EncryptedData
        // boilerplate has to repeat.
        Element encData = this.dom.createElementNS(NS, "EncryptedData");
        Element encMethod = this.dom.createElementNS(NS, "EncryptionMethod");
        Element cipData = this.dom.createElementNS(NS, "CipherData");
        Element cipRef = this.dom.createElementNS(NS, "CipherReference");
        encMethod.setAttribute("Algorithm",
                "http://www.idpf.org/2008/embedding");
        cipRef.setAttribute("URI", res.getPath());
        cipData.appendChild(cipRef);
        encData.appendChild(encMethod);
        encData.appendChild(cipData);
        this.dom.getDocumentElement().appendChild(encData);
    }

    /**
     * @return the empty
     */
    public boolean isEmpty() {
        return empty;
    }
}

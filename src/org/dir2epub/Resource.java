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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.BreakIterator;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import nu.validator.htmlparser.common.XmlViolationPolicy;
import nu.validator.htmlparser.dom.HtmlDocumentBuilder;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class Resource {

    /**
     * Magic numbers. First byte must be unique!
     */
    private final int[][] MAGICS = {
            { 0x47, 0x49, 0x46, 0x38 }, // check next two bytes separately!
            { 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A }, 
            { 0xFF, 0xD8, 0xFF },
            { 0x00, 0x01, 0x00, 0x00 },
            { 0x4F, 0x54, 0x54, 0x4F },
            { 0x77, 0x4F, 0x46, 0x46 },
            { 0x4F, 0x67, 0x67, 0x53, 0x00 },
            { 0x49, 0x44, 0x33 },
            { 0x1A, 0x45, 0xDF, 0xA3 },
            { 0x25, 0x50, 0x44, 0x46, 0x2D },
            { 0x3C, 0x3F, 0x78, 0x6D, 0x6C },
            { 0xEF, 0xBB, 0xBF },
            { 0x1F, 0x8B, 0x08 },
            { 0x40, 0x63, 0x68, 0x61, 0x72, 0x73, 0x65, 0x74, 0x20, 0x22 },
            { 0xFE, 0xFF },
            // TODO zip
    };

    private final String[] TYPES = {
            "image/gif",
            "image/png",
            "image/jpeg",
            "application/vnd.ms-opentype",
            "application/vnd.ms-opentype",
            "application/font-woff",
            "application/ogg",
            "audio/mpeg",
            "video/webm",
            "application/pdf",
            "XML",
            "UTF-8",
            "GZIP",
            "text/css",
            "UTF-16",
    };
    
    private final int[][] UTF8_MAGICS = {
            { 0x3C, 0x3F, 0x78, 0x6D, 0x6C },
            { 0x40, 0x63, 0x68, 0x61, 0x72, 0x73, 0x65, 0x74, 0x20, 0x22 },
    };

    private final String[] UTF8_TYPES = {
            "XML",
            "text/css",
    };

    /**
     * Sorted.
     */
    private final String[] XML_EXTENSIONS = {
            "ncx",
            "pls",
            "smil",
            "svg",
            "svgz",
            "xhtml",
            "xht",
            "xml",
    };

    /**
     * Sorted.
     */
    private final String[] RELIABLY_SNIFFED_TYPES = {
            "application/font-woff",
            "application/ogg",
            "application/pdf",
            "application/vnd.ms-opentype",
            "application/xml",
            "audio/mp4",
            "audio/mpeg",
            "audio/ogg",
            "image/gif",
            "image/jpeg",
            "image/png",
            "video/mp4",
            "video/ogg",
            "video/webm",
    };

    /**
     * The zip-local name with the zip-local path.
     */
    private final String path;

    private final File file;

    protected StorageMode storageMode;

    protected final Reporter reporter;

    protected Document dom;

    public Resource(String path, File file, Reporter reporter) {
        super();
        this.path = path;
        this.file = file;
        this.storageMode = StorageMode.UNINITIALIZED;
        this.reporter = reporter;
        this.dom = null;
    }

    public Resource(String path, String ns, String localName, Reporter reporter) {
        super();
        this.path = path;
        this.file = null;
        this.storageMode = StorageMode.DEFLATE;
        this.reporter = reporter;
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        try {
            this.dom = dbf.newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
        this.dom.appendChild(this.dom.createElementNS(ns, localName));
    }

    public void writeToZip(ZipOutputStream zip) {
//        assert storageMode != StorageMode.UNINITIALIZED;
        try {
        ZipEntry entry = new ZipEntry(path);
        entry.setMethod(ZipEntry.DEFLATED);
        entry.setSize(file.length());
        byte[] buf = new byte[(int) file.length()];
        FileInputStream in = new FileInputStream(file);
        in.read(buf);
        zip.putNextEntry(entry);
        zip.write(buf);
        zip.closeEntry();
        } catch (Exception e) {
            // TODO: handle exception
            e.printStackTrace();
        }
    }

    /**
     * @return the path
     */
    public String getPath() {
        return path;
    }

    /**
     * @return the dom
     */
    public Document getDom() {
        if (this.dom == null) {
            try {
                assert this.file != null : "Getting DOM without backing file.";
                InputSource is = new InputSource();
                is.setSystemId(path);
                is.setByteStream(new FileInputStream(file));
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                dbf.setNamespaceAware(true);
                dbf.setCoalescing(true);
                DocumentBuilder builder;
                try {
                    builder = dbf.newDocumentBuilder();
                } catch (ParserConfigurationException e) {
                    throw reporter.fatal("Broken Java config.", e);
                }
                this.dom = builder.parse(this.file);
            } catch (FileNotFoundException e) {
                throw reporter.fatal("File went away during program execution: "
                        + path);
            } catch (SAXException e) {
                throw reporter.fatal("Ill-formed XML: " + path);
            } catch (IOException e) {
                throw reporter.fatal("Unable to read file: " + path);
            }
        }
        return this.dom;
    }

    /**
     * @return the file
     */
    public File getFile() {
        assert dom == null;
        return file;
    }

    @Override public int hashCode() {
        return path.hashCode();
    }

    @Override public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Resource other = (Resource) obj;
        return path.equals(other.path);
    }

    public String getFileName() {
        int slash = path.lastIndexOf('/');
        if (slash < 0) {
            return path;
        }
        return path.substring(slash + 1);
    }
    
    public String getFileNameWithoutExtension() {
        String name = getFileName();
        int dot = name.lastIndexOf('.');
        if (dot <= 0) { // leading dot is not extension dot!
            return name;
        }
        return name.substring(0, dot);        
    }
    
    public String getFileNameExtension() {
        String name = getFileName();
        int dot = name.lastIndexOf('.');
        if (dot <= 0) { // leading dot is not extension dot!
            return "";
        }
        return name.substring(dot + 1).toLowerCase();
    }
    
    public boolean isAutoIncluded() {
        return !isHidden() && !isLegalese();
    }
    
    public boolean isHidden() {
        if (file != null && file.isHidden()) {
            return true;
        }
        if (getFileName().startsWith(".")) {
            return true;
        }
        return false;
    }
    
    public boolean isLegalese() {
        String ext = getFileNameExtension();
        if ("txt".equals(ext) || "md".equals(ext)) {
            return true;
        }
        if (!"".equals(ext)) {
            return false;
        }
        String nameLowerCase = getFileName().toLowerCase();
        if (nameLowerCase.startsWith("readme")) {
            return true;
        }
        if (nameLowerCase.startsWith("license")) {
            return true;
        }
        if (nameLowerCase.startsWith("copying")) {
            return true;
        }
        if (nameLowerCase.startsWith("fontlog")) {
            return true;
        }
        if (nameLowerCase.startsWith("changelog")) {
            return true;
        }
        return false;
    }
    
    public String checkType(String declaredType) {
        try {
            FileInputStream in = new FileInputStream(file);
            String type = null;
            boolean gzip = false;
            boolean xml = false;
            boolean utf8 = false;
            boolean utf16 = false;
            int first = in.read();
            if (first == -1) {
                // empty file
                throw reporter.fatal("File " + path + " is empty.");
            }
            magicloop: for (int i = 0; i < MAGICS.length; i++) {
                int[] magic = MAGICS[i];
                if (magic[0] == first) {
                    for (int j = 1; j < magic.length; j++) {
                        int b = in.read();
                        if (b == -1) {
                            break magicloop;
                        }
                        if (b != magic[j]) {
                            break magicloop;
                        }
                    }
                    type = TYPES[i];
                    break magicloop;
                }
            }
            // The loop above matched only the first 4 bytes of the GIF magic
            gifcheck: if (type == "image/gif") {
                int b = in.read();
                if (!(b == 0x37 || b == 0x39)) {
                    type = null;
                    break gifcheck;
                }
                if (in.read() != 0x61) {
                    type = null;
                }
            }
            if ("UTF-8" == type) {
                utf8 = true;
                type = null;
                utf8magicloop: for (int i = 0; i < UTF8_MAGICS.length; i++) {
                    int[] magic = UTF8_MAGICS[i];
                    if (magic[0] == first) {
                        for (int j = 1; j < magic.length; j++) {
                            int b = in.read();
                            if (b == -1) {
                                break utf8magicloop;
                            }
                            if (b != magic[j]) {
                                break utf8magicloop;
                            }
                        }
                        type = UTF8_TYPES[i];
                        break utf8magicloop;
                    }
                }
            }
            if ("XML" == type) {
                xml = true;
                type = null;
            } else if ("GZIP" == type) {
                gzip = true;
                type = null;
            } else if ("UTF-16" == type) {
                utf8 = true;
                type = null;
            }
            in.close();
            in = null;
            if (type == null && !xml && !gzip && !utf8 && !utf16) {
                in = new FileInputStream(file);
                if (first == 0xFF) {
                    // Check MP3 without ID3 wrapper and UTF-16
                    in.read(); // Skip 0xFF
                    int b = in.read();
                    if (b != -1) {
                        if (b == 0xFE) {
                            utf16 = true;
                        } else if ((b & 0xFE) == 0xFA) {
                            type = "audio/mpeg";
                        }
                    }
                } else {
                    // XXX what if box size happens to match a short magic?
                    mp4check: {
                        long length = file.length();
                        if (length < 12L) {
                            break mp4check;
                        }
                        long boxSize = 0;
                        for (int i = 0; i < 4; i++) {
                            int b = in.read();
                            boxSize <<= 8;
                            boxSize |= ((long)b);
                        }
                        if (length < boxSize) {
                            break mp4check;
                        }
                        if (boxSize % 4L != 0) {
                            break mp4check;
                        }
                        if (in.read() != 0x66) { // f
                            break mp4check;
                        }
                        if (in.read() != 0x74) { // t
                            break mp4check;
                        }
                        if (in.read() != 0x79) { // y
                            break mp4check;
                        }
                        if (in.read() != 0x70) { // p
                            break mp4check;
                        }
                        // bytes read: 8
                        boolean foundMp4 = true;
                        if (in.read() != 0x6D) { // m
                            foundMp4 = false;
                        }
                        if (in.read() != 0x70) { // p
                            foundMp4 = false;
                        }
                        if (in.read() != 0x34) { // 4
                            foundMp4 = false;
                        }
                        if (foundMp4) {
                            // TODO audio?
                            type = "video/mp4";
                            break mp4check;
                        }
                        // bytes read: 11
                        for (int i = 0; i < 5; i++) {
                            if (in.read() == -1) {
                                break mp4check;
                            }
                        }
                        // bytes read: 16
                        long bytesRead = 16L;
                        while (bytesRead < boxSize) {
                            foundMp4 = true;
                            int b = in.read();
                            if (b == -1) {
                                break mp4check;
                            }
                            if (b != 0x6D) { // m
                                foundMp4 = false;
                            }
                            b = in.read();
                            if (b == -1) {
                                break mp4check;
                            }
                            if (b != 0x70) { // p
                                foundMp4 = false;
                            }
                            b = in.read();
                            if (b == -1) {
                                break mp4check;
                            }
                            if (b != 0x34) { // 4
                                foundMp4 = false;
                            }
                            if (foundMp4) {
                                // TODO audio?
                                type = "video/mp4";
                                break mp4check;
                            }
                            in.read();
                            bytesRead += 4L;
                        }
                    }
                }
                in.close();
                in = null;
            }

            String ext = getFileNameExtension();
            if (type == null) {
                // Now see if it parses as XML
                boolean hasXmlExt = Arrays.binarySearch(XML_EXTENSIONS, ext) >= 0;
                if (hasXmlExt) {
                    xml = true;
                }
                if (declaredType != null && declaredType.endsWith("+xml")) {
                    xml = true;
                }
                String parserError = null;
                InputSource is = new InputSource();
                is.setSystemId(path);
                if (gzip) {
                    is.setByteStream(new GZIPInputStream(new FileInputStream(
                            file)));
                } else {
                    is.setByteStream(new FileInputStream(file));
                }
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                dbf.setNamespaceAware(true);
                dbf.setCoalescing(true);
                DocumentBuilder builder;
                try {
                    builder = dbf.newDocumentBuilder();
                } catch (ParserConfigurationException e) {
                    throw reporter.fatal("Broken Java config.", e);
                }
                try {
                    this.dom = builder.parse(this.file);
                } catch (SAXException e) {
                    parserError = e.getMessage();
                }
                if ((this.dom == null || this.dom.getDocumentElement().getNamespaceURI() == null)
                        && (("application/xhtml+xml".equals(declaredType) && !hasXmlExt) || (declaredType == null && ("html".equals(ext) || "htm".equals(ext))))) {
                    // Let's assume the file is HTML.
                    HtmlDocumentBuilder hdb = new HtmlDocumentBuilder(
                            XmlViolationPolicy.ALTER_INFOSET);
                    // TODO UTF-8 fallback
                    is = new InputSource();
                    is.setSystemId(path);
                    if (gzip) {
                        // Probably useless but for consistency with XML
                        is.setByteStream(new GZIPInputStream(
                                new FileInputStream(file)));
                    } else {
                        is.setByteStream(new FileInputStream(file));
                    }
                    try {
                        this.dom = hdb.parse(is);
                        reporter.info("Converted " + path
                                + " from HTML to XHTML.");
                    } catch (SAXException ex) {
                    }
                }
                
                if (this.dom != null) {
                    String rootNS = this.dom.getDocumentElement().getNamespaceURI();
                    if ("http://www.w3.org/1999/xhtml".equals(rootNS)) {
                        type = "application/xhtml+xml";
                    } else if ("http://www.w3.org/2000/svg".equals(rootNS)) {
                        type = "image/svg+xml";
                    } else if ("http://www.daisy.org/z3986/2005/ncx/".equals(rootNS)) {
                        type = "application/x-dtbncx+xml";
                    } else if ("http://www.w3.org/ns/SMIL".equals(rootNS)) {
                        type = "application/smil+xml";
                    } else if ("http://www.w3.org/2005/01/pronunciation-lexicon".equals(rootNS)) {
                        type = "application/pls+xml";
                    } else if (declaredType != null && declaredType.endsWith("+xml")) {
                        type = declaredType;
                    } else {
                        type = "application/xml";
                    }
                } else if (xml) {
                    throw reporter.fatal("Purported XML file " + path + " is not well-formed: " + parserError);
                }
            }

            if (type == null && (("css".equals(ext) && declaredType == null) || "text/css".equals(declaredType))) {
                // TODO check UTF-8
                if (utf16) {
                    throw reporter.fatal("CSS file " + path + " is UTF-16-encoded. EPUB requires UTF-8 and this tool does not support conversion, yet.");
                }
                type = "text/css";
            }
            
            if (type == null && (("js".equals(ext) && declaredType == null) || "text/javascript".equals(declaredType) || "application/javascript".equals(declaredType))) {
                // TODO check UTF-8
                if (utf16) {
                    throw reporter.fatal("JavaScript file " + path + " is UTF-16-encoded. EPUB requires UTF-8 and this tool does not support conversion, yet.");
                }
                type = "text/javascript";
            }
            
            if (type != null) {
                if (declaredType != null) {
                    if (type.equals(declaredType)) {
                        return type;
                    }
                    if ("application/ogg".equals(type)
                            && ("video/ogg".equals(declaredType) || "audio/ogg".equals(declaredType))) {
                        return declaredType;
                    }
                    reporter.err("File " + path + " was declared to be of type " + declaredType + " but is actually of type " + type + ". Adjusting manifest accordingly.");
                }
                return type;
            }

            if (Arrays.binarySearch(RELIABLY_SNIFFED_TYPES, declaredType) >= 0) {
                throw reporter.fatal("File " + path + " was declared to be of type " + declaredType + ", but it is not of that type.");
            }

            return declaredType;
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return declaredType;
    }
}

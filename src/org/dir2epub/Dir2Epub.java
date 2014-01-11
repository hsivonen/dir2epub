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
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import nu.validator.htmlparser.impl.NCName;

import org.dir2epub.navigation.NavigationDocument;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public class Dir2Epub {

    private static final String OPS = "http://www.idpf.org/2007/ops";

    private static final String MATHML = "http://www.w3.org/1998/Math/MathML";

    private static final String SVG = "http://www.w3.org/2000/svg";

    private static final String XHTML = "http://www.w3.org/1999/xhtml";

    private static final String CNS = "urn:oasis:names:tc:opendocument:xmlns:container";

    private static final String ONS = "http://www.idpf.org/2007/opf";
    
    private static final String NCX = "http://www.daisy.org/z3986/2005/ncx/";
    
    private static final String DC = "http://purl.org/dc/elements/1.1/";

    private static final byte[] MIMETYPE = { 'a', 'p', 'p', 'l', 'i', 'c', 'a',
        't', 'i', 'o', 'n', '/', 'e', 'p', 'u', 'b', '+', 'z', 'i', 'p' };

    /**
     * Sorted.
     */
//    private final String[] CORE_MEDIA_TYPES = {
//            "application/font-woff",
//            "application/pls+xml",
//            "application/smil+xml",
//            "application/vnd.ms-opentype",
//            "application/x-dtbncx+xml",
//            "application/xhtml+xml",
//            "audio/mp4",
//            "audio/mpeg",
//            "image/gif",
//            "image/jpeg",
//            "image/png",
//            "image/svg+xml",
//            "text/css",
//            "text/javascript"
//    };
    
    private final File epubFile;
    
    private final Reporter reporter;
    
    public Dir2Epub(File epubFile, Reporter reporter) {
        super();
        this.epubFile = epubFile;
        this.reporter = reporter;
    }

    private Map<String, Resource> inputResources;
    
    private Map<String, Resource> outputResources = new LinkedHashMap<String, Resource>();
    
    private Resource container;

    private Resource opf;
    
    private Resource nav;
    
    private Resource ncx;

    private boolean refreshExistingManifest = false;
    
    private boolean removeScripting = false;

    private NavigationDocument navigation = null;

    private HashSet<Resource> documents = new HashSet<Resource>();
    
    private Element findUniqueChild(Element parent, String ns, String localName, String notFoundErr, String multipleErr, Reporter reporter) {
        Element child = null;
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (ns.equals(n.getNamespaceURI()) && localName.equals(n.getLocalName())) {
                if (child != null) {
                    throw reporter.fatal(multipleErr);
                }
                child = (Element) n;
            }
        }
        if (child == null && notFoundErr != null) {
            throw reporter.fatal(notFoundErr);
        }
        return child;
    }
    
    
    public void build(File directory) {
        if (!directory.isDirectory()) {
            throw reporter.fatal("The input directory is not a directory.");
        }
        ResourceMapBuilder rmb = new ResourceMapBuilder(reporter);
        inputResources = rmb.buildMap(directory);
        
        checkMimetype();
        if (inputResources.containsKey("META-INF/rights.xml")) {
            throw reporter.fatal("META-INF/rights.xml found. Cannot continue. This tool does not support any DRM scheme.");            
        }
        if (inputResources.containsKey("META-INF/encryption.xml")) {
            throw reporter.fatal("META-INF/encryption.xml found. Will not continue. This tool generates encryption.xml itself and only for font obfuscation.");
        }
        if ((container = inputResources.remove("META-INF/container.xml")) != null) {
            Document containerDoc = container.getDom();
            Element root = containerDoc.getDocumentElement();
            if (!(CNS.equals(root.getNamespaceURI()) && "container".equals(root.getLocalName()))) {
                throw reporter.fatal("Bogus root element in META-INF/container.xml.");
            }
            Element rootfiles = findUniqueChild(root, CNS, "rootfiles",
                    "No <rootfiles> element in META-INF/container.xml.",
                    "Too many <rootfiles> elements in META-INF/container.xml.",
                    reporter);
            Element rootfile = findUniqueChild(rootfiles, CNS, "rootfile", "No root file declared in META-INF/container.xml.", "Multiple root files declared in META-INF/container.xml. This tool supports only one.", reporter);
            if (!"application/oebps-package+xml".equals(rootfile.getAttribute("media-type"))) {
                throw reporter.fatal("Bad root file media type in META-INF/container.xml.");                
            }
            String rootPath = rootfile.getAttribute("full-path");
            opf = inputResources.get(rootPath);
            if (opf == null) {
                createOpf(rootPath);
            }
        } else {
            for (Entry<String, Resource> entry : inputResources.entrySet()) {
                Resource res = entry.getValue();
                if ("opf".equals(res.getFileNameExtension())) {
                    if (opf == null) {
                        opf = res;
                    } else {
                        throw reporter.fatal("Multiple OPF files.");
                    }
                }
            }
            if (opf == null) {
                createOpf("content.opf");
            }
            createContainerXml();
        }
        inputResources.remove(opf.getPath());
        outputResources.put(container.getPath(), container);
        outputResources.put(opf.getPath(), opf);
        
        ensureManifest();
        
        checkItems();
        
        for (Resource res : documents) {
            res.initNext(outputResources);
        }
        
        ensureSpine();
                
        if (navigation == null || !navigation.hasNonEmptyToc()) {
            // TODO generate navigation
        }
        
        if (ncx == null) {
            if (outputResources.containsKey("toc.ncx")) {
                // Can this even happen at this point anymore?
                throw reporter.fatal("toc.ncx exists but is not an NCX file.");
            }
            ncx = new Resource("toc.ncx", NCX, "ncx", reporter);
            outputResources.put(ncx.getPath(), ncx);
        }
        
        if (nav == null) {
            nav = new Resource(generateTocPath(), XHTML, "html", reporter);
            Document dom = nav.getDom();
            Element root = dom.getDocumentElement();
            // TODO figure out some text for title
            Element title = dom.createElementNS(XHTML, "title");
            Element head = dom.createElementNS(XHTML, "head");
            Element body = dom.createElementNS(XHTML, "body");
            head.appendChild(title);
            root.appendChild(head);
            root.appendChild(body);
        }
        
        // Update NCX
        
        Document dom = ncx.getDom();
        Element root = dom.getDocumentElement();
        
        Element navList = findUniqueChild(root, NCX, "navList", null, "Multiple <navList> elements in NCX", reporter);
        if (navList == null && navigation.hasLandmarks()) {
            navList = dom.createElementNS(NCX, "navList");
            root.appendChild(navList);
        }
        
        Element pageList = findUniqueChild(root, NCX, "pageList", null, "Multiple <pageList> elements in NCX", reporter);
        if (pageList == null && navigation.hasPageList()) {
            pageList = dom.createElementNS(NCX, "pageList");
            root.insertBefore(pageList, navList);
        }

        Element navMap = findUniqueChild(root, NCX, "navMap", null, "Multiple <navMap> elements in NCX", reporter);
        if (navMap == null && navigation.hasToc()) {
            navMap = dom.createElementNS(NCX, "navMap");
            root.insertBefore(navMap, pageList);
        }
        
        if (navMap != null) {
            navigation.generateNcx(navMap, "navPoint");
        }
        if (pageList != null) {
            navigation.generateNcx(pageList, "pageTarget");
        }
        if (navList != null) {
            navigation.generateNcx(navList, "navTarget");
        }
        
        // Update TOC HTML
        
        dom = nav.getDom();
        root = dom.getDocumentElement();
        
        Element toc = null;
        Element pages = null;
        Element landmarks = null;
        
        Node current = root;
        Node next;
        domwalk: for (;;) {
            if (current.getNodeType() == Node.ELEMENT_NODE) {
                Element elt = (Element) current;

                if (XHTML.equals(elt.getNamespaceURI()) && "nav".equals(elt.getLocalName()) && elt.hasAttributeNS(OPS, "type")) {
                    String type = elt.getAttributeNS(OPS, "type");
                    if ("toc".equals(type)) {
                        toc = elt;
                    } else if ("page-list".equals(type)) {
                        pages = elt;
                    } else if ("landmarks".equals(type)) {
                        landmarks = elt;
                    }
                }
                
                if ((next = current.getFirstChild()) != null) {
                    current = next;
                    continue;
                }
            }
            for (;;) {
                if ((next = current.getNextSibling()) != null) {
                    current = next;
                    break;
                }
                current = current.getParentNode();
                if (current == root)
                    break domwalk;
            }
        }

        Node body = root.getLastChild();
        while (body != null && !(XHTML.equals(body.getNamespaceURI()) && "body".equals(body.getLocalName()))) {
            body = body.getPreviousSibling();
        }
        if (body == null) {
            body = dom.createElementNS(XHTML, "body");
            root.appendChild(body);
        }
        
        if (toc == null && navigation.hasToc()) {
            toc = dom.createElementNS(XHTML, "nav");
            toc.setAttributeNS(OPS, "type", "toc");
            body.appendChild(toc);
        }
        
        if (pages == null && navigation.hasPageList()) {
            pages = dom.createElementNS(XHTML, "nav");
            pages.setAttributeNS(OPS, "type", "page-list");
            body.appendChild(pages);
        }
        
        if (landmarks == null && navigation.hasLandmarks()) {
            landmarks = dom.createElementNS(XHTML, "nav");
            landmarks.setAttributeNS(OPS, "type", "landmarks");
            body.appendChild(landmarks);
        }

        if (toc != null) {
            navigation.generateHtmlToc(toc);
        }
        if (pages != null) {
            navigation.generateHtmlPageList(pages);
        }
        if (landmarks != null) {
            navigation.generateHtmlLandmarks(landmarks);
        }        
        
        // Ensure OPF metadata exists
        
        dom = opf.getDom();
        root = dom.getDocumentElement();
        Element metadata = findUniqueChild(root, ONS, "metadata", null, "Multiple <metadata> elements in " + opf.getPath() + ".", reporter);
        if (metadata != null) {
            metadata = dom.createElementNS(ONS, "metadata");
            root.insertBefore(metadata, root.getFirstChild());
        }
        
        // TODO hoist children out of dc-metadata and x-metadata
        
        // Figure out title, author, language and book id
        // EPUB makes this way too complicated!
        
        String title = null;
        String author = null;
        String language = null;
        String guid = null;
        
        // id
        
        String idId = root.getAttributeNS(null, "unique-identifier");
        Element idNode = getElementById(root, idId);
        
        if (idNode != null && idNode.getParentNode() != metadata) {
            idNode = null;
            idId = null;
            reporter.err("The unique-identifier attribute on OPF root points to a node that is not a child of <metadata>.");
        }
        
        if (idNode != null) {
            if (DC.equals(idNode.getNamespaceURI()) && "identifier".equals(idNode.getLocalName())) {
                guid = trimWhiteSpace(idNode.getTextContent());
                if (guid.length() == 0) {
                    guid = null;
                }
            }
        }
        
        // language
        
        for (Node n = metadata.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (DC.equals(n.getNamespaceURI()) && "language".equals(n.getLocalName())) {
               String lang = checkLang(n.getTextContent());
               // TODO
            }
        }
        
        // TODO Generate NCX (check existing docTitle, version, etc.)
        
        // TODO Generate guide
        
        // TODO Generate metadata

        // TODO add silly required version attributes to OPF and NCX
        
        EpubWriter w = null;
        try {
            w = new EpubWriter(new FileOutputStream(epubFile), reporter);
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        for (Resource res : outputResources.values()) {
            w.write(res);
        }
        w.close();
    }
    
    private String checkLang(String lang) {
        lang = toAsciiLowerCase(trimWhiteSpace(lang));
        // TODO
        return null;
    }

    public String toAsciiLowerCase(String str) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                c += 0x20;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private String trimWhiteSpace(String str) {
        int firstNonWhiteSpace = 0;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            switch (c) {
                case ' ':
                case '\t':
                case '\r':
                case '\n':
                    continue;
                default:
                    firstNonWhiteSpace = i;
            }
        }
        int lastWhiteSpace = str.length();
        for (int i = str.length() - 1; i >= 0; i--) {
            char c = str.charAt(i);
            switch (c) {
                case ' ':
                case '\t':
                case '\r':
                case '\n':
                    continue;
                default:
                    lastWhiteSpace = i + 1;
            }
        }       
        return str.substring(firstNonWhiteSpace, lastWhiteSpace);
    }


    private String generateTocPath() {
        String stem = "toc";
        String candidate = stem + ".xhtml";
        while (outputResources.containsKey(candidate)) {
            stem += "_";
            candidate = stem + ".xhtml";
        }
        return candidate;
    }


    private void ensureSpine() {
        Document dom = opf.getDom();
        Element root = dom.getDocumentElement();
        assert ONS.equals(root.getNamespaceURI()) && "package".equals(root.getLocalName()) : "Checked OPF root already";
        Element manifest = findUniqueChild(root, ONS, "manifest", null, "Multiple <manifest> elements in " + opf.getPath() + ".", reporter);
        assert manifest != null : "Ensured manifest already";
        
        Element spine = findUniqueChild(root, ONS, "spine", null,
                "Multiple <spine> elements in " + opf.getPath() + ".", reporter);
        if (spine == null) {
            spine = dom.createElementNS(ONS, "spine");
            root.insertBefore(spine, manifest.getNextSibling());
        }
        
        checktoc: if (spine.hasAttributeNS(null, "toc")) {
            String id = spine.getAttributeNS(null, "toc");
            Element toc = getElementById(dom, id);
            if (toc == null) {
                reporter.warn("The toc attribute on the <spine> element referred to non-existing id " + id + ".");
                spine.removeAttributeNS(null, "toc");
                break checktoc;
            }
            if (!isItem(manifest, toc)) {
                reporter.warn("The toc attribute on the <spine> element did not refer to an <item> child of <manifest>.");
                spine.removeAttributeNS(null, "toc");
                break checktoc;
            }
            if (!"application/x-dtbncx+xml".equals(toc.getAttributeNS(null, "media-type"))) {
                reporter.warn("The toc attribute on the <spine> referred to a non-NCX <item>.");
                spine.removeAttributeNS(null, "toc");
                break checktoc;                
            }
            ncx = outputResources.get(manifestUrlToPath(toc.getAttributeNS(null, "href")));
            assert ncx != null : "Bogus path on the NCX item.";
        }
     
        NavigationDocument ncxNavigation = null;
        NavigationDocument htmlNavigation = null;
        if (ncx != null) {
            ncxNavigation = new NavigationDocument(ncx, reporter);
        }
        if (nav != null) {
            htmlNavigation = new NavigationDocument(nav, reporter);
        }
        if (ncxNavigation == null) {
            navigation = htmlNavigation;
        } else if (htmlNavigation == null) {
            navigation = ncxNavigation;
        } else {
            navigation = new NavigationDocument(ncxNavigation, htmlNavigation, reporter);
        }
        
        LinkedHashSet<Resource> tocDocs;
        if (navigation != null && navigation.hasToc()) {
            tocDocs = navigation.listDocs(outputResources, reporter);
        } else {
            tocDocs = new LinkedHashSet<Resource>();
        }
        
        LinkedHashSet<Resource> spineDocs = new LinkedHashSet<Resource>();
        Node node = spine.getFirstChild();
        while (node != null) {
            Node remove = null;
            if (ONS.equals(node.getNamespaceURI()) && "itemref".equals(node.getLocalName())) {
                Element itemref = (Element) node;
                if (itemref.hasAttributeNS(null, "idref")) {
                    Resource resource = getResourceById(itemref.getAttributeNS(null, "idref"));
                    if (resource == null) {
                        reporter.err("<itemref> element did not refer to an <item>. Removing element.");
                        remove = node;                        
                    } else {
                        if (spineDocs.contains(resource)) {
                            reporter.err("<itemref> element referred to an <item> that was already referred to by an earlier <itemref>. Removing element.");
                            remove = node;                            
                        }
                        spineDocs.add(resource);
                        documents.remove(resource);
                    }
                } else {
                    reporter.err("<itemref> element had no idref attribute. Removing element.");
                    remove = node;
                }
            }
            node = node.getNextSibling();
            if (remove != null) {
                spine.removeChild(remove);
            }
        }
        
        if (!spineDocs.isEmpty() && !documents.isEmpty()) {
                reporter.warn("<spine> was not empty but did not list all (X)HTML documents from the <manifest>.");
        }
        documents.removeAll(tocDocs);
        if (spineDocs.isEmpty()) {
            spineDocs = tocDocs;
        } else if (!tocDocs.isEmpty()) {
            LinkedList<Resource> spineList = new LinkedList<Resource>(spineDocs);
            LinkedList<Resource> tocList = new LinkedList<Resource>(tocDocs);
            spineDocs = new LinkedHashSet<Resource>();
            boolean consumeSpine = !tocList.contains(spineList.getFirst());
            while (!spineList.isEmpty() && !tocList.isEmpty()) {
                Resource until = consumeSpine ? tocList.getFirst() : spineList.getFirst();
                while (consumeSpine ? !spineList.isEmpty() : !tocList.isEmpty()) {
                    Resource res = consumeSpine ? spineList.removeFirst() : tocList.removeFirst();
                    spineDocs.add(res);
                    if (res == until) {
                        consumeSpine = !consumeSpine;
                        break;
                    }
                }
            }
            spineDocs.addAll(spineList);
            spineDocs.addAll(tocList);
        }
        if (!documents.isEmpty()) {
            LinkedList<Resource> spineList = new LinkedList<Resource>(spineDocs);
            if (spineList.isEmpty()) {
                // See if we have only one doc with null getNext. If we do,
                // let's put that into the spine list and let a later loop 
                // fill in the rest (assuming that the next links aren't bogus).
                Resource nullNext = null;
                for (Resource res : documents) {
                    if (res.getNext() == null) {
                        if (nullNext == null || toAsciiLowerCase(nullNext.getFileName()).startsWith("footno")) {
                            // LaTeX2HTML calls its footnote doc footnode.html
                            nullNext = res;
                        } else {
                            nullNext = null;
                            break;
                        }
                    }
                }
                if (nullNext != null) {
                    spineList.add(nullNext);
                }
            } else {
                // This iteration is by index, because iterators don't support
                // this kind of concurrent modification.
                for (int i = 0; i < spineList.size() && !documents.isEmpty(); i++) {
                    Resource res = spineList.get(i);
                    Resource next = res.getNext();
                    if (documents.remove(next)) {
                        spineList.add(i + 1, next);
                    }
                }
                
            }
            if (!documents.isEmpty()) {
                // The remaining docs are not reachable from the spine via
                // rel=next
                outer: for (;;) {
                    for (Resource res : documents) {
                        int index = spineList.indexOf(res.getNext());
                        if (index >= 0) {
                            spineList.add(index, res);;
                            continue outer;
                        }
                    }
                    break;
                }
            }
            if (!documents.isEmpty()) {
                // Next links alone were not enough to define the order :-(
                // * Separate potential cover
                // * Separate toc
                // * Separate likely bibliography, footnotes, etc.
                // * Sort the rest in the middle

                // TODO Fit remaining docs into spine
//                http://www.microformats.org/wiki/book-brainstorming
            }
            spineDocs = new LinkedHashSet<Resource>(spineList);
        } else if (!spineDocs.isEmpty()) {
            throw reporter.fatal("Empty spine and no documents available to add to the spine.");
        }
        
        // Insert the spine docs that don't yet have <itemref> elements into
        // the spine
        Resource itemRefRes = null;
        for (node = spine.getFirstChild(); node !=null; node = node.getNextSibling()) {
            if (ONS.equals(node.getNamespaceURI()) && "itemref".equals(node.getLocalName())) {
                Element itemref = (Element) node;
                itemRefRes = getResourceById(itemref.getAttributeNS(null, "idref"));
                break;
            }
        }
        for (Resource resource : spineDocs) {
            if (resource == itemRefRes) {
                while (node != null) {
                    if (ONS.equals(node.getNamespaceURI()) && "itemref".equals(node.getLocalName())) {
                        Element itemref = (Element) node;
                        itemRefRes = getResourceById(itemref.getAttributeNS(null, "idref"));
                        break;
                    }                    
                    node = node.getNextSibling();
                }
            } else {
                Element itemref = dom.createElementNS(ONS, "itemref");
                itemref.setAttributeNS(null, "idref", getIdByResource(resource));
                spine.insertBefore(itemref, node);
            }
        }
    }


    private static boolean isItem(Element manifest, Element elt) {
        return ONS.equals(elt.getNamespaceURI()) && "item".equals(elt.getLocalName()) && elt.getParentNode() == manifest;
    }

    private Resource getResourceById(String id) {
        Document dom = opf.getDom();
        Element root = dom.getDocumentElement();
        Element manifest = findUniqueChild(root, ONS, "manifest", null, "Multiple <manifest> elements in " + opf.getPath() + ".", reporter);
        assert manifest != null: "ensureManifest should have created manifest!";
        for (Node n = manifest.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (ONS.equals(n.getNamespaceURI())
                    && "item".equals(n.getLocalName())) {
                Element item = (Element) n;
                if (id.equals(item.getAttributeNS(null, "id"))) {
                    return outputResources.get(manifestUrlToPath(item.getAttributeNS(null, "href")));
                }
            }            
        }
        return null;
    }
    
    private String getIdByResource(Resource resource) {
        Document dom = opf.getDom();
        Element root = dom.getDocumentElement();
        Element manifest = findUniqueChild(root, ONS, "manifest", null, "Multiple <manifest> elements in " + opf.getPath() + ".", reporter);
        assert manifest != null: "ensureManifest should have created manifest!";
        for (Node n = manifest.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (ONS.equals(n.getNamespaceURI())
                    && "item".equals(n.getLocalName())) {
                Element item = (Element) n;
                if (resource == outputResources.get(manifestUrlToPath(item.getAttributeNS(null, "href")))) {
                    return item.getAttributeNS(null, "id");
                }
            }            
        }
        return null;
    }
    
    private void checkItems() {
        Document dom = opf.getDom();
        Element root = dom.getDocumentElement();
        Element manifest = findUniqueChild(root, ONS, "manifest", null, "Multiple <manifest> elements in " + opf.getPath() + ".", reporter);
        assert manifest != null: "ensureManifest should have created manifest!";

        // Collect ids. This intentionally happens before items removals so 
        // that dangling spine references result in errors without an 
        // opportunity to match a newly-generated id that happens to be the
        // same as a removed id.
        Set<String> ids = new HashSet<String>();
        Node current = root;
        Node next;
        idloop: for (;;) {
            if (current.getNodeType() == Node.ELEMENT_NODE) {
                Element elt = (Element) current;
                if (elt.hasAttributeNS(null, "id")) {
                    String id = elt.getAttributeNS(null, "id");
                    if ("".equals(id)) {
                        throw reporter.fatal("Found an empty id in "
                                + opf.getPath() + " on element "
                                + elt.getLocalName() + ".");
                    }
                    if ("".equals(id.trim())) {
                        throw reporter.fatal("Found a whitespace-only id in "
                                + opf.getPath() + " on element "
                                + elt.getLocalName() + ".");
                    }
                    if (ids.contains(id)) {
                        throw reporter.fatal("Duplicate id " + id + " in "
                                + opf.getPath() + ".");
                    }
                    ids.add(id);
                }
                if ((next = current.getFirstChild()) != null) {
                    current = next;
                    continue;
                }
            }
            for (;;) {
                if ((next = current.getNextSibling()) != null) {
                    current = next;
                    break;
                }
                current = current.getParentNode();
                if (current == root)
                    break idloop;
            }
        }
        
        List<Element> styles = new LinkedList<Element>();
        List<Element> docs = new LinkedList<Element>();
        
        Set<Element> remove = new HashSet<Element>();
        List<Element> needId = new LinkedList<Element>();
        for (Node n = manifest.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (ONS.equals(n.getNamespaceURI())
                    && "item".equals(n.getLocalName())) {
                Element item = (Element) n;
                // TODO item without href
                String url = item.getAttributeNS(null, "href");
                String path = manifestUrlToPath(url);
                Resource resource = inputResources.remove(path);
                if (resource == null) {
                    if (refreshExistingManifest) {
                        reporter.info("Missing declared resource " + path + ". Removing manifest entry.");
                        remove.add(item);
                        continue;
                    } else {
                        throw reporter.fatal("Missing declared resource " + path + ".");
                    }
                }
                String type = item.getAttributeNS(null, "media-type").trim();
                type = resource.checkType("".equals(type) ? null
                        : toAsciiLowerCase(type));
                if (type == null) {
                    throw reporter.fatal("Unable to guess the media type of "
                            + path
                            + ". Please declare the type manually in the manifest.");
                }
                item.setAttributeNS(null, "media-type", type);

                if ("text/css".equals(type)) {
                    styles.add(item);
                } else if ("application/xhtml+xml".equals(type)) {
                    docs.add(item);
                    documents.add(resource);
                } else if (removeScripting && "text/javascript".equals(type)) {
                    remove.add(item);
                    continue;
                }

                Set<String> props = generateProperties(
                        item.getAttributeNS(null, "properties"), type, resource);
                if (props.contains("nav")) {
                    if (nav != null) {
                        throw reporter.fatal("Duplicate navigation document "
                                + path + ". Already saw navigation document "
                                + nav.getPath() + ".");
                    }
                    nav = resource;
                }
                if (props.contains("scripted")) {
                    reporter.info("Scripted resource: " + path + ".");
                }
                if (props.contains("remote-resources")) {
                    reporter.info("Resource includes remote resources: " + path + ".");
                }
                item.setAttributeNS(null, "properties", joinSet(props));
                
                // TODO check fallback.

                if (!item.hasAttributeNS(null, "id")) {
                    needId.add(item);
                }

                outputResources.put(resource.getPath(), resource);
            }
        }

        for (Element element : remove) {
            manifest.removeChild(element);
        }
        
        // Yes, the spec requires an id for each item. Even ones that are not
        // referenced from the spine. Sigh.
        for (Element element : needId) {
            if (docs.contains(element) && docs.size() == 1 && !ids.contains("content")) {
                element.setAttributeNS(null, "id", "content");
                ids.add("content");
            } else if (styles.contains(element) && styles.size() == 1 && !ids.contains("style")) {
                element.setAttributeNS(null, "id", "style");
                ids.add("style");
            } else {
                Resource resource = outputResources.get(manifestUrlToPath(element.getAttributeNS(null, "href")));
                String id = generateId(resource, ids);
                element.setAttributeNS(null, "id", id);
                ids.add(id);
            }
        }        
        
        for (Resource resource : inputResources.values()) {
            if (!resource.isHidden()) {
                if (resource.isLegalese()) {
                    outputResources.put(resource.getPath(), resource);
                } else if (resource.getPath().startsWith("META-INF/")) {
                    outputResources.put(resource.getPath(), resource);
                } else {
                    reporter.warn("Omitting undeclared resource: " + resource.getPath() + ".");
                }
            }
        }

        inputResources.clear();
    }

    private String generateId(Resource resource, Set<String> ids) {
        String name = resource.getFileNameWithoutExtension();
        StringBuilder id = new StringBuilder();
        char first = name.charAt(0);
        if (NCName.isNCNameStart(first)) {
            id.append(first);
        } else if (NCName.isNCNameTrail(first)) {
            id.append('i');
            id.append(first);
        } else {
            id.append('i');
        }
        for (int i = 1; i < name.length(); i++) {
            char c = name.charAt(i);
            if (NCName.isNCNameTrail(c)) {
                id.append(c);
            }
        }
        while (ids.contains(id.toString())) {
            id.append('_');
        }
        return id.toString();
    }


    private String joinSet(Set<String> props) {
        StringBuilder builder = new StringBuilder();
        for (String prop : props) {
            builder.append(prop);
            builder.append(' ');
        }
        if (builder.length() > 0) {
            builder.setLength(builder.length() - 1);
        }
        return builder.toString();
    }

    private Set<String> generateProperties(String declaredProperties, String type, Resource resource) {
        Set<String> props = new HashSet<String>();
        String[] tokens = declaredProperties.split("\\s");
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];
            if (token.length() > 0) {
                props.add(token);
            }
        }
        // TODO save original props and report changes
        props.remove("nav");
        props.remove("mathml");
        props.remove("scripted");
        props.remove("svg");
        props.remove("switch");
        if (!type.startsWith("image/")) {
            props.remove("cover-image");
        }
        
        Document dom = resource.getDom();
        if (dom != null) {
            Set<Element> remove = new HashSet<Element>();
            // TODO check remote props.remove("remote-resources");
            Node root = dom.getDocumentElement();
            boolean svg = SVG.equals(root.getNamespaceURI());
            boolean xhtml = XHTML.equals(root.getNamespaceURI());
            Node current = root;
            Node next;
            domwalk: for (;;) {
                if (current.getNodeType() == Node.ELEMENT_NODE) {
                    Element elt = (Element) current;

                    String ns = elt.getNamespaceURI();
                    if (XHTML.equals(ns)) {
                        String ln = elt.getLocalName();
                        if (xhtml || svg) {
                            if ("script".equals(ln) || "form".equals(ln)
                                    || "input".equals(ln)
                                    || "select".equals(ln)
                                    || "textarea".equals(ln)
                                    || "button".equals(ln)) {
                                if (removeScripting) {
                                    remove.add(elt);
                                } else {
                                    props.add("scripted");
                                }
                            } else if ("nav".equals(ln) && "toc".equals(elt.getAttributeNS(OPS, "type"))) {
                                if (props.contains("nav")) {
                                    throw reporter.fatal("More than one table of contents in " + resource.getPath() + ".");
                                }
                                props.add("nav");                                
                            }
                            if (removeScripting) {
                                removeEventHandlers(elt);
                            } else if (hasEventHandlerAttribute(elt)) {
                                props.add("scripted");
                            }
                        }
                    } else if (SVG.equals(ns)) {
                        if (xhtml) {
                            props.add("svg");
                        }
                        if (xhtml || svg) {
                            if ("script".equals(elt.getLocalName())) {
                                if (removeScripting) {
                                    remove.add(elt);
                                } else {
                                    props.add("scripted");
                                }
                            }
                            if (removeScripting) {
                                removeEventHandlers(elt);
                            } else if (hasEventHandlerAttribute(elt)) {
                                props.add("scripted");                            
                            }
                        }
                    } else if (MATHML.equals(ns)) {
                        if (xhtml || svg) {
                            props.add("mathml");
                            if (removeScripting) {
                                removeEventHandlers(elt);
                            } else if (hasEventHandlerAttribute(elt)) {
                                props.add("scripted");                            
                            }
                        }
                    } else if (OPS.equals(ns)) {
                        if (xhtml && "switch".equals(elt.getLocalName())) {
                            props.add("switch");
                        }
                    }
                    
                    if ((next = current.getFirstChild()) != null) {
                        current = next;
                        continue;
                    }
                }
                for (;;) {
                    if ((next = current.getNextSibling()) != null) {
                        current = next;
                        break;
                    }
                    current = current.getParentNode();
                    if (current == root)
                        break domwalk;
                }
            }
            for (Element element : remove) {
                element.getParentNode().removeChild(element);
            }
        }
        
        return props;
    }
    
    private void removeEventHandlers(Element elt) {
        NamedNodeMap atts = elt.getAttributes();
        int i = 0;
        while (i < atts.getLength()) {
            Node attribute = atts.item(i);
            String ns = attribute.getNamespaceURI();
            String ln = attribute.getLocalName();
            if (ns == null
                    && ln.startsWith("on")) {
                atts.removeNamedItemNS(ns, ln);
                // In theory, after removal, the iteration order is no longer
                // guaranteed to be the same, so start over.
                i = 0;
            } else {
                i++;
            }
        }
    }


    private boolean hasEventHandlerAttribute(Element elt) {
        NamedNodeMap atts = elt.getAttributes();
        for (int i = 0; i < atts.getLength(); i++) {
            Node attribute = atts.item(i);
            if (attribute.getNamespaceURI() == null
                    && attribute.getLocalName().startsWith("on")) {
                return true;
            }
        }
        return false;
    }

    private void ensureManifest() {
        Document dom = opf.getDom();
        Element root = dom.getDocumentElement();
        if (!(ONS.equals(root.getNamespaceURI()) && "package".equals(root.getLocalName()))) {
            throw reporter.fatal("Bogus root element in " + opf.getPath() + ".");
        }
        Element manifest = findUniqueChild(root, ONS, "manifest", null, "Multiple <manifest> elements in " + opf.getPath() + ".", reporter);
        if (manifest == null) {
            manifest = dom.createElementNS(ONS, "manifest");
            Element metadata = findUniqueChild(root, ONS, "metadata", null, "Multiple <metadata> elements in " + opf.getPath() + ".", reporter);
            if (metadata != null) {
                root.insertBefore(manifest, metadata.getNextSibling());
            } else {
                Element spine = findUniqueChild(root, ONS, "spine", null, "Multiple <spine> elements in " + opf.getPath() + ".", reporter);
                if (spine != null) {
                    root.insertBefore(manifest, spine);
                } else {
                    Element guide = findUniqueChild(root, ONS, "guide", null, "Multiple <guide> elements in " + opf.getPath() + ".", reporter);
                    if (guide != null) {
                        root.insertBefore(manifest, guide);
                    } else {
                        Element bindings = findUniqueChild(root, ONS, "bindings", null, "Multiple <bindings> elements in " + opf.getPath() + ".", reporter);
                        if (bindings != null) {
                            root.insertBefore(manifest, bindings);
                        } else {
                            root.insertBefore(manifest, root.getFirstChild());
                        }
                    }
                }
            }
        }
        // TODO normalize item URLs
        if (!refreshExistingManifest) {
            for (Node n = manifest.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (ONS.equals(n.getNamespaceURI())
                        && "item".equals(n.getLocalName())) {
                    // Found an item. Treat the manifest as existing.
                    return;
                }
            }
        }
        // Proceed to generating the manifest.
        for (Resource resource: inputResources.values()) {
            // add an <item> for all non-READMEs that don't have entries yet
            if (!resource.isAutoIncluded()) {
                continue;
            }
            String url = pathToManifestUrl(resource.getPath());
            if (findItemByUrl(url, manifest) == null) {
                Element item = dom.createElementNS(ONS, "item");
                item.setAttributeNS(null, "href", url);
                manifest.appendChild(item);
            }
        }
    }

    private Element findItemByUrl(String url, Element manifest) {
        for (Node n = manifest.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (ONS.equals(n.getNamespaceURI())
                    && "item".equals(n.getLocalName())) {
                Element item = (Element) n;
                if (url.equals(item.getAttributeNodeNS(null, "href"))) {
                    return item;
                }
            }
        }
        return null;
    }
    
    private String manifestUrlToPath(String url) {
        return urlToPath(url, opf.getPath(), reporter);
    }
    
    public static boolean isAbsoluteUrl(String url) {
        if (url.length() == 0) {
            return false;
        }
        char c = url.charAt(0);
        if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'))) {
            return false;
        }
        for (int i = 1; i < url.length(); i++) {
            c = url.charAt(i);
            if (c == ':') {
                return true;
            } else if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || (c == '+') || (c == '-') || (c == '.'))) {
                return false;
            }
        }
        return false;
    }
    
    public static String chopHash(String path) {
        if (path == null) {
            return null;
        }
        int index = path.indexOf('#');
        if (index < 0) {
            return path;
        }
        return path.substring(0, index);
    }
    
    public static String urlToPath(String url, String basePath, Reporter reporter) {
        if (url == null) {
            return null;
        }
        if (url.startsWith("//")) {
            throw reporter.fatal("URL " + url + " is a scheme-relative URL where path-relative URL was expected.");            
        } 
        if (url.startsWith("/")) {
            throw reporter.fatal("URL " + url + " is an absolute-path-relative URL where path-relative URL was expected.");
        }
        if (isAbsoluteUrl(url)) {
            throw reporter.fatal("URL " + url + " is an absolute URL where path-relative URL was expected.");            
        }
        
        if (url.startsWith("#")) {
            if (url.length() == 0) {
                return basePath;
            } else {
                return basePath + url;
            }
        }
        
        int index = basePath.lastIndexOf('/');
        String baseDir;
        if (index == -1) {
            baseDir = "";
        } else {
            baseDir = basePath.substring(0, index + 1);
        }
        
        StringBuilder builder = new StringBuilder();
        builder.append(baseDir);
        for (int i = 0; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c == '%' && i + 2 < url.length() && isHex(url.charAt(i + 1)) && isHex(url.charAt(i + 2))) {
                int codePoint = Integer.parseInt(url.substring(i + 1, i + 3), 16);
                if (codePoint == '/') {
                    throw reporter.fatal("Percent encode decodes to a slash in URL " + url + ".");
                } else if (codePoint > 0x7F) {
                    throw reporter.fatal("URL " + url + " contains a non-Basic Latin percent escape. This tool does not support non-Basic Latin file names.");
                }
                builder.append((char)codePoint);
            } else {
                builder.append(c);
            }
        }
        while ((index = builder.indexOf("/./")) != -1) {
             builder.delete(index, index + 2);
        }
        if (builder.indexOf("./") == 0) {
            builder.delete(0, 2);
        }
        if (builder.indexOf("../") == 0) {
            throw reporter.fatal("URL " + url + " points to outside the root directory of the publication.");
        }
        while ((index = builder.indexOf("/../")) != -1) {
            int end = index + 3;
            int start = builder.lastIndexOf("/", index - 1) + 1;
            builder.delete(start, end);
            if (builder.indexOf("../") == 0) {
                throw reporter.fatal("URL " + url + " points to outside the root directory of the publication.");
            }
        }
        return builder.toString();
    }
    
    private static boolean isHex(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')
                || (c >= 'A' && c <= 'F');
    }

    private String pathToUrl(String path, String basePath) {
        int index = basePath.lastIndexOf('/');
        String prefix;
        if (index == -1) {
            return path;
        }
        prefix = basePath.substring(0, index + 1);
        
        for (int level = 0;; level++) {
            if (path.startsWith(prefix)) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < level; i++) {
                    sb.append("../");
                }
                sb.append(path.substring(prefix.length()));
                return sb.toString();
            }
            index = prefix.lastIndexOf('/', prefix.length() - 2);
            if (index == -1) {
                prefix = "";
            } else {
                prefix = prefix.substring(0, index + 1);
            }
        }
    }
    
    private String pathToManifestUrl(String path) {
        return pathToUrl(path, opf.getPath());
    }
    
    private void createContainerXml() {
        container = new Resource("META-INF/container.xml", CNS, "container", reporter);
        Document dom = container.getDom();
        Element root = dom.getDocumentElement();
        // bah. versioning. :-(
        root.setAttribute("version", "1.0");
        Element rootfiles = dom.createElementNS(CNS, "rootfiles");
        root.appendChild(rootfiles);
        Element rootfile = dom.createElementNS(CNS, "rootfile");
        rootfile.setAttribute("media-type", "application/oebps-package+xml");
        rootfile.setAttribute("full-path", opf.getPath());
        rootfiles.appendChild(rootfile);
    }

    private void createOpf(String rootPath) {
        opf = new Resource(rootPath, ONS, "package", reporter);
    }

    private void checkMimetype() {

        Resource res = inputResources.get("mimetype");
        if (res == null) {
            return;
        }
        inputResources.remove("mimetype");
        File file = res.getFile();
        if (file.length() != MIMETYPE.length) {
            throw reporter.fatal("Incorrect mimetype file. Maybe this directory is for a different OASIS packaging-based format?");
        }
        try {
            FileInputStream in = new FileInputStream(file);
            try {
                for (int i = 0; i < MIMETYPE.length; i++) {
                    byte b = MIMETYPE[i];
                    if (in.read() != b) {
                        throw reporter.fatal("Incorrect mimetype file. Maybe this directory is for a different OASIS packaging-based format?");
                    }
                }
            } finally {
                in.close();
            }
        } catch (FileNotFoundException e) {
            throw reporter.fatal("File went away during program execution: mimetype");
        } catch (IOException e) {
            throw reporter.fatal("Unable to read file: mimetype");
        }
    }

    /**
     * @param args
     */
    public static void main(String[] args) {
        Reporter reporter = new Reporter
        ();
        boolean force = false;
        File dir = null;
        
        for (String arg : args) {
            if ("-f".equals(arg)) {
                force = true;
            } else if (dir == null) {
                dir = new File(arg);
            } else {
                reporter.fatal("Bogus argument: " + arg);
            }
        }

        if (dir == null) {
            dir = new File(System.getProperty("user.dir"));
        }
        
        if (!dir.isDirectory()) {
            reporter.fatal("Not a directory: " + dir.toString());            
        }
        
        dir = dir.getAbsoluteFile();
        File parent = dir.getParentFile();
        String name = dir.getName();
        File epub = new File(parent, name + ".epub");
        
        if (epub.exists() && !force) {
            reporter.fatal("Output file already exists (use -f to overwrite): " + epub.toString());            
        }
        
        Dir2Epub d2e = new Dir2Epub(epub, reporter);
        d2e.build(dir);
    }

    /**
     * Finds an element of that has an attribute called <code>id</code> which 
     * has the given value and is not in a namespace. The IDness of the 
     * attribute is based on the attribute name--not on the DTD.
     * 
     * @param node the root of the subtree to search
     * @param id the value of the id attribute
     * @return the first element that has the specified attribute
     */
    public static final Element getElementById(Node node, String id) {
        Node current = node;
        Node next;
        for (;;) {
            switch (current.getNodeType()) {
                case Node.ELEMENT_NODE:
                    Element elt = (Element) current;
                    if (id.equals(elt.getAttribute("id"))) {
                        return elt;
                    }
                // fall through
                case Node.DOCUMENT_FRAGMENT_NODE:
                case Node.DOCUMENT_NODE:
                    if ((next = current.getFirstChild()) != null) {
                        current = next;
                        continue;
                    }
            }
            for (;;) {
                if ((next = current.getNextSibling()) != null) {
                    current = next;
                    break;
                }
                current = current.getParentNode();
                if (current == node)
                    return null;
            }
        }
    }

}

/*
 * Copyright 2013 Henri Sivonen
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

package org.dir2epub.navigation;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.dir2epub.Reporter;
import org.dir2epub.Resource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class NavigationDocument {

    private static final String XHTML = "http://www.w3.org/1999/xhtml";

    private static final String NCX = "http://www.daisy.org/z3986/2005/ncx/";
    
    private static final String OPS = "http://www.idpf.org/2007/ops";

    private boolean emptyToc = false;
    
    private NavigationHierarchy toc = null;
    
    private NavigationHierarchy pageList = null;
    
    private NavigationHierarchy landmarks = null;
    
    public NavigationDocument(NavigationDocument ncx, NavigationDocument html, Reporter reporter) {
        // TOC
        if (ncx.toc == null && html.toc == null) {
            this.toc = null;
        } else if (ncx.toc == null) {
            this.toc = html.toc;
        } else if (html.toc == null) {
            this.toc = ncx.toc;
        } else if (ncx.toc.isEmpty() && html.toc.isEmpty()) {
            if (ncx.toc.hasTitle() && html.toc.hasTitle()) {
                if (ncx.toc.equals(html.toc)) {
                    this.toc = ncx.toc;
                } else {
                    throw reporter.fatal("The title of the table of contents differs between the NCX file and the EPUB3 Navigation Document.");
                }
            } else if (ncx.toc.hasTitle()) {
                this.toc = ncx.toc;                
            } else if (html.toc.hasTitle()) {
                this.toc = html.toc;
            }
        } else if (ncx.toc.isEmpty()) {
            this.toc = html.toc;
        } else if (html.toc.isEmpty()) {
            this.toc = ncx.toc;
        } else if (ncx.toc.equals(html.toc)) {
            this.toc = ncx.toc;
        } else {
            throw reporter.fatal("The table of contents differs between the NCX file and the EPUB3 Navigation Document.");            
        }
        // Page List
        if (ncx.pageList == null && html.pageList == null) {
            this.pageList = null;
        } else if (ncx.pageList == null) {
            this.pageList = html.pageList;
        } else if (html.pageList == null) {
            this.pageList = ncx.pageList;
        } else if (ncx.pageList.isEmpty() && html.pageList.isEmpty()) {
            if (ncx.pageList.hasTitle() && html.pageList.hasTitle()) {
                if (ncx.pageList.equals(html.pageList)) {
                    this.pageList = ncx.pageList;
                } else {
                    throw reporter.fatal("The title of the page list differs between the NCX file and the EPUB3 Navigation Document.");
                }
            } else if (ncx.pageList.hasTitle()) {
                this.pageList = ncx.pageList;                
            } else if (html.pageList.hasTitle()) {
                this.pageList = html.pageList;
            }
        } else if (ncx.pageList.isEmpty()) {
            this.pageList = html.pageList;
        } else if (html.pageList.isEmpty()) {
            this.pageList = ncx.pageList;
        } else if (ncx.pageList.equals(html.pageList)) {
            this.pageList = ncx.pageList;
        } else {
            throw reporter.fatal("The page list differs between the NCX file and the EPUB3 Navigation Document.");            
        }
        // Landmarks
        if (ncx.landmarks == null && html.landmarks == null) {
            this.landmarks = null;
        } else if (ncx.landmarks == null) {
            this.landmarks = html.landmarks;
        } else if (html.landmarks == null) {
            this.landmarks = ncx.landmarks;
        } else if (ncx.landmarks.isEmpty() && html.landmarks.isEmpty()) {
            if (ncx.landmarks.hasTitle() && html.landmarks.hasTitle()) {
                if (ncx.landmarks.equals(html.landmarks)) {
                    this.landmarks = ncx.landmarks;
                } else {
                    throw reporter.fatal("The title of the list of landmarks differs between the NCX file and the EPUB3 Navigation Document.");
                }
            } else if (ncx.landmarks.hasTitle()) {
                this.landmarks = ncx.landmarks;                
            } else if (html.landmarks.hasTitle()) {
                this.landmarks = html.landmarks;
            }
        } else if (ncx.landmarks.isEmpty()) {
            this.landmarks = html.landmarks;
        } else if (html.landmarks.isEmpty()) {
            this.landmarks = ncx.landmarks;
        } else if (ncx.landmarks.equals(html.landmarks)) {
            this.landmarks = ncx.landmarks;
        } else {
            throw reporter.fatal("The list of landmarks differs between the NCX file and the EPUB3 Navigation Document.");            
        }        
    }
    
    public NavigationDocument(Resource resource, Reporter reporter) {
        Document dom = resource.getDom();
        if (dom == null) {
            // Can this even happen at this point anymore.
            throw reporter.fatal("Purported navigation document " + resource.getPath() + " has no DOM.");
        }
        Element root = dom.getDocumentElement();
        String ns = root.getNamespaceURI();
        if (XHTML.equals(ns)) {
            Node current = root;
            Node next;
            domwalk: for (;;) {
                if (current.getNodeType() == Node.ELEMENT_NODE) {
                    Element elt = (Element) current;

                    if ("nav".equals(elt.getLocalName()) && XHTML.equals(elt.getNamespaceURI()) && elt.hasAttributeNS(OPS, "type")) {
                        String type = elt.getAttributeNS(OPS, "type");
                        if ("toc".equals(type)) {
                            if (toc != null) {
                                throw reporter.fatal("Duplicate <nav epub:type='toc'> in the Navigation Document.");
                            }
                            toc = new NavigationHierarchy(elt, resource.getPath(), reporter);                            
                        } else if ("page-list".equals(type)) {
                            if (pageList != null) {
                                throw reporter.fatal("Duplicate <nav epub:type='page-list'> in the Navigation Document.");
                            }
                            pageList = new NavigationHierarchy(elt, resource.getPath(), reporter);                            
                        } else if ("landmarks".equals(type)) {
                            if (landmarks != null) {
                                throw reporter.fatal("Duplicate <nav epub:type='landmarks'> in the Navigation Document.");
                            }
                            landmarks = new NavigationHierarchy(elt, resource.getPath(), reporter);
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
        } else if (NCX.equals(ns)) {
            if (!"ncx".equals(root.getLocalName())) {
                throw reporter.fatal("Purported NCX document " + resource.getPath() + " has a bogus root element."); 
            }
            Node node = root.getFirstChild();
            while (node != null) {
                if (NCX.equals(node.getNamespaceURI())) {
                    String local = node.getLocalName();
                    if ("navMap".equals(local)) {
                        if (toc != null) {
                            throw reporter.fatal("Duplicate <navMap> in NCX.");
                        }
                        toc = new NavigationHierarchy((Element) node, resource.getPath(), reporter);
                    } else if ("pageList".equals(local)) {
                        if (pageList != null) {
                            throw reporter.fatal("Duplicate <pageList> in NCX.");
                        }
                        pageList = new NavigationHierarchy((Element) node, resource.getPath(), reporter);
                    } else if ("navList".equals(local)) {
                        if (landmarks != null) {
                            throw reporter.fatal("Duplicate <navList> in NCX.");
                        }
                        landmarks = new NavigationHierarchy((Element) node, resource.getPath(), reporter);
                    }
                }
                node = node.getNextSibling();
            }
        } else {
            throw reporter.fatal("Purported navigation document " + resource.getPath() + " is neither an NCX nor an XHTML document.");            
        }
    }

    public boolean hasToc() {
        return toc != null;
    }

    public boolean hasNonEmptyToc() {
        return toc != null && !emptyToc;
    }

    public boolean hasPageList() {
        return pageList != null ;
    }

    public boolean hasLandmarks() {
        return landmarks != null ;
    }

    public LinkedHashSet<Resource> listDocs(Map<String, Resource> outputResources,
            Reporter reporter) {
        LinkedHashSet<Resource> set = new LinkedHashSet<Resource>();
        toc.listDocs(set, outputResources);
        if (set.isEmpty()) {
            emptyToc = true;
        }
        return set;
    }

    public void generateNcx(Element hierarchy, String name) {
        for (Node n = hierarchy.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (NCX.equals(n.getNamespaceURI()) && name.equals(n.getLocalName())) {
                return;
            }
        }
        NavigationHierarchy hier = null;
        if ("navPoint".equals(name)) {
            hier = toc;
        } else if ("pageTarget".equals(name)) {
            hier = pageList;
        } else if ("navTarget".equals(name)) {
            hier = landmarks;
        } else {
            assert false: "Bogus name";
        }
        if (hier != null) {
            hier.generateNcx(hierarchy, name);
        }
    }

    public void generateHtmlToc(Element parent) {
        toc.generateHtml(parent);
    }

    public void generateHtmlPageList(Element parent) {
        pageList.generateHtml(parent);
    }

    public void generateHtmlLandmarks(Element parent) {
        landmarks.generateHtml(parent);
    }

}

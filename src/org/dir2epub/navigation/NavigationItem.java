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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.dir2epub.Dir2Epub;
import org.dir2epub.Reporter;
import org.dir2epub.Resource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class NavigationItem {

    private static final String XHTML = "http://www.w3.org/1999/xhtml";

    private static final String NCX = "http://www.daisy.org/z3986/2005/ncx/";

    static String parseNavLabel(Node node, Reporter reporter) {
        node = node.getFirstChild();
        while (node != null) {
            if (NCX.equals(node.getNamespaceURI()) && "navLabel".equals(node.getLocalName())) {
                node = node.getFirstChild();
                while (node != null) {
                    if (NCX.equals(node.getNamespaceURI()) && "text".equals(node.getLocalName())) {
                        return Dir2Epub.normalizeSpace(node.getTextContent());
                    }
                    node = node.getNextSibling();
                }
                throw reporter.fatal("<navLabel> does not have a <text> child in NCX.");
            }
            node = node.getNextSibling();
        }
        return null;
    }
    
    static String parseContentSrc(Node node, Reporter reporter) {
        node = node.getFirstChild();
        while (node != null) {
            if (NCX.equals(node.getNamespaceURI()) && "content".equals(node.getLocalName())) {
                Element elt = (Element) node;
                if (!elt.hasAttributeNS(null, "src")) {
                    throw reporter.fatal("<content> does not have an src attribute in NCX.");
                }
                return elt.getAttributeNS(null, "src");
            }
        }
        return null;
    }

    static List<NavigationItem> parseNcxChildList(Node node,
            String expected, String resPath, Reporter reporter) {
        List<NavigationItem> list = new LinkedList<NavigationItem>();
        node = node.getFirstChild();
        while (node != null) {
            if (NCX.equals(node.getNamespaceURI()) && expected.equals(node.getLocalName())) {
                list.add(new NavigationItem(node, resPath, reporter));
            }
        }
        return list;
    }

    static List<NavigationItem> parseHtmlChildList(Node node, String resPath, Reporter reporter) {
        List<NavigationItem> list = new LinkedList<NavigationItem>();
        node = node.getFirstChild();
        while (node != null) {
            if (XHTML.equals(node.getNamespaceURI()) && "li".equals(node.getLocalName())) {
                list.add(new NavigationItem(node, resPath, reporter));
            } else if (node.getNodeType() == Node.ELEMENT_NODE) {
                throw reporter.fatal("Non-<li> element in <ol> in navigation document " + resPath + ".");
            }
            // TODO check non-whitespace text nodes
        }
        return list;
    }

    private String title;
    
    private String href;
    
    private List<NavigationItem> sublist;
    
    public NavigationItem(Node node, String resPath, Reporter reporter) {
        super();
        String ns = node.getNamespaceURI();
        if (NCX.equals(ns)) {
            this.title = NavigationItem.parseNavLabel(node, reporter);
            this.href = Dir2Epub.urlToPath(NavigationItem.parseContentSrc(node, reporter), resPath, reporter);
            this.sublist = NavigationItem.parseNcxChildList(node, node.getLocalName(), resPath, reporter);
            if (title == null) {
                throw reporter.fatal("No title in navigation item in " + resPath + ".");
            }
            if (title.length() == 0) {
                throw reporter.fatal("Empty title in navigation item in " + resPath + ".");
            }
            if (href == null && sublist.size() == 0) {
                throw reporter.fatal("Navigation item in " + resPath + " has neither neither an URL nor a sublist.");
            }
        } else if (XHTML.equals(ns)) {
            node = node.getFirstChild();
            boolean seenTitle = false;
            while (node != null) {
                if (!seenTitle && XHTML.equals(node.getNamespaceURI()) && "a".equals(node.getLocalName())) {
                    this.title = Dir2Epub.normalizeSpace(node.getTextContent());
                    Element elt = (Element) node;
                    if (!elt.hasAttributeNS(null, "href")) {
                        throw reporter.fatal("<a> element in navigation hierarchy does not have an href attribute.");
                    }
                    this.href = Dir2Epub.urlToPath(elt.getAttributeNS(null, "href"), resPath, reporter);
                } else if (!seenTitle && XHTML.equals(node.getNamespaceURI()) && "span".equals(node.getLocalName())) {
                    this.title = Dir2Epub.normalizeSpace(node.getTextContent());
                    this.href = null;
                } else if (seenTitle && XHTML.equals(node.getNamespaceURI()) && "ol".equals(node.getLocalName())) {
                    this.sublist = NavigationItem.parseHtmlChildList(node, resPath, reporter);
                } else if (node.getNodeType() == Node.ELEMENT_NODE) {
                    throw reporter.fatal("Invalid navigation <li> element in " + resPath + ".");                    
                }
                // TODO non-whitespace text nodes
            }
            if (this.sublist == null) {
                this.sublist = new LinkedList<NavigationItem>();
            }
        } else {
            assert false: "Should have NCX or XHTML here.";
        }
    }

    /**
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override public boolean equals(Object obj) {
        if (!(obj instanceof NavigationItem)) {
            return false;
        }
        NavigationItem other = (NavigationItem) obj;
        if (this.title == null) {
            if (other.title != null) {
                return false;
            }
        } else if (!this.title.equals(other.title)) {
            return false;
        }
        if (this.href == null) {
            if (other.href != null) {
                return false;
            }
        } else if (!this.href.equals(other.href)) {
            return false;
        }
        return this.sublist.equals(other.sublist);
    }

    /**
     * @see java.lang.Object#hashCode()
     */
    @Override public int hashCode() {
        int titleHash = 0;
        if (title != null) {
            titleHash = title.hashCode();
        }
        int hrefHash = 0;
        if (href != null) {
            hrefHash = href.hashCode();
        }
        int listHash = 0;
        if (sublist != null) {
            listHash = sublist.hashCode();
        }
        return titleHash ^ hrefHash ^ listHash;
    }

    public void listDocs(LinkedHashSet<Resource> set,
            Map<String, Resource> outputResources) {
        String path = getHrefWithoutHash();
        if (path != null) {
            Resource resource = outputResources.get(path);
            if (resource != null) {
                set.add(resource);
            }
        }
        for (NavigationItem item : sublist) {
            item.listDocs(set, outputResources);
        }
    }
    
    private String getHrefWithoutHash() {
        return Dir2Epub.chopHash(this.href);
    }

    public void generateNcx(Element parent, String name) {
        Document dom = parent.getOwnerDocument();
        Element elt = dom.createElementNS(NCX, name);
        parent.appendChild(elt);
        Element navLabel = dom.createElementNS(NCX, "navLabel");
        parent.appendChild(navLabel);
        Element text = dom.createElementNS(NCX, "text");
        navLabel.appendChild(text);
        text.setTextContent(title);
        if (href != null) {
            Element content = dom.createElementNS(NCX, "content");
            parent.appendChild(content);
            content.setAttributeNS(null, "src", href);
        }
        for (NavigationItem item : sublist) {
            item.generateNcx(elt, name);
        }
    }

    public void generateHtml(Element parent) {
        Element li = parent.getOwnerDocument().createElementNS(XHTML, "li");
        parent.appendChild(li);
        Element aSpan;
        if (href == null) {
            aSpan = parent.getOwnerDocument().createElementNS(XHTML, "span");
        } else {
            aSpan = parent.getOwnerDocument().createElementNS(XHTML, "a");
            aSpan.setAttributeNS(null, "href", href);
        }
        li.appendChild(aSpan);
        aSpan.setTextContent(title);
        if (!sublist.isEmpty()) {
            Element ol = parent.getOwnerDocument().createElementNS(XHTML, "ol");
            li.appendChild(ol);
            for (NavigationItem item : sublist) {
                item.generateHtml(ol);
            }
        }
    }
}

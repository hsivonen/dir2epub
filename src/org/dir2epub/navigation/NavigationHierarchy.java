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

import org.dir2epub.Reporter;
import org.dir2epub.Resource;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Corresponds to an EPUB3 <code>nav</code> element or an NCX 
 * <code>navMap</code>, <code>pageList</code> or <code>navList</code> 
 * element.
 * 
 * @author hsivonen
 */
public class NavigationHierarchy {

    private static final String XHTML = "http://www.w3.org/1999/xhtml";

    private static final String NCX = "http://www.daisy.org/z3986/2005/ncx/";
    
    private String title;
    
    private List<NavigationItem> list;

    public NavigationHierarchy(Element elt, String resPath, Reporter reporter) {
        String ns = elt.getNamespaceURI();
        if (XHTML.equals(ns)) {
            Node node = elt.getFirstChild();
            boolean seenHeading = false;
            while (node != null) {
                if (XHTML.equals(node.getNamespaceURI())) {
                    String local = node.getLocalName();
                    if (!seenHeading && ("h1".equals(local) || "h2".equals(local) || "h3".equals(local) || "h4".equals(local) || "h5".equals(local) || "h6".equals(local) || "hgroup".equals(local))) {
                        this.title = NavigationItem.normalizeSpace(node.getTextContent());
                    } else if ("ol".equals(local)) {
                        this.list = NavigationItem.parseHtmlChildList(node, resPath, reporter);
                    } else {
                        throw reporter.fatal("Stray element " + local + " in navigation document " + resPath + ".");
                    }
                }
            }
            if (list == null) {
                list = new LinkedList<NavigationItem>();
            }
        } else if (NCX.equals(ns)) {
            this.title = NavigationItem.parseNavLabel(elt, reporter);
            String local = elt.getLocalName();
            if ("navMap".equals(local)) {
                this.list = NavigationItem.parseNcxChildList(elt, "navPoint", resPath, reporter);
            } else if ("pageList".equals(local)) {
                this.list = NavigationItem.parseNcxChildList(elt, "pageTarget", resPath, reporter);
            } else if ("navList".equals(local)) {
                this.list = NavigationItem.parseNcxChildList(elt, "navTarget", resPath, reporter);
            } else {
                assert false: "Should have navMap, pageList or navList here.";
            }
        } else {
            assert false: "Should be XHTML or NCX here.";
        }
    }
    
    public boolean isEmpty() {
        return this.list.isEmpty();
    }
    
    public boolean hasTitle() {
        return title != null;
    }

    /**
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override public boolean equals(Object obj) {
        if (!(obj instanceof NavigationHierarchy)) {
            return false;
        }
        NavigationHierarchy other = (NavigationHierarchy) obj;
        if (this.title == null) {
            if (other.title != null) {
                return false;
            }
        } else if (!this.title.equals(other.title)) {
            return false;
        }
        return this.list.equals(other.list);
    }

    /**
     * @see java.lang.Object#hashCode()
     */
    @Override public int hashCode() {
        int titleHash = 0;
        if (title != null) {
            titleHash = title.hashCode();
        }
        int listHash = 0;
        if (list != null) {
            listHash = list.hashCode();
        }
        return titleHash ^ listHash;
    }

    public void listDocs(LinkedHashSet<Resource> set,
            Map<String, Resource> outputResources) {
        for (NavigationItem item : list) {
            item.listDocs(set, outputResources);
        }        
    }
}

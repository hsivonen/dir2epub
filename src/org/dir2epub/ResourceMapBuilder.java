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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

public class ResourceMapBuilder {

    private final Reporter reporter;
    
    public ResourceMapBuilder(Reporter reporter) {
        super();
        this.reporter = reporter;
    }

    public Map<String, Resource> buildMap(File directory) {
        assert directory.isDirectory(): "Directory is not directory!";
        Map<String, Resource> map = new HashMap<String, Resource>();
        recursivelyBuildMap(map, "", directory);
        return map;
    }

    private void recursivelyBuildMap(Map<String, Resource> map, String pathPrefix, File directory) {
        File[] files = directory.listFiles();
        HashSet<String> namesSeen = new HashSet<String>();
        fileloop: for (int i = 0; i < files.length; i++) {
            File file = files[i];
            String name = file.getName();
            boolean isDir = file.isDirectory();
            final String word = isDir ? "directory" : "file";
            if (name == null || name.length() == 0) {
                reporter.err("Dropping nameless " + word + " in: " + pathPrefix);
                continue fileloop;
            }
            String zipName = pathPrefix + name;
            if (!isDir && !file.isFile()) {
                reporter.err("Dropping non-file: " + zipName);
                continue fileloop;
            }
            for (int j = 0; j < name.length(); j++) {
                char c = name.charAt(j);
                if (c < ' ') {
                    reporter.err("Dropping a " + word + " with a control character in name: " + zipName);
                    continue fileloop;
                }
                switch (c) {
                    case '/':
                        reporter.err("Dropping a " + word + " with a slash in name: " + zipName);
                        continue fileloop;                    
                    case '"':
                        reporter.err("Dropping a " + word + " with a quote in name: " + zipName);
                        continue fileloop;                    
                    case '*':
                        reporter.err("Dropping a " + word + " with an asterisk in name: " + zipName);
                        continue fileloop;                    
                    case ':':
                        reporter.err("Dropping a " + word + " with a colon in name: " + zipName);
                        continue fileloop;                    
                    case '<':
                        reporter.err("Dropping a " + word + " with a less-than sign in name: " + zipName);
                        continue fileloop;                    
                    case '>':
                        reporter.err("Dropping a " + word + " with a greater-than sign in name: " + zipName);
                        continue fileloop;                    
                    case '?':
                        reporter.err("Dropping a " + word + " with a question mark in name: " + zipName);
                        continue fileloop;                    
                    case '\\':
                        reporter.err("Dropping a " + word + " with a backslash in name: " + zipName);
                        continue fileloop;                    
                    case 0x7F:
                        reporter.err("Dropping a " + word + " with a DEL in name: " + zipName);
                        continue fileloop;                    
                }
                if (c > 0x7F) {
                    reporter.err("Dropping a " + word + " with an non-ASCII character in name: " + zipName);
                    continue fileloop;
                }
            }
            if (name.endsWith(".")) {
                reporter.err("Dropping a " + word + " whose name ends with a dot: " + zipName);
                continue fileloop;
            }
            // We are limited to ASCII now. No need for fancy canonicalization.
            String lc = name.toLowerCase();
            if (namesSeen.contains(lc)) {
                reporter.err("Dropping a " + word + " whose name differs only in case from the name of another file or directory: " + zipName);
                continue fileloop;
            }
            namesSeen.add(lc);
            if (isDir) {
                recursivelyBuildMap(map, zipName + '/', file);
                continue fileloop;
            }
            map.put(zipName, new Resource(zipName, file, reporter));
        }
    }
    
}

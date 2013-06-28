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

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class EpubWriter {
    
    private static final byte[] MIMETYPE = { 'a', 'p', 'p', 'l', 'i', 'c', 'a',
            't', 'i', 'o', 'n', '/', 'e', 'p', 'u', 'b', '+', 'z', 'i', 'p' };

    private final ZipOutputStream zip;
    
    private final Reporter reporter;
    
    public EpubWriter(OutputStream out, Reporter reporter) throws IOException {
        this.zip = new ZipOutputStream(out);
        this.reporter = reporter;
        this.zip.setMethod(ZipOutputStream.STORED);
        this.zip.setLevel(9);
        ZipEntry mimeEntry = new ZipEntry("mimetype");
        CRC32 crc32 = new CRC32();
        crc32.update(MIMETYPE);
        mimeEntry.setSize(MIMETYPE.length);
        mimeEntry.setCompressedSize(MIMETYPE.length);
        mimeEntry.setCrc(crc32.getValue());
        this.zip.putNextEntry(mimeEntry);
        this.zip.write(MIMETYPE);
        this.zip.closeEntry();
        reporter.info("S mimetype");
    }
    
    public void write(Resource resource) {
        resource.writeToZip(this.zip);
    }
    
    public void close() {
        try {
            this.zip.close();
        } catch (IOException e) {
            throw reporter.fatal("Closing output file failed.");
        }
    }
}

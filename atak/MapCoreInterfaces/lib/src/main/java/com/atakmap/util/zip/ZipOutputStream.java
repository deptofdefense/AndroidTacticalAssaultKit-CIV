/**
 * Specific file pulled and modified from
 * https://android.googlesource.com/platform/libcore/+/android-6.0.1_r21/luni/src/main/java/java/util/zip/ZipOutputStream.java
 *
 * In support of
 * https://android.googlesource.com/platform/libcore/+/android-6.0.1_r21/luni/src/main/java/java/util/zip
 */

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.atakmap.util.zip;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Used to write (compress) data into zip files.
 *
 * <p>{@code ZipOutputStream} is used to write {@link ZipEntry}s to the underlying
 * stream. Output from {@code ZipOutputStream} can be read using {@link ZipFile}
 * or {@link ZipInputStream}.
 *
 * <p>While {@code DeflaterOutputStream} can write compressed zip file
 * entries, this extension can write uncompressed entries as well.
 * Use {@link ZipEntry#setMethod} or {@link #setMethod} with the {@link ZipEntry#STORED} flag.
 *
 * <h3>Example</h3>
 * <p>Using {@code ZipOutputStream} is a little more complicated than {@link GZIPOutputStream}
 * because zip files are containers that can contain multiple files. This code creates a zip
 * file containing several files, similar to the {@code zip(1)} utility.
 * <pre>
 * OutputStream os = ...
 * ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(os));
 * try {
 *     for (int i = 0; i < fileCount; ++i) {
 *         String filename = ...
 *         byte[] bytes = ...
 *         ZipEntry entry = new ZipEntry(filename);
 *         zos.putNextEntry(entry);
 *         zos.write(bytes);
 *         zos.closeEntry();
 *     }
 * } finally {
 *     zos.close();
 * }
 * </pre>
 */
final class ZipOutputStream {
    static long writeLongAsUint32(OutputStream os, long i) throws IOException {
        // Write out the long value as an unsigned int
        os.write((int) (i & 0xFF));
        os.write((int) (i >> 8) & 0xFF);
        os.write((int) (i >> 16) & 0xFF);
        os.write((int) (i >> 24) & 0xFF);
        return i;
    }

    static long writeLongAsUint64(OutputStream os, long i) throws IOException {
        int i1 = (int) i;
        os.write(i1 & 0xFF);
        os.write((i1 >> 8) & 0xFF);
        os.write((i1 >> 16) & 0xFF);
        os.write((i1 >> 24) & 0xFF);
        int i2 = (int) (i >> 32);
        os.write(i2 & 0xFF);
        os.write((i2 >> 8) & 0xFF);
        os.write((i2 >> 16) & 0xFF);
        os.write((i2 >> 24) & 0xFF);
        return i;
    }

    static int writeIntAsUint16(OutputStream os, int i) throws IOException {
        os.write(i & 0xFF);
        os.write((i >> 8) & 0xFF);
        return i;
    }
}

/*
 * Copyright (c) 2017, Joyent, Inc. All rights reserved.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.joyent.manta.presto.record.telegraf;

import com.facebook.presto.spi.ConnectorSession;
import com.facebook.presto.spi.type.Type;
import com.facebook.presto.testing.TestingConnectorSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.joyent.manta.client.MantaObject;
import com.joyent.manta.presto.MantaCountingInputStream;
import com.joyent.manta.presto.column.MantaColumn;
import com.joyent.manta.presto.record.json.MantaJsonDataFileObjectMapperProvider;
import com.joyent.manta.presto.record.json.MantaJsonRecordCursor;
import io.airlift.slice.Slice;
import org.junit.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Test
public class MantaTelegrafJsonRecordCursorTest {
    private static final boolean OUTPUT_ENABLED = true;

    public void canParseSampleRecordsWithoutAnError() throws IOException {
        canParseTelegrafJsonSampleRecordsWithoutAnError("test-data/cursor/metrics-2017-06-01.telegraf.json");
    }

    @SuppressWarnings("Duplicates")
    private void canParseTelegrafJsonSampleRecordsWithoutAnError(final String testFile) throws IOException {
        final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        final ConnectorSession session = new TestingConnectorSession(Collections.emptyList());
        List<MantaColumn> columns = new MantaTelegrafColumnLister().listColumns(null, null, session);

        final long totalBytes = 37782293;

        InputStream in = classLoader.getResourceAsStream(testFile);
        MantaObject object = mock(MantaObject.class);
        when(object.getPath()).thenReturn(testFile);

        MantaCountingInputStream cin = new MantaCountingInputStream(in, object);
        MantaJsonDataFileObjectMapperProvider mapperProvider = new MantaJsonDataFileObjectMapperProvider();
        ObjectMapper mapper = mapperProvider.get();
        ObjectReader streamingReader = mapper.readerFor(ObjectNode.class);

        try (MantaJsonRecordCursor cursor = new MantaJsonRecordCursor(null,
                columns, cin.getPath(), totalBytes, cin,
                streamingReader)) {

            final int columnLen = columns.size();
            long line = 0L;
            while (cursor.advanceNextPosition()) {
                print("[");
                printf("%06d", ++line);

                for (int i = 0; i < columnLen; i++) {
                    printf(" col-%d=", i);

                    if (cursor.isNull(i)) {
                        print("null");
                        continue;
                    }

                    Type type = cursor.getType(i);
                    Class<?> javaType = type.getJavaType();
                    if (javaType == boolean.class) {
                        final boolean val = cursor.getBoolean(i);

                        print("boolean");
                        printf(" (%b)", val);
                    } else if (javaType == long.class) {
                        final long val = cursor.getLong(i);

                        print("long");
                        printf(" (%d)", val);
                    } else if (javaType == double.class) {
                        final double val = cursor.getDouble(i);

                        print("double");
                        printf(" (%d)", val);
                    } else if (javaType == Slice.class) {
                        final String val = cursor.getSlice(i).toStringUtf8();
                        Assert.assertNotNull(val);

                        print("slice");
                        printf(" (%s)", val);
                    } else {
                        final String val = cursor.getObject(i).toString();
                        Assert.assertNotNull(val);

                        print("object");
                        printf(" (%s)", val);
                    }
                }

                println("]");
            }

            Duration readTime = Duration.of(cursor.getReadTimeNanos(), ChronoUnit.NANOS);
            System.err.printf("Read file [%s] in %d ms\n", testFile, readTime.toMillis());
        }
    }

    private void print(final String s) {
        if (OUTPUT_ENABLED) {
            System.out.print(s);
        }
    }

    private void printf(final String s, Object... args) {
        if (OUTPUT_ENABLED) {
            System.out.printf(s, args);
        }
    }

    private void println(final String s) {
        if (OUTPUT_ENABLED) {
            System.out.println(s);
        }
    }
}

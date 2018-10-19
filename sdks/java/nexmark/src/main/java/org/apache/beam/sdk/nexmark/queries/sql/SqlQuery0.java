/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.beam.sdk.nexmark.queries.sql;

import static org.apache.beam.sdk.nexmark.queries.NexmarkQuery.IS_BID;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.beam.sdk.coders.Coder;
import org.apache.beam.sdk.extensions.sql.SqlTransform;
import org.apache.beam.sdk.metrics.Counter;
import org.apache.beam.sdk.metrics.Metrics;
import org.apache.beam.sdk.nexmark.model.Bid;
import org.apache.beam.sdk.nexmark.model.Event;
import org.apache.beam.sdk.nexmark.model.Event.Type;
import org.apache.beam.sdk.nexmark.model.sql.SelectEvent;
import org.apache.beam.sdk.schemas.transforms.Convert;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.Filter;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.PInput;
import org.apache.beam.sdk.values.Row;

/**
 * Query 0: Pass events through unchanged.
 *
 * <p>This measures the overhead of the Beam SQL implementation and test harness like conversion
 * from Java model classes to Beam records.
 *
 * <p>{@link Bid} events are used here at the moment, ås they are most numerous with default
 * configuration.
 */
public class SqlQuery0 extends PTransform<PCollection<Event>, PCollection<Bid>> {

  private static final PTransform<PInput, PCollection<Row>> QUERY =
      SqlTransform.query("SELECT * FROM PCOLLECTION");

  public SqlQuery0() {
    super("SqlQuery0");
  }

  @Override
  public PCollection<Bid> expand(PCollection<Event> allEvents) {
    PCollection<Row> rows =
        allEvents
            .apply(Filter.by(IS_BID))
            .apply(getName() + ".SelectEvent", new SelectEvent(Type.BID));

    return rows.apply(getName() + ".Serialize", logBytesMetric(rows.getCoder()))
        .apply(QUERY)
        .apply(Convert.fromRows(Bid.class));
  }

  private PTransform<? super PCollection<Row>, PCollection<Row>> logBytesMetric(
      final Coder<Row> coder) {

    return ParDo.of(
        new DoFn<Row, Row>() {
          private final Counter bytesMetric = Metrics.counter(name, "bytes");

          @ProcessElement
          public void processElement(@Element Row element, OutputReceiver<Row> o)
              throws IOException {
            ByteArrayOutputStream outStream = new ByteArrayOutputStream();
            coder.encode(element, outStream, Coder.Context.OUTER);
            byte[] byteArray = outStream.toByteArray();
            bytesMetric.inc((long) byteArray.length);
            ByteArrayInputStream inStream = new ByteArrayInputStream(byteArray);
            Row row = coder.decode(inStream, Coder.Context.OUTER);
            o.output(row);
          }
        });
  }
}
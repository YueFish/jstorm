/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package backtype.storm.drpc;

import backtype.storm.coordination.CoordinatedBolt.FinishedCallback;
import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.BasicBoltExecutor;
import backtype.storm.topology.IBasicBolt;
import backtype.storm.topology.IRichBolt;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.tuple.Tuple;
import backtype.storm.utils.KeyedRoundRobinQueue;
import java.util.HashMap;
import java.util.Map;

public class KeyedFairBolt implements IRichBolt, FinishedCallback {
    IRichBolt _delegate;
    KeyedRoundRobinQueue<Tuple> _rrQueue;
    Thread _executor;
    FinishedCallback _callback;

    public KeyedFairBolt(IRichBolt delegate) {
        _delegate = delegate;
    }

    public KeyedFairBolt(IBasicBolt delegate) {
        this(new BasicBoltExecutor(delegate));
    }

    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        if (_delegate instanceof FinishedCallback) {
            _callback = (FinishedCallback) _delegate;
        }
        _delegate.prepare(stormConf, context, collector);
        _rrQueue = new KeyedRoundRobinQueue<>();
        _executor = new Thread(new Runnable() {
            public void run() {
                try {
                    while (true) {
                        _delegate.execute(_rrQueue.take());
                    }
                } catch (InterruptedException ignored) {
                }
            }
        });
        _executor.setDaemon(true);
        _executor.start();
    }

    public void execute(Tuple input) {
        Object key = input.getValue(0);
        _rrQueue.add(key, input);
    }

    public void cleanup() {
        _executor.interrupt();
        _delegate.cleanup();
    }

    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        _delegate.declareOutputFields(declarer);
    }

    public void finishedId(Object id) {
        if (_callback != null) {
            _callback.finishedId(id);
        }
    }

    @Override
    public Map<String, Object> getComponentConfiguration() {
        return new HashMap<>();
    }
}

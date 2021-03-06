/*
 * Copyright 2020 Netflix, Inc.
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

package io.mantisrx.server.worker.jobmaster.clutch.rps;

import com.netflix.control.clutch.Clutch;
import com.netflix.control.clutch.ClutchConfiguration;
import com.yahoo.sketches.quantiles.UpdateDoublesSketch;
import io.mantisrx.runtime.descriptor.StageScalingPolicy;
import io.mantisrx.runtime.descriptor.StageSchedulingInfo;
import io.mantisrx.shaded.com.google.common.collect.ImmutableMap;
import io.vavr.Tuple;
import io.vavr.control.Option;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RpsClutchConfigurationSelectorTest {
    private static final Logger logger = LoggerFactory.getLogger(RpsClutchConfigurationSelectorTest.class);

    @Test
    public void testApply() {
        UpdateDoublesSketch rpsSketch = UpdateDoublesSketch.builder().setK(1024).build();
        rpsSketch.update(100);
        Map<Clutch.Metric, UpdateDoublesSketch> sketches = ImmutableMap.of(Clutch.Metric.RPS, rpsSketch);

        ClutchRpsPIDConfig rpsConfig = new ClutchRpsPIDConfig(0.0, Tuple.of(20.0, 10.0), 0, 0, Option.none(), Option.none(), Option.none(), Option.none(), Option.none());
        io.mantisrx.server.worker.jobmaster.clutch.ClutchConfiguration customConfig = new io.mantisrx.server.worker.jobmaster.clutch.ClutchConfiguration(
                1, 10, 0, Option.none(), Option.of(300L), Option.none(), Option.none(), Option.none(), Option.none(), Option.none(), Option.of(rpsConfig), Option.none());

        StageSchedulingInfo schedulingInfo = new StageSchedulingInfo(3, null, null, null, null, true);
        RpsClutchConfigurationSelector selector = new RpsClutchConfigurationSelector(1, schedulingInfo, customConfig);

        ClutchConfiguration config = selector.apply(sketches);

        assertEquals(Clutch.Metric.RPS, config.getMetric());
        assertEquals(100.0, config.getSetPoint(), 1e-10);
        assertEquals(1, config.getMinSize());
        assertEquals(10, config.getMaxSize());
        assertEquals(Tuple.of(20.0, 10.0), config.getRope());
        assertEquals(300L, config.getCooldownInterval());
    }

    @Test
    public void testScalingPolicyFallback() {
        UpdateDoublesSketch rpsSketch = UpdateDoublesSketch.builder().setK(1024).build();
        rpsSketch.update(100);
        Map<Clutch.Metric, UpdateDoublesSketch> sketches = ImmutableMap.of(Clutch.Metric.RPS, rpsSketch);

        StageScalingPolicy scalingPolicy = new StageScalingPolicy(1, 2, 9, 0, 0, 400L, null);

        StageSchedulingInfo schedulingInfo = new StageSchedulingInfo(3, null, null, null, scalingPolicy, true);
        RpsClutchConfigurationSelector selector = new RpsClutchConfigurationSelector(1, schedulingInfo, null);

        ClutchConfiguration config = selector.apply(sketches);

        assertEquals(Clutch.Metric.RPS, config.getMetric());
        assertEquals(100.0, config.getSetPoint(), 1e-10);
        assertEquals(2, config.getMinSize());
        assertEquals(9, config.getMaxSize());
        assertEquals(Tuple.of(30.0, 0.0), config.getRope());
        assertEquals(400L, config.getCooldownInterval());

    }

    @Test
    public void testSetPointQuantile() {
        UpdateDoublesSketch rpsSketch = UpdateDoublesSketch.builder().setK(1024).build();
        for (int i = 1; i <= 100; i++) {
            rpsSketch.update(i);
        }
        Map<Clutch.Metric, UpdateDoublesSketch> sketches = ImmutableMap.of(Clutch.Metric.RPS, rpsSketch);

        StageScalingPolicy scalingPolicy = new StageScalingPolicy(1, 2, 9, 0, 0, 400L, null);

        StageSchedulingInfo schedulingInfo = new StageSchedulingInfo(3, null, null, null, scalingPolicy, true);
        RpsClutchConfigurationSelector selector = new RpsClutchConfigurationSelector(1, schedulingInfo, null);

        ClutchConfiguration config = selector.apply(sketches);

        assertEquals(76.0, config.getSetPoint(), 1e-10);
        assertEquals(Tuple.of(22.8, 0.0), config.getRope());

    }

    @Test
    public void testReturnSameConfigIfSetPointWithin5Percent() {
        UpdateDoublesSketch rpsSketch = UpdateDoublesSketch.builder().setK(1024).build();
        for (int i = 1; i <= 100; i++) {
            rpsSketch.update(i);
        }
        Map<Clutch.Metric, UpdateDoublesSketch> sketches = ImmutableMap.of(Clutch.Metric.RPS, rpsSketch);

        StageScalingPolicy scalingPolicy = new StageScalingPolicy(1, 2, 9, 0, 0, 400L, null);

        StageSchedulingInfo schedulingInfo = new StageSchedulingInfo(3, null, null, null, scalingPolicy, true);
        RpsClutchConfigurationSelector selector = new RpsClutchConfigurationSelector(1, schedulingInfo, null);

        ClutchConfiguration config = selector.apply(sketches);

        assertEquals(76.0, config.getSetPoint(), 1e-10);

        for (int i = 101; i <= 105; i++) {
            rpsSketch.update(i);
        }
        ClutchConfiguration newConfig = selector.apply(sketches);
        // Instance equality
        assertTrue(config == newConfig);
        for (int i = 106; i < 110; i++) {
            rpsSketch.update(i);
        }
        newConfig = selector.apply(sketches);
        assertTrue(config != newConfig);
        assertEquals(82.0, newConfig.getSetPoint(), 1e-10);
    }

    @Test
    public void testSetPointDriftAdjust() {
        UpdateDoublesSketch rpsSketch = UpdateDoublesSketch.builder().setK(1024).build();
        for (int i = 1; i <= 100; i++) {
            if (i <= 76) {
                rpsSketch.update(i);
            } else {
                rpsSketch.update(1000 + i);
            }
        }
        Map<Clutch.Metric, UpdateDoublesSketch> sketches = ImmutableMap.of(Clutch.Metric.RPS, rpsSketch);

        StageScalingPolicy scalingPolicy = new StageScalingPolicy(1, 2, 9, 0, 0, 400L, null);

        StageSchedulingInfo schedulingInfo = new StageSchedulingInfo(3, null, null, null, scalingPolicy, true);
        RpsClutchConfigurationSelector selector = new RpsClutchConfigurationSelector(1, schedulingInfo, null);

        ClutchConfiguration config = selector.apply(sketches);

        assertEquals(83.6, config.getSetPoint(), 1e-10);

    }
}

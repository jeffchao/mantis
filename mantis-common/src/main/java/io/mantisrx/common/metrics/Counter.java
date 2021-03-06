/*
 * Copyright 2019 Netflix, Inc.
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

package io.mantisrx.common.metrics;


import io.mantisrx.common.metrics.spectator.MetricId;


public interface Counter {

    public void increment();

    public void increment(long x);

    public long value();

    /**
     * @return rate value of counter
     *
     * @deprecated this api will be deprecated after metrics are migrated to spectator-api
     */
    @Deprecated
    public long rateValue();

    /**
     * @return rate time in millis
     *
     * @deprecated this api will be deprecated after metrics are migrated to spectator-api
     */
    @Deprecated
    public long rateTimeInMilliseconds();

    public String event();

    public MetricId id();
}

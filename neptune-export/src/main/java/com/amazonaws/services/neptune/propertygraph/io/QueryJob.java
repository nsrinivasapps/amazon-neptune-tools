/*
Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
Licensed under the Apache License, Version 2.0 (the "License").
You may not use this file except in compliance with the License.
A copy of the License is located at
    http://www.apache.org/licenses/LICENSE-2.0
or in the "license" file accompanying this file. This file is distributed
on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
express or implied. See the License for the specific language governing
permissions and limitations under the License.
*/

package com.amazonaws.services.neptune.propertygraph.io;

import com.amazonaws.services.neptune.io.Directories;
import com.amazonaws.services.neptune.io.Status;
import com.amazonaws.services.neptune.propertygraph.NamedQuery;
import com.amazonaws.services.neptune.propertygraph.ConcurrencyConfig;
import com.amazonaws.services.neptune.propertygraph.NeptuneGremlinClient;
import com.amazonaws.services.neptune.util.Timer;

import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class QueryJob {
    private final Queue<NamedQuery> queries;
    private final NeptuneGremlinClient.QueryClient queryClient;
    private final ConcurrencyConfig concurrencyConfig;
    private final Directories directories;
    private final Format format;

    public QueryJob(Collection<NamedQuery> queries,
                    NeptuneGremlinClient.QueryClient queryClient,
                    ConcurrencyConfig concurrencyConfig,
                    Directories directories,
                    Format format){
        this.queries = new ConcurrentLinkedQueue<>(queries);
        this.queryClient = queryClient;
        this.concurrencyConfig = concurrencyConfig;
        this.directories = directories;
        this.format = format;
    }

    public void execute() throws Exception {
        try (Timer timer = new Timer()) {
            System.err.println("Writing " + format.description() + " files from queries");

            Status status = new Status();

            ExecutorService taskExecutor = Executors.newFixedThreadPool(concurrencyConfig.concurrency());

            for (int index = 1; index <= concurrencyConfig.concurrency(); index++) {
                QueryTask queryTask = new QueryTask(queries, queryClient, directories, format, status, index);
                taskExecutor.execute(queryTask);
            }

            taskExecutor.shutdown();

            try {
                taskExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
    }
}

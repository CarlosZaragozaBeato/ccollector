package com.zensyra.collector.core.sync;

import java.util.List;

public interface DataCollector {

    IntegrationSource source();
    List<SyncJob> jobs();
}

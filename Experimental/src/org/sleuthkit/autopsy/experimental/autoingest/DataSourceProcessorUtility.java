/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
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
package org.sleuthkit.autopsy.experimental.autoingest;

import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.datasourceprocessors.AutoIngestDataSourceProcessor;
import org.sleuthkit.autopsy.datasourceprocessors.AutoIngestDataSourceProcessor.AutoIngestDataSourceProcessorException;

/**
 * A utility class to find Data Source Processors
 */
class DataSourceProcessorUtility {
    
    private DataSourceProcessorUtility() {        
    }

    /**
     * A utility method to find all Data Source Processors (DSP) that are able
     * to process the input data source. Only the DSPs that implement
     * AutoIngestDataSourceProcessor interface are used.
     *
     * @param dataSourcePath Full path to the data source
     * @return Hash map of all DSPs that can process the data source along with
     * their confidence score
     * @throws
     * org.sleuthkit.autopsy.datasourceprocessors.AutoIngestDataSourceProcessor.AutoIngestDataSourceProcessorException
     */
    static Map<AutoIngestDataSourceProcessor, Integer> getDataSourceProcessor(Path dataSourcePath) throws AutoIngestDataSourceProcessorException {

        // lookup all AutomatedIngestDataSourceProcessors 
        Collection<? extends AutoIngestDataSourceProcessor> processorCandidates = Lookup.getDefault().lookupAll(AutoIngestDataSourceProcessor.class);

        Map<AutoIngestDataSourceProcessor, Integer> validDataSourceProcessorsMap = new HashMap<>();
        for (AutoIngestDataSourceProcessor processor : processorCandidates) {
            int confidence = processor.canProcess(dataSourcePath);
            if (confidence > 0) {
                validDataSourceProcessorsMap.put(processor, confidence);
            }
        }

        return validDataSourceProcessorsMap;
    }
}

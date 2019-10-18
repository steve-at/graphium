/**
 * Copyright © 2019 Salzburg Research Forschungsgesellschaft (graphium@salzburgresearch.at)
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
package at.srfg.graphium.lanelet2import.service.impl;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

import org.openstreetmap.osmosis.core.task.v0_6.RunnableSource;
import org.openstreetmap.osmosis.core.task.v0_6.Sink;
import org.openstreetmap.osmosis.core.task.v0_6.SinkSource;
import org.openstreetmap.osmosis.pbf2.v0_6.PbfReader;
import org.openstreetmap.osmosis.xml.common.CompressionMethod;
import org.openstreetmap.osmosis.xml.v0_6.XmlReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import at.srfg.graphium.io.adapter.IAdapter;
import at.srfg.graphium.io.adapter.ISegmentAdapter;
import at.srfg.graphium.io.adapter.impl.GraphVersionMetadata2GraphVersionMetadataDTOAdapter;
import at.srfg.graphium.io.adapter.impl.WaySegment2SegmentDTOAdapter;
import at.srfg.graphium.io.adapter.registry.ISegmentAdapterRegistry;
import at.srfg.graphium.io.adapter.registry.impl.SegmentAdapterRegistryImpl;
import at.srfg.graphium.io.dto.IGraphVersionMetadataDTO;
import at.srfg.graphium.io.dto.IWaySegmentDTO;
import at.srfg.graphium.io.outputformat.ISegmentOutputFormatFactory;
import at.srfg.graphium.io.outputformat.IWayGraphOutputFormat;
import at.srfg.graphium.io.outputformat.IWayGraphOutputFormatFactory;
import at.srfg.graphium.io.outputformat.impl.jackson.GenericJacksonSegmentOutputFormatFactoryImpl;
import at.srfg.graphium.io.outputformat.impl.jackson.GenericJacksonWayGraphOutputFormatFactoryImpl;
import at.srfg.graphium.lanelet2import.adapter.LaneletsAdapter;
import at.srfg.graphium.lanelet2import.model.IImportConfig;
import at.srfg.graphium.lanelet2import.reader.EntitySink;
import at.srfg.graphium.model.IHDWaySegment;
import at.srfg.graphium.model.IWayGraphVersionMetadata;
import at.srfg.graphium.model.IWaySegment;
import at.srfg.graphium.model.impl.WayGraphVersionMetadata;
import at.srfg.graphium.model.management.impl.Source;

/**
 * @author mwimmer
 *
 */
public class LaneletImporterService {
	
	private static Logger log = LoggerFactory.getLogger(LaneletImporterService.class);

    private IWayGraphOutputFormatFactory<IWaySegment> outputFormatFactory;
    private LaneletsAdapter laneletsAdapter;
    
    public LaneletImporterService() {
    	
    	IAdapter<IGraphVersionMetadataDTO, IWayGraphVersionMetadata> adapter = 
    			new GraphVersionMetadata2GraphVersionMetadataDTOAdapter();
    	ISegmentAdapterRegistry<IWaySegmentDTO, IWaySegment> adapterRegistry = 
    			new SegmentAdapterRegistryImpl<IWaySegmentDTO, IWaySegment>();
    	
    	WaySegment2SegmentDTOAdapter<IWaySegmentDTO, IWaySegment> waySegmentAdapter = 
    			new WaySegment2SegmentDTOAdapter<IWaySegmentDTO, IWaySegment>();
    	List<ISegmentAdapter<IWaySegmentDTO, IWaySegment>> adapters =
    			new ArrayList<ISegmentAdapter<IWaySegmentDTO,IWaySegment>>();
    	adapters.add(waySegmentAdapter);
    	adapterRegistry.setAdapters(adapters);
    	
    	ISegmentOutputFormatFactory<IWaySegment> segmentOutputFormatFactory = 
    			new GenericJacksonSegmentOutputFormatFactoryImpl<IWaySegment>(adapterRegistry);
    	
    	this.outputFormatFactory = 
    			new GenericJacksonWayGraphOutputFormatFactoryImpl<IWaySegment>(segmentOutputFormatFactory,adapter);
    	
    	laneletsAdapter = new LaneletsAdapter();
    }

    // TODO: asynchrone Verarbeitung umsetzen!
    
	public void importOsm(IImportConfig config) throws Exception {
        log.info("Start converting Lanelet file for graph " + config.getGraphName() + " in version " + config.getVersion() + "...");

        // read OSM model and identify end nodes and nodes for segmentation task
        log.info("Start reading PBF for identifiying segmentation nodes...");
//        long startTime = System.currentTimeMillis();

//        SinkSource wayTagFilter = createWayTagFilter(config);
//        SinkSource relationTagFilter = createRelationTagFilter();

        EntitySink entitySink = new EntitySink();
//        readOsm(segmentationNodesSink, wayTagFilter, config, false);
        readOsm(entitySink, null, config, false);

//        List<IHDRegulatoryElement> hdRegulatoryElements = adaptRegulatoryElements(entitySink);
        List<IHDWaySegment> hdWaySegments = adaptLanelets(entitySink);
//        collectRegulatoryElements(hdWaySegment, hdRegulatoryElements);
        
        log.info(hdWaySegments.size() + " segments adapted");
        
        FileOutputStream stream = null;
        IWayGraphOutputFormat<IWaySegment> outputFormat = null;
       
        try {
			stream = new FileOutputStream(config.getOutputDir() + "/" + config.getGraphName() + "_" + config.getVersion() + ".json");
	        outputFormat = outputFormatFactory.getWayGraphOutputFormat(stream);
	        outputFormat.serialize(this.getVersionMetadata(config, hdWaySegments.size()));
	        
	        for (IHDWaySegment hdSegment : hdWaySegments) {
	        	outputFormat.serialize(hdSegment);
	        }
	        
        } catch (Exception th) {
            throw th;
        } finally {
        	if (outputFormat != null) {
        		try {
        			outputFormat.close();
				} catch (Exception e) {
					log.warn(e.getMessage(), e);
				}
        	}
        	
        	if (stream != null) {
        		try {
					stream.close();
				} catch (Exception e) {
					log.warn(e.getMessage(), e);
				}
        	}
        }

        log.info("Finished converting Lanelet 2 file");
        
	}
        
	private List<IHDWaySegment> adaptLanelets(EntitySink entitySink) {
		return laneletsAdapter.adaptLanelets(entitySink.getRelations(),
											 entitySink.getWays(),
											 entitySink.getNodes());
	}
	
//	private List<IHDRegulatoryElement> adaptRegulatoryElements(EntitySink entitySink) {
//		
//		return null;
//	}

//	private SinkSource createWayTagFilter(IImportConfig config) {
//        Set<String> keys = new HashSet<>();
//        Map<String, Set<String>> keyValues = new HashMap<String, Set<String>>();
//		return new TagFilter("accept-way", keys, keyValues);
//	}
//	
//	private SinkSource createRelationTagFilter() {
//        Set<String> keys = new HashSet<String>();
//        Map<String, Set<String>> keyValues = new HashMap<String, Set<String>>();
//        Set<String> restrictionValues = new HashSet<String>();
//        restrictionValues.add("restriction"); 
//        keyValues.put("type", restrictionValues);
//        return new TagFilter("accept-relation", keys, keyValues);
//	}

	private Thread readOsm(Sink sink, SinkSource filter, IImportConfig config, boolean async) {
        Sink readerSink;
        if (filter == null) {
        	readerSink = sink;
        } else {
            filter.setSink(sink);
        	readerSink = filter;
        }
        
//        File boundsFile = null;
//        if (config.getBoundsFile() != null) {
//        	log.info("found polygon definition for geographic filtering");
//        	// PolygonFilter -> Tagfilter -> Sink
//        	boundsFile = new File(config.getBoundsFile());
//        	PolygonFilter polygonFilterSink = new PolygonFilter(IdTrackerType.Dynamic, boundsFile, true, false, false, false);
//        	
//        	polygonFilterSink.setSink(sink);
//        	filter.setSink(polygonFilterSink);
//            readerSink = filter;
//        } else {
//        	// Tagfilter -> Sink
//        	readerSink = filter;
//        }
        		
        RunnableSource reader;
        if (config.getInputFile().endsWith(".pbf")) {
        	reader = new PbfReader(new File(config.getInputFile()), config.getWorkerThreads());
        } else {
        	CompressionMethod compressionMethod;
        	if (config.getInputFile().endsWith(".gz")) {
        		compressionMethod = CompressionMethod.GZip;
        	} else if (config.getInputFile().endsWith(".bz2")) {
        		compressionMethod = CompressionMethod.BZip2;
        	} else {
        		compressionMethod = CompressionMethod.None;
        	}
//        	reader = new FastXmlReader(new File(config.getInputFile()), false, compressionMethod);
        	reader = new XmlReader(new File(config.getInputFile()), false, compressionMethod);
        }
        	
        reader.setSink(readerSink);
        
        if (async) {
	        Thread readerThread = new Thread(reader, "Lanelet File Reader");
	        readerThread.start();
	        return readerThread;
        } else {
        	reader.run();
        	return null;
        }
	}
	
    private IWayGraphVersionMetadata getVersionMetadata(IImportConfig config, int segmentsCount) {
        IWayGraphVersionMetadata metadata = new WayGraphVersionMetadata();
        metadata.setGraphName(config.getGraphName());
        metadata.setVersion(config.getVersion());
        metadata.setValidFrom(config.getValidFrom());
        metadata.setValidTo(config.getValidTo());
        metadata.setSource(new Source(3, "Lanelet 2"));
        metadata.setSegmentsCount(segmentsCount);
        metadata.setConnectionsCount(-1);
        return metadata;
    }

}
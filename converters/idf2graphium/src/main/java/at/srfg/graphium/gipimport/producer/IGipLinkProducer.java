/**
 * Copyright © 2017 Salzburg Research Forschungsgesellschaft (graphium@salzburgresearch.at)
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
package at.srfg.graphium.gipimport.producer;

import java.util.concurrent.BlockingQueue;

import at.srfg.graphium.gipimport.model.IDFMetadata;
import at.srfg.graphium.gipimport.model.IImportConfigIdf;
import at.srfg.graphium.model.IBaseSegment;

public interface IGipLinkProducer<T extends IBaseSegment> extends Runnable {
	
	Thread produceLinks(BlockingQueue<T> queue,
			IImportConfigIdf config, IDFMetadata metadata);
	
	boolean isReady();

	

}
/**
 * Copyright 2018 Ricoh Company, Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.theta360.vrmediaconnection;

import org.fourthline.cling.support.contentdirectory.AbstractContentDirectoryService;
import org.fourthline.cling.support.contentdirectory.ContentDirectoryErrorCode;
import org.fourthline.cling.support.contentdirectory.ContentDirectoryException;
import org.fourthline.cling.support.contentdirectory.DIDLParser;
import org.fourthline.cling.support.model.BrowseFlag;
import org.fourthline.cling.support.model.BrowseResult;
import org.fourthline.cling.support.model.DIDLContent;
import org.fourthline.cling.support.model.DIDLObject;
import org.fourthline.cling.support.model.SortCriterion;
import org.fourthline.cling.support.model.container.Container;
import org.fourthline.cling.support.model.item.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ContentDirectoryService extends AbstractContentDirectoryService {

    private static final Logger logger = LoggerFactory.getLogger(ContentDirectoryService.class);

    private Contents contents;

    public ContentDirectoryService(Contents contents) {
        this.contents = contents;
    }

    @Override
    public BrowseResult browse(String objectID, BrowseFlag browseFlag,
                               String filter,
                               long firstResult, long maxResults,
                               SortCriterion[] orderby) throws ContentDirectoryException {

        logger.debug("browse objectID: " + objectID);

        try {
            DIDLContent didl = new DIDLContent();

            ContentElement element = contents.getContentElement(objectID);
            if (element == null) {
                logger.warn("failed to get contentElement. id [{}] is not found.", objectID);
                return new BrowseResult("", 0, 0);
            }

            DIDLObject didlObject = element.getDIDLObject();
            if (didlObject == null) {
                logger.warn("failed to get didlObject.");
                return new BrowseResult("", 0, 0);
            }

            if (didlObject instanceof Item) {
                didl.addItem((Item) didlObject);
                return new BrowseResult(new DIDLParser().generate(didl), 1, 1);
            }

            if (browseFlag == BrowseFlag.METADATA) {
                didl.addContainer((Container) didlObject);
                return new BrowseResult(new DIDLParser().generate(didl), 1, 1);
            }

            for (Container container : ((Container) didlObject).getContainers()) {
                didl.addContainer(container);
            }

            for (Item item : ((Container) didlObject).getItems()) {
                didl.addItem(item);
            }

            String xml = new DIDLParser().generate(didl);
            logger.debug(xml);
            return new BrowseResult(xml,
                    ((Container) didlObject).getChildCount(),
                    ((Container) didlObject).getChildCount());

        } catch (Exception ex) {
            throw new ContentDirectoryException(
                    ContentDirectoryErrorCode.CANNOT_PROCESS,
                    ex.toString()
            );
        }
    }

    @Override
    public BrowseResult search(String containerId,
                               String searchCriteria, String filter,
                               long firstResult, long maxResults,
                               SortCriterion[] orderby) throws ContentDirectoryException {

        // You can override this method to implement searching!
        return super.search(containerId, searchCriteria, filter, firstResult, maxResults, orderby);
    }
}

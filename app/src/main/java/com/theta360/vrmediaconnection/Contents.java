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

import org.fourthline.cling.support.model.DIDLObject;
import org.fourthline.cling.support.model.WriteStatus;
import org.fourthline.cling.support.model.container.Container;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Contents {

    private static final Logger logger = LoggerFactory.getLogger(Contents.class);

    public static final String ROOT_ID = "0";
    public static final String VIDEO_ID = "1";
    public static final String IMAGE_ID = "2";
    public static final String CORRECTION_ID = "3";
    public static final String ORIGINAL_VIDEO_ID = "3-1";
    public static final String CORRECTED_VIDEO_ID = "3-2";

    private final Map<String, ContentElement> contentMap = new ConcurrentHashMap<>();

    public Contents() {
        Container root = new Container();
        root.setId(ROOT_ID);
        root.setClazz(new DIDLObject.Class("object.container"));
        root.setParentID("-1");
        root.setTitle("root");
        root.setCreator(Constants.Content.CREATOR);
        root.setRestricted(true);
        root.setSearchable(true);
        root.setWriteStatus(WriteStatus.NOT_WRITABLE);
        root.setChildCount(0);
        ContentElement element = new ContentElement(ROOT_ID, root);

        contentMap.clear();
        contentMap.putIfAbsent(ROOT_ID, element);
    }

    public Container getRootContainer() {
        ContentElement element = contentMap.get(ROOT_ID);
        if (element == null) {
            logger.error("failed to get root element.");
            throw new RuntimeException("failed to get root element.");
        }
        return (Container) element.getDIDLObject();
    }

    public ContentElement getContentElement(String id) {
        return contentMap.get(id);
    }

    public void addContentElement(String id, ContentElement contentElement) {
        contentMap.putIfAbsent(id, contentElement);
    }

    public void removeContentElement(String id) {
        contentMap.remove(id);
    }

}

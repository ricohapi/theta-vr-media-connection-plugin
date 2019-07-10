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

import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.provider.MediaStore;

import com.theta360.vrmediaconnection.network.HttpConnector;

import org.fourthline.cling.support.model.DIDLObject;

public class ContentElement {

    private String id;
    private DIDLObject didlObject;
    private String title;
    private String localUri;
    private String virtualUri;
    private String localPath;
    private String mimeType;
    private long width;
    private long height;

    private HttpConnector connector;


    public ContentElement(String id, DIDLObject didlObject) {
        this.id = id;
        this.didlObject = didlObject;
        this.title = "";
        this.localUri = "";
        this.virtualUri = "";
        this.localPath = "";
        this.mimeType = "";

        this.connector = new HttpConnector(Constants.Net.LOCAL_URI);
    }

    @Override
    public String toString() {
        return "id=" + id +
                ", title=" + title +
                ", localUri=" + localUri +
                ", virtualUri=" + virtualUri +
                ", localPath=" + localPath +
                ", mimeType=" + mimeType +
                ", width=" + width +
                ", height=" + height;
    }

    public String getId() { return this.id; }

    public DIDLObject getDIDLObject() { return this.didlObject; }

    public String getTitle() { return this.title; }

    public String getLocalUri() { return this.localUri; }

    public String getVirtualUri() { return this.virtualUri; }

    public String getLocalPath() { return this.localPath; }

    public String getMimeType() { return this.mimeType; }

    public long getWidth() { return this.width; }

    public long getHeight() { return this.height; }


    public void setTitle(String title) { this.title = title; }

    public void setLocalUri(String localUri) { this.localUri = localUri; }

    public void setVirtualUri(String virtualUri) { this.virtualUri = virtualUri; }

    public void setLocalPath(String localPath) { this.localPath = localPath; }

    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public void setSize(long width, long height) {
        this.width = width;
        this.height = height;
    }

    public Bitmap createThumbnail() {
        if (isCorrectedContent()) {
            return ThumbnailUtils.createVideoThumbnail(localPath, MediaStore.Video.Thumbnails.MINI_KIND);
        } else {
            return connector.getThumb(localUri);
        }
    }

    private boolean isCorrectedContent() {
        if (id.indexOf(Constants.Content.CORRECTED_VIDEO_ID_PREFIX) == 0) {
            return true;
        }
        return false;
    }
}

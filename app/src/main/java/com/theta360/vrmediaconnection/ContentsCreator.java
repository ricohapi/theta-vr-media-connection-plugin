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

import android.content.Context;
import android.os.AsyncTask;
import android.provider.MediaStore;

import com.theta360.vrmediaconnection.network.HttpConnector;
import com.theta360.vrmediaconnection.network.ImageInfo;

import org.fourthline.cling.support.model.DIDLObject;
import org.fourthline.cling.support.model.Res;
import org.fourthline.cling.support.model.WriteStatus;
import org.fourthline.cling.support.model.container.Container;
import org.fourthline.cling.support.model.dlna.DLNAProtocolInfo;
import org.fourthline.cling.support.model.item.ImageItem;
import org.fourthline.cling.support.model.item.Item;
import org.fourthline.cling.support.model.item.VideoItem;
import org.seamless.util.MimeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ContentsCreator extends AsyncTask<Void, Void, Void> implements ModeListener {

    private static final Logger logger = LoggerFactory.getLogger(ContentsCreator.class);

    private Context context;
    private String ipAddress;
    private Contents contents;
    private List<ImageInfo> imageInfoList;
    private boolean isCorrectionMode;

    public ContentsCreator(Context context, String ipAddress, Contents contents, boolean isCorrectionMode) {
        this.context = context;
        this.ipAddress = ipAddress;
        this.contents = contents;
        this.isCorrectionMode = isCorrectionMode;
    }

    @Override
    protected Void doInBackground(Void... params) {

        HttpConnector connector = new HttpConnector(Constants.Net.LOCAL_URI);
        this.imageInfoList = connector.getList();

        Container root = contents.getRootContainer();
        Container videoContainer = createContainer(Contents.VIDEO_ID, root, Constants.Content.DISPLAY_VIDEO_DIR_NAME);
        Container imageContainer = createContainer(Contents.IMAGE_ID, root, Constants.Content.DISPLAY_IMAGE_DIR_NAME);
        Container originalVideoContainer = null;

        if (isCorrectionMode) {
            Container correctedContainer = createContainer(Contents.CORRECTION_ID, root, Constants.Content.DISPLAY_CORRECTION_DIR_NAME);
            originalVideoContainer = createContainer(Contents.ORIGINAL_VIDEO_ID, correctedContainer, Constants.Content.DISPLAY_ORIGINAL_VIDEO_DIR_NAME);
            createContainer(Contents.CORRECTED_VIDEO_ID, correctedContainer, Constants.Content.DISPLAY_CORRECTED_VIDEO_DIR_NAME);
        }

        for (ImageInfo info : imageInfoList) {
            if (ImageInfo.FILE_FORMAT_CODE_EXIF_MPEG.equals(info.getFileFormat())) {
                createVideoContents(info, videoContainer);
                if (isCorrectionMode) {
                    createCorrectedFolder(info, originalVideoContainer);
                }
            } else {
                createImageContents(info, imageContainer);
            }
        }

        return null;
    }

    @Override
    public void updateStatus(boolean isCorrectionMode) {
        if (isCorrectionMode) {
            recreateCorrectionFolder();
        } else {
            removeCorrectionContainer();
            recursiveDeleteFile(new File(Constants.Storage.CORRECTED_DIR));
        }
    }

    public void createCorrectedVideoContents(ContentElement originalElem) {

        ContentElement parentElement = contents.getContentElement(Contents.CORRECTED_VIDEO_ID);
        if (parentElement == null) {
            logger.error("contents is not contain [{}]", Contents.CORRECTED_VIDEO_ID);
            throw new RuntimeException("contents is not contain [" + Contents.CORRECTED_VIDEO_ID + "]");
        }
        Container parent = (Container) parentElement.getDIDLObject();

        String title = originalElem.getTitle();
        // The postfix "_360" should be added to the title.
        // By this postfix, Oculus Go can recognize it as the spherical video.
        String playerTitle = title + "_360";
        String localUri = originalElem.getLocalUri();

        String originalId = originalElem.getId().substring(Constants.Content.ORIGINAL_VIDEO_ID_PREFIX.length());
        String id = Constants.Content.CORRECTED_VIDEO_ID_PREFIX + originalId;

        String virtualUri = createVirtualUri(id);
        String thumbnailUri = createThumbnailUri(virtualUri);
        String localPath = getCorrectedFilePath(originalId);

        File file = new File(localPath);
        long size = file.length();

        Res res = new Res(new MimeType(
                Constants.MimeType.MP4.substring(0, Constants.MimeType.MP4.indexOf('/')),
                Constants.MimeType.MP4.substring(Constants.MimeType.MP4.indexOf('/') + 1)),
                size,
                virtualUri);
        res.setResolution((int) originalElem.getWidth(), (int) originalElem.getHeight());
        res.setProtocolInfo(new DLNAProtocolInfo(Constants.ProtocolInfo.MP4));

        VideoItem videoItem = new VideoItem(id, parent.getId(), playerTitle, Constants.Content.CREATOR, res);
        videoItem.addProperty(new DIDLObject.Property.UPNP.ALBUM_ART_URI(URI.create(thumbnailUri)));
        videoItem.setRestricted(true);
        parent.addItem(videoItem);
        parent.setChildCount(parent.getChildCount() + 1);

        ContentElement element = new ContentElement(id, videoItem);
        element.setTitle(title);
        element.setLocalUri(localUri);
        element.setVirtualUri(virtualUri);
        element.setLocalPath(localPath);
        element.setMimeType(Constants.MimeType.MP4);
        element.setSize(originalElem.getWidth(), originalElem.getHeight());

        contents.addContentElement(id, element);

        // update the size of dummy file.
        final long length;
        try {
            length = context.getResources().getAssets().openFd(Constants.Content.DUMMY_FILE_DONE).getLength();
        } catch (IOException e) {
            logger.error("failed to read file length of {}.", Constants.Content.DUMMY_FILE_DONE);
            throw new RuntimeException("failed to read file length of " + Constants.Content.DUMMY_FILE_DONE, e);
        }
        originalElem.getDIDLObject().getFirstResource().setSize(length);

        logger.debug("createContent: {}", element);
    }

    private Container createContainer(String id, Container parent, String title) {
        Container container = new Container();
        container.setClazz(new DIDLObject.Class("object.container"));
        container.setId(id);
        container.setParentID(parent.getId());
        container.setTitle(title);
        container.setRestricted(true);
        container.setWriteStatus(WriteStatus.NOT_WRITABLE);
        container.setChildCount(0);

        parent.addContainer(container);
        parent.setChildCount(parent.getChildCount() + 1);
        contents.addContentElement(id, new ContentElement(id, container));

        return container;
    }

    private void createVideoContents(ImageInfo info, Container parent) {

        if (ImageInfo.PROJECTION_TYPE_DUALFISH.equals(info.getProjectionType())) {
            return;
        }
        String title = info.getFileName().substring(0, info.getFileName().lastIndexOf('.'));
        // The postfix "_360" should be added to the title.
        // By this postfix, Oculus Go can recognize it as the spherical video.
        String playerTitle = title + "_360";
        String localUri = info.getFileId();
        String id = createId(localUri);
        String virtualUri = createVirtualUri(id);
        String thumbnailUri = createThumbnailUri(virtualUri);
        long size = info.getFileSize();

        Res res = new Res(new MimeType(
                Constants.MimeType.MP4.substring(0, Constants.MimeType.MP4.indexOf('/')),
                Constants.MimeType.MP4.substring(Constants.MimeType.MP4.indexOf('/') + 1)),
                size,
                virtualUri);
        res.setResolution(info.getWidth(), info.getHeight());
        res.setProtocolInfo(new DLNAProtocolInfo(Constants.ProtocolInfo.MP4));

        VideoItem videoItem = new VideoItem(id, parent.getId(), playerTitle, Constants.Content.CREATOR, res);
        videoItem.addProperty(new DIDLObject.Property.UPNP.ALBUM_ART_URI(URI.create(thumbnailUri)));
        videoItem.setRestricted(true);
        parent.addItem(videoItem);
        parent.setChildCount(parent.getChildCount() + 1);

        ContentElement element = new ContentElement(id, videoItem);
        element.setTitle(title);
        element.setLocalUri(localUri);
        element.setVirtualUri(virtualUri);
        element.setLocalPath(getLocalPath(localUri));
        element.setMimeType(Constants.MimeType.MP4);
        element.setSize(info.getWidth(), info.getHeight());
        contents.addContentElement(id, element);

        logger.debug("createContent: {}", element);
    }

    private void createImageContents(ImageInfo info, Container parent) {

        String fileName = info.getFileName();
        String ext = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        if (!ext.equals("jpg")) {
            return;
        }
        String title = fileName.substring(0, fileName.lastIndexOf('.'));
        String localUri = info.getFileId();

        // The extension should be added to the title of image contents.
        // With Oculus Go, unless the extension is added, the download images from THETA cannot be viewed.
        String id = createId(localUri) + ".JPG";
        String virtualUri = createVirtualUri(id);
        String thumbnailUri = createThumbnailUri(virtualUri);
        long size = info.getFileSize();

        Res res = new Res(new MimeType(
                Constants.MimeType.JPEG.substring(0, Constants.MimeType.JPEG.indexOf('/')),
                Constants.MimeType.JPEG.substring(Constants.MimeType.JPEG.indexOf('/') + 1)),
                size,
                virtualUri);
        res.setResolution(info.getWidth(), info.getHeight());
        res.setProtocolInfo(new DLNAProtocolInfo(Constants.ProtocolInfo.JPEG));

        ImageItem imageItem = new ImageItem(id, parent.getId(), title, Constants.Content.CREATOR, res);
        imageItem.addProperty(new DIDLObject.Property.UPNP.ALBUM_ART_URI(URI.create(thumbnailUri)));
        imageItem.setRestricted(true);
        parent.addItem(imageItem);
        parent.setChildCount(parent.getChildCount() + 1);

        ContentElement element = new ContentElement(id, imageItem);
        element.setTitle(title);
        element.setLocalUri(localUri);
        element.setVirtualUri(virtualUri);
        element.setLocalPath(getLocalPath(localUri));
        element.setMimeType(Constants.MimeType.JPEG);
        element.setSize(info.getWidth(), info.getHeight());
        contents.addContentElement(id, element);

        logger.debug("createContent: {}", element);
    }

    private void createCorrectedFolder(ImageInfo info, Container parent) {

        String title = info.getFileName().substring(0, info.getFileName().lastIndexOf('.'));
        String playerTitle = title + "_360";
        String localUri = info.getFileId();
        String preCorrectedId = createId(localUri);
        String id = Constants.Content.ORIGINAL_VIDEO_ID_PREFIX + preCorrectedId;
        String virtualUri = createVirtualUri(id);
        String thumbnailUri = createThumbnailUri(virtualUri);
        String localPath = getLocalPath(localUri);

        final long length;
        try {
            length = context.getResources().getAssets().openFd(Constants.Content.DUMMY_FILE_PROCESSING).getLength();
        } catch (IOException e) {
            logger.error("failed to read file length of {}.", Constants.Content.DUMMY_FILE_PROCESSING);
            throw new RuntimeException("failed to read file length of " + Constants.Content.DUMMY_FILE_PROCESSING, e);
        }

        // create dummy resource
        Res res = new Res(new MimeType(
                Constants.MimeType.MP4.substring(0, Constants.MimeType.MP4.indexOf('/')),
                Constants.MimeType.MP4.substring(Constants.MimeType.MP4.indexOf('/') + 1)),
                length,
                virtualUri);
        res.setResolution(1920, 960);
        res.setProtocolInfo(new DLNAProtocolInfo(Constants.ProtocolInfo.MP4));

        VideoItem item = new VideoItem(id, parent.getId(), playerTitle, Constants.Content.CREATOR, res);
        item.addProperty(new DIDLObject.Property.UPNP.ALBUM_ART_URI(URI.create(thumbnailUri)));
        item.setRestricted(true);
        parent.addItem(item);
        parent.setChildCount(parent.getChildCount() + 1);

        ContentElement element = new ContentElement(id, item);
        element.setTitle(title);
        element.setLocalUri(localUri);
        element.setVirtualUri(virtualUri);
        element.setLocalPath(localPath);
        element.setMimeType(Constants.MimeType.MP4);
        element.setSize(info.getWidth(), info.getHeight());

        if (existsCorrectedData(preCorrectedId)) {
            createCorrectedVideoContents(element);
        }

        contents.addContentElement(id, element);

        logger.debug("createContent: {}", element);
    }

    private void recreateCorrectionFolder() {
        Container root = contents.getRootContainer();
        Container correctedContainer = createContainer(Contents.CORRECTION_ID, root, Constants.Content.DISPLAY_CORRECTION_DIR_NAME);
        Container originalVideoContainer = createContainer(Contents.ORIGINAL_VIDEO_ID, correctedContainer, Constants.Content.DISPLAY_ORIGINAL_VIDEO_DIR_NAME);
        createContainer(Contents.CORRECTED_VIDEO_ID, correctedContainer, Constants.Content.DISPLAY_CORRECTED_VIDEO_DIR_NAME);

        for (ImageInfo info : this.imageInfoList) {
            if (ImageInfo.FILE_FORMAT_CODE_EXIF_MPEG.equals(info.getFileFormat())) {
                createCorrectedFolder(info, originalVideoContainer);
            }
        }
        logger.info("recreated CorrectionFolder.");
    }

    private void removeCorrectionContainer() {
        clearChildContent(Contents.CORRECTED_VIDEO_ID);
        clearChildContent(Contents.ORIGINAL_VIDEO_ID);
        clearChildContent(Contents.CORRECTION_ID);
        contents.removeContentElement(Contents.CORRECTION_ID);

        Container root = contents.getRootContainer();
        List<Container> containers = root.getContainers();
        for (Container container : containers) {
            if (container.getTitle().equals(Constants.Content.DISPLAY_CORRECTION_DIR_NAME))
                containers.remove(container);
        }
        root.setChildCount(2);

        logger.info("removed CorrectionContainer.");
    }

    private void clearChildContent(String containerId) {
        logger.debug("clearChildContent(): {}", containerId);
        ContentElement element = contents.getContentElement(containerId);
        if (element == null) {
            logger.error("contents is not contain [{}]", containerId);
            throw new RuntimeException("contents is not contain [" + containerId + "]");
        }

        Container container = (Container) element.getDIDLObject();
        List<Item> items = container.getItems();
        for (Item item : items) {
            contents.removeContentElement(item.getId());
        }
        if (items != null) items.clear();

        List<Container> childContainers = container.getContainers();
        for (Container childContainer : childContainers) {
            contents.removeContentElement(childContainer.getId());
        }
        if (childContainers != null) childContainers.clear();

        container.setChildCount(0);
    }

    private void recursiveDeleteFile(File file) {
        if (!file.exists()) return;

        if (file.isDirectory()) {
            for (File child : file.listFiles()) {
                recursiveDeleteFile(child);
            }
        }

        file.delete();
        updateDatabase(file);
        logger.debug("delete: {}", file.getPath());
    }

    private String baseName(String localUri) {
        Matcher matcher = Pattern.compile("/\\d{3}RICOH.*").matcher(localUri);
        if (!matcher.find()) return "";
        return matcher.group();
    }

    private String createId(String localUri) {
        String baseName = baseName(localUri);
        if (baseName.isEmpty()) return localUri;
        return baseName.substring(0, baseName.lastIndexOf('.'));
    }

    private String createVirtualUri(String id) {
        return "http://" + this.ipAddress + ":" + Constants.Net.PORT + id;
    }

    private String createThumbnailUri(String virtualUri) {
        return virtualUri + "?type=thumb";
    }

    private String getLocalPath(String localUri) {
        String baseName = baseName(localUri);
        if (baseName.isEmpty()) return localUri;
        return Constants.Storage.DCIM + baseName;
    }

    private String getCorrectedFilePath(String originalId) {
        return Constants.Storage.CORRECTED_DIR + originalId + Constants.Content.CORRECTED_SUFFIX + ".mp4";
    }

    private boolean existsCorrectedData(String id) {
        String correctedData = getCorrectedFilePath(id);
        File file = new File(correctedData);
        return file.exists();
    }

    private void updateDatabase(File file) {
        context.getContentResolver().delete(
                MediaStore.Files.getContentUri("external"),
                MediaStore.Files.FileColumns.DATA + " like ?",
                new String[]{file.getPath() + "%"}
        );
    }
}

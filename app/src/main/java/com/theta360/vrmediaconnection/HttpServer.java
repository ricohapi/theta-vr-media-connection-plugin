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
import android.graphics.Bitmap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

public class HttpServer extends NanoHTTPD {

    private static final Logger logger = LoggerFactory.getLogger(HttpServer.class);

    private Context context;
    private String ipAddress;
    private Contents contents;
    private VideoConverter videoConverter;

    public HttpServer(Context context, String ipAddress, Contents contents, VideoConverter videoConverter) {
        super(Constants.Net.PORT);
        this.context = context;
        this.ipAddress = ipAddress;
        this.contents = contents;
        this.videoConverter = videoConverter;
        logger.debug("built server: http://{}:{}", this.ipAddress, Constants.Net.PORT);
    }

    @Override
    public Response serve(IHTTPSession session) {
        Method method = session.getMethod();
        switch (method) {
            case GET:
                return this.serveFile(session);

            default:
                return newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED,
                        Constants.MimeType.PLAINTEXT,
                        "Method [" + method + "] is not allowed.");
        }
    }

    private Response serveFile(IHTTPSession session) {
        String uri = session.getUri();
        Map<String, String> headers = session.getHeaders();
        Map<String, List<String>> params = session.getParameters();
        logger.debug("uri:{} headers:{} params:{}", uri, headers, params);

        // thumbnail
        if (!params.isEmpty()) {
            List<String> types = params.get("type");
            if (types.contains("thumb")) {
                return serveThumbnail(uri);
            }
        }

        String range = null;
        for (String key : headers.keySet()) {
            if (key.equals("range")) {
                range = headers.get(key);
            }
        }

        try {

            if (isCorrectionOriginalContent(uri)) {
                return serveDummyResponse(uri);
            }

            if (range == null) {
                return getFullResponse(uri);
            } else {
                return getPartialResponse(uri, range);
            }

        } catch (FileNotFoundException e) {
            logger.error("URI [{}] is not found.", uri);
            e.printStackTrace();
            return newFixedLengthResponse(Response.Status.NOT_FOUND, Constants.MimeType.PLAINTEXT, "URI [" + uri + "] is not found.");
        } catch (IOException e) {
            logger.error("can't open URI [{}].", uri);
            e.printStackTrace();
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, Constants.MimeType.PLAINTEXT, "can't open URI [" + uri + "].");
        } catch (IllegalArgumentException e) {
            logger.error("Illegal URI [{}].", uri);
            e.printStackTrace();
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, Constants.MimeType.PLAINTEXT, "Illegal URI [" + uri + "]");
        }
    }

    private Response serveThumbnail(String id) {
        ContentElement element = contents.getContentElement(id);
        if (element == null) {
            logger.warn("failed to serve thumbnail. id [{}] is not found.", id);
            return newFixedLengthResponse(Response.Status.NOT_FOUND, Constants.MimeType.PLAINTEXT, "id [" + id + "] is not found.");
        }
        Bitmap thumb = element.createThumbnail();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        thumb.compress(Bitmap.CompressFormat.JPEG, 100, baos);
        ByteArrayInputStream stream = new ByteArrayInputStream(baos.toByteArray());
        return newFixedLengthResponse(Response.Status.OK, Constants.MimeType.JPEG, stream, baos.size());
    }

    private Response serveDummyResponse(String uri) {
        ContentElement element = contents.getContentElement(uri);
        if (element == null) {
            logger.warn("failed to serve dummy response. id [{}] is not found.", uri);
            return newFixedLengthResponse(Response.Status.NOT_FOUND, Constants.MimeType.PLAINTEXT, "id [" + uri + "] is not found.");
        }

        String dummyFileName = "";
        if (existsCorrectedData(uri)) {
            dummyFileName = Constants.Content.DUMMY_FILE_DONE;
        } else {
            dummyFileName = Constants.Content.DUMMY_FILE_PROCESSING;
            this.videoConverter.push(element);
        }

        logger.debug("response dummy: {}", dummyFileName);

        final InputStream stream;
        final long length;
        try {
            stream = context.getResources().getAssets().open(dummyFileName);
            length = context.getResources().getAssets().openFd(dummyFileName).getLength();
        } catch (IOException e) {
            logger.error("failed to read file {}.", dummyFileName);
            throw new RuntimeException("failed to read file " + dummyFileName, e);
        }
        Response response = newFixedLengthResponse(Response.Status.OK, Constants.MimeType.MP4, stream, length);
        response.addHeader("Accept-Ranges", "bytes");
        response.addHeader("Cache-Control", "no-cache");
        response.addHeader("transferMode.dlna.org", "Streaming");
        response.addHeader("contentFeatures.dlna.org", "DLNA.ORG_PN=AVC_MP4_BL_CIF15_AAC_520;DLNA.ORG_OP=01;DLNA.ORG_CI=0");

        return response;
    }

    private Response getFullResponse(String uri) throws FileNotFoundException{
        File file = getInputStream(uri);
        FileInputStream stream = new FileInputStream(file);
        String mimeType = getMimeType(uri);
        Response response = newFixedLengthResponse(Response.Status.OK, mimeType, stream, file.length());
        response.addHeader("Accept-Ranges", "bytes");
        response.addHeader("Cache-Control", "no-cache");
        response.addHeader("transferMode.dlna.org", "Streaming");

        switch (mimeType) {
            case Constants.MimeType.JPEG:
                response.addHeader("contentFeatures.dlna.org", "DLNA.ORG_PN=JPEG_LRG;DLNA.ORG_OP=01;DLNA.ORG_CI=0");
                break;
            case Constants.MimeType.MP4:
                response.addHeader("contentFeatures.dlna.org", "DLNA.ORG_PN=AVC_MP4_BL_CIF15_AAC_520;DLNA.ORG_OP=01;DLNA.ORG_CI=0");
                break;
            default:
                break;
        }
        return response;
    }

    private Response getPartialResponse(String uri, String rangeHeader) throws IOException {
        File file = getInputStream(uri);
        String rangeVal = rangeHeader.trim().substring("bytes=".length());
        long totalSize = file.length();
        long start, end;
        if (rangeVal.startsWith("-")) {
            end = totalSize - 1;
            start = totalSize - 1 - Long.parseLong(rangeVal.substring("-".length()));
        } else {
            String[] range = rangeVal.split("-");
            start = Long.parseLong(range[0]);
            end = range.length > 1 ? Long.parseLong(range[1]) : totalSize - 1;
        }
        if (end > totalSize - 1) {
            end = totalSize - 1;
        }
        if (start <= end) {
            long contentLen = end - start + 1;

            FileInputStream stream = new FileInputStream(file);
            stream.skip(start);

            Response response = newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, getMimeType(uri), stream, contentLen);
            response.addHeader("Accept-Ranges", "bytes");
            response.addHeader("Cache-Control", "no-cache");
            response.addHeader("transferMode.dlna.org", "Streaming");
            response.addHeader("Content-Range", "bytes " + start + "-" + end + "/" + totalSize);

            if (Constants.MimeType.MP4.equals(getMimeType(uri))) {
                response.addHeader("contentFeatures.dlna.org", "DLNA.ORG_PN=AVC_MP4_BL_CIF15_AAC_520;DLNA.ORG_OP=01;DLNA.ORG_CI=0");
            }
            return response;

        } else {
            logger.debug("start > end  rangeHeader: {}", rangeHeader);
            return newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, Constants.MimeType.HTML, rangeHeader);
        }
    }

    private boolean isCorrectionOriginalContent(String uri) {
        if (uri.indexOf(Constants.Content.ORIGINAL_VIDEO_ID_PREFIX) == 0) {
            return true;
        }
        return false;
    }

    private File getInputStream(String uri) {
        String filePath = getFilePath(uri);
        logger.debug("getInputStream filePath: {}", filePath);
        return new File(filePath);
    }

    private String getFilePath(String id) {
        ContentElement element = contents.getContentElement(id);
        if (element == null) {
            logger.warn("failed to get file path. id [{}] is not found.", id);
            return "";
        }
        return element.getLocalPath();
    }

    private String getMimeType(String id) {
        ContentElement element = contents.getContentElement(id);
        if (element == null) {
            logger.warn("failed to get MimeType. id [{}] is not found.", id);
            return Constants.MimeType.PLAINTEXT;
        }
        return element.getMimeType();
    }

    private String getCorrectedFilePath(String originalId) {
        String fileName = Constants.Storage.CORRECTED_DIR + originalId + Constants.Content.CORRECTED_SUFFIX + ".mp4";
        return fileName;
    }

    private boolean existsCorrectedData(String id) {
        String originalId = id.substring(Constants.Content.ORIGINAL_VIDEO_ID_PREFIX.length());
        String correctedData = getCorrectedFilePath(originalId);
        File file = new File(correctedData);
        return file.exists();
    }

}

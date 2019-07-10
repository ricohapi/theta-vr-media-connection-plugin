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

import android.os.Environment;

public final class Constants {
    private Constants() {}

    public static class Net {
        public static final int PORT = 8888;
        public static final String AP_HOST = "192.168.1.1";
        public static final String LOCAL_URI = "127.0.0.1:8080";
    }

    public static class MimeType {
        public static final String PLAINTEXT = "text/plain";
        public static final String HTML = "text/html";
        public static final String JPEG = "image/jpeg";
        public static final String PNG = "image/png";
        public static final String MP4 = "video/mp4";
    }

    public static class ProtocolInfo {
        public static final String JPEG = "http-get:*:image/jpeg:DLNA.ORG_PN=JPEG_LRG;DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=00D00000000000000000000000000000";
        public static final String MP4 = "http-get:*:video/mp4:DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01500000000000000000000000000000";
    }

    public static class Storage {
        public static final String DCIM = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getPath();
        public static final String TMP_DIR = Environment.getExternalStorageDirectory().getPath() + "/Converted";
        public static final String CORRECTED_DIR = DCIM + "/VRMediaConnection";
    }

    public static class Content {
        public static final String CREATOR = "RICOH THETA";

        public static final String DISPLAY_VIDEO_DIR_NAME = "Video";
        public static final String DISPLAY_IMAGE_DIR_NAME = "Image";
        public static final String DISPLAY_CORRECTION_DIR_NAME = "Orientation Correction";
        public static final String DISPLAY_ORIGINAL_VIDEO_DIR_NAME = "Original Video";
        public static final String DISPLAY_CORRECTED_VIDEO_DIR_NAME = "Corrected Video";

        public static final String ORIGINAL_VIDEO_ID_PREFIX = "/ORIGINAL";
        public static final String CORRECTED_VIDEO_ID_PREFIX = "/CORRECTED";

        public static final String DUMMY_FILE_PROCESSING = "processing.mp4";
        public static final String DUMMY_FILE_DONE = "done.mp4";

        public static final String CORRECTED_SUFFIX = "_corrected";
    }
}

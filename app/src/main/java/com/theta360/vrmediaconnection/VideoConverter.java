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
import android.content.Intent;
import android.net.Uri;

import com.theta360.pluginlibrary.values.LedTarget;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theta4j.osc.CommandResponse;
import org.theta4j.osc.CommandState;
import org.theta4j.osc.OSCException;
import org.theta4j.webapi.ConvertVideoFormats;
import org.theta4j.webapi.Theta;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.URL;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class VideoConverter {

    private static final Logger logger = LoggerFactory.getLogger(VideoConverter.class);

    private Context context;
    private ContentsCreator contentsCreator;
    private final Theta theta = Theta.createForPlugin();
    private ExecutorService executorService;

    public VideoConverter(Context context, ContentsCreator contentsCreator) {
        this.context = context;
        this.contentsCreator = contentsCreator;
        this.executorService = Executors.newSingleThreadExecutor();
    }

    public synchronized void push(ContentElement element) {
        executorService.submit(new Task(element));
    }

    public synchronized void shutdown() {
        executorService.shutdownNow();
        logger.info("VideoConverter is shutdown.");
    }

    public class Task implements Callable<String> {

        private ConvertVideoFormats.Size size;
        private String commandId;
        private ContentElement element;
        private StatusChecker statusChecker;

        public Task(ContentElement element) {
            this.element = element;

            if (element.getWidth() == 3840) this.size = ConvertVideoFormats.Size._3840_1920;
            else                         this.size = ConvertVideoFormats.Size._1920_960;

            this.commandId = "";
            this.statusChecker = new StatusChecker(context);
        }

        @Override
        public String call() {

            try {
                if (existsCorrectedData(element.getId())) {
                    return "";
                }

                if (canProcess()) {
                    String convertedFilePath = convert();
                    copy(convertedFilePath);
                    contentsCreator.createCorrectedVideoContents(this.element);

                    // play sound
                    context.sendBroadcast(new Intent("com.theta360.plugin.ACTION_AUDIO_MOVSTOP"));
                }

                Thread.sleep(3500); // wait until LED8 is available.
                checkStorageStatus();

            } catch (InterruptedException | OSCException e) {
                logger.debug("canceled: {}", e.getMessage());

            } catch (ExecutionException | IOException e) {
                logger.error(e.getMessage());
                e.printStackTrace();
                context.sendBroadcast(new Intent("com.theta360.plugin.ACTION_ERROR_OCCURED"));
            }

            return this.commandId;
        }

        private boolean canProcess() throws InterruptedException, ExecutionException, IOException {
            File file = new File(element.getLocalPath());
            boolean isEnoughStorage = statusChecker.isEnoughStorage(file.length());
            boolean isEnoughBattery = statusChecker.isEnoughBattery();
            if (isEnoughStorage && isEnoughBattery) {
                return true;
            }
            logger.info("cannot a correction process. isEnoughStorage:{} isEnoughBattery:{}", isEnoughStorage, isEnoughBattery);
            context.sendBroadcast(new Intent("com.theta360.plugin.ACTION_ERROR_OCCURED"));
            return false;
        }

        private void checkStorageStatus() throws InterruptedException, ExecutionException, IOException {
            StatusChecker.StorageStatus storageStatus = statusChecker.getStorageStatus();
            Intent intent = null;
            switch (storageStatus) {
                case VERY_FEW:
                    intent = new Intent("com.theta360.plugin.ACTION_LED_BLINK");
                    intent.putExtra("target", LedTarget.LED8.toString());
                    intent.putExtra("period", 2000);
                    context.sendBroadcast(intent);
                    break;

                case FEW:
                    intent = new Intent("com.theta360.plugin.ACTION_LED_SHOW");
                    intent.putExtra("target", LedTarget.LED8.toString());
                    context.sendBroadcast(intent);
                    break;

                default:
                    intent = new Intent("com.theta360.plugin.ACTION_LED_HIDE");
                    intent.putExtra("target", LedTarget.LED8.toString());
                    context.sendBroadcast(intent);
                    break;
            }
        }

        private String convert() throws InterruptedException, OSCException, IOException {
            String convertedFilePath = "";

            try {
                // If there is an incomplete file at "Converted" directory,
                // sometimes the conversion process will be failure.
                // So the files inside "Converted" directory should be deleted.
                recursiveDeleteFile(new File(Constants.Storage.TMP_DIR));

                ConvertVideoFormats.Parameter params = new ConvertVideoFormats.Parameter(
                        new URL(this.element.getLocalUri()),
                        this.size,
                        ConvertVideoFormats.ProjectionType.EQUIRECTANGULAR,
                        ConvertVideoFormats.Codec.H264_MPEG4_AVC,
                        ConvertVideoFormats.TopBottomCorrectionType.APPLY
                );

                logger.info("start convert: {}", this.element.getLocalUri());
                CommandResponse<ConvertVideoFormats.Result> response = theta.convertVideoFormats(params);
                BigDecimal progress = new BigDecimal(0);
                this.commandId = response.getID();
                while (response.getState() != CommandState.DONE) {
                    progress = response.getProgress().getCompletion();
                    logger.debug("id:{} progress:{}", response.getID(), progress.toPlainString());

                    response = theta.commandStatus(response);
                    Thread.sleep(500);
                }

                URL convertedFile = response.getResult().getFileUrl();
                logger.debug("id:{} converted:{}", response.getID(), convertedFile.getPath());
                convertedFilePath = convertedFile.getPath();

            } catch (InterruptedException e) {
                if (!commandId.isEmpty()) {
                    logger.debug("id:{} cancelVideoConvert()", this.commandId);
                    theta.cancelVideoConvert();
                }
                throw new InterruptedException("canceled convert");
            }

            return convertedFilePath;
        }

        private void copy(String path) throws IOException {

            String baseName = getBaseName(path);
            String inPath = Constants.Storage.TMP_DIR + baseName;

            File outDir = new File(Constants.Storage.CORRECTED_DIR + baseName.substring(0, 9));
            String outPath = outDir.getPath() + "/" + this.element.getTitle() + Constants.Content.CORRECTED_SUFFIX + ".MP4";
            logger.debug("in:{} out:{}", inPath, outPath);

            if (!outDir.exists()) {
                if (outDir.mkdirs()) {
                    logger.debug("mkdirs: {}", outDir.getAbsolutePath());
                } else {
                    logger.error("failed to mkdirs.");
                }
            }

            try (InputStream in = new FileInputStream(inPath); OutputStream out = new FileOutputStream(outPath)) {
                byte[] buffer = new byte[1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                out.flush();
                registerFile(outPath);
                logger.info("created a corrected data: {}", outPath);
            } catch (IOException e) {
                throw new IOException("failed to copy file.", e);
            }
        }
    }

    private void recursiveDeleteFile(File file) {

        if (!file.exists()) return;

        if (file.isDirectory()) {
            for (File child : file.listFiles()) {
                recursiveDeleteFile(child);
            }
        }
        file.delete();
    }

    private String getBaseName(String path) throws IOException {
        Matcher matcher = Pattern.compile("/\\d{3}RICOH.*").matcher(path);
        if (!matcher.find()) {
            throw new IOException("cannot find path:" + path);
        }
        return matcher.group();
    }

    private void registerFile(String path) {
        Uri uri = Uri.fromFile(new File(path));
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri);
        this.context.sendBroadcast(mediaScanIntent);
    }

    private String getCorrectedFilePath(String originalId) {
        return Constants.Storage.CORRECTED_DIR + originalId + Constants.Content.CORRECTED_SUFFIX + ".mp4";
    }

    private boolean existsCorrectedData(String id) {
        String originalId = id.substring(Constants.Content.ORIGINAL_VIDEO_ID_PREFIX.length());
        String correctedData = getCorrectedFilePath(originalId);
        File file = new File(correctedData);
        return file.exists();
    }
}

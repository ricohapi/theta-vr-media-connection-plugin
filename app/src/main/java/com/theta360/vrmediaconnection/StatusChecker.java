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
import android.content.IntentFilter;
import android.os.BatteryManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theta4j.webapi.Options;
import org.theta4j.webapi.Theta;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class StatusChecker {

    private static final Logger logger = LoggerFactory.getLogger(StatusChecker.class);
    private static final long THRESHOLD_REJECT_PROCESS = 1 * 1073741824L; // 1GB
    private static final long THRESHOLD_VERY_FEW = 2 * 1073741824L; // 1GB
    private static final long THRESHOLD_FEW = 5 * 1073741824L; // 1GB
    private static final float LOW_BATTERY_LEVEL = 0.10f;

    private final Theta theta = Theta.createForPlugin();
    private Context context;

    public StatusChecker(Context context) {
        this.context = context;
    }

    public enum StorageStatus {
        ENOUGH,
        FEW,
        VERY_FEW
    }

    public StorageStatus getStorageStatus() throws InterruptedException, ExecutionException, IOException {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Future<Long> f = executorService.submit(new CheckStorageTask());
        long remainingSpace = -1l;

        try {
            remainingSpace = f.get();
            if (remainingSpace == -1) {
                throw new IOException("failed to get remaining space.");

            } else if (remainingSpace <= THRESHOLD_VERY_FEW) {
                logger.info("remainingSpace is very few.");
                return StorageStatus.VERY_FEW;

            } else if ((THRESHOLD_VERY_FEW < remainingSpace) && (remainingSpace <= THRESHOLD_FEW)) {
                logger.info("remainingSpace is few.");
                return StorageStatus.FEW;

            } else {
                return StorageStatus.ENOUGH;
            }

        } catch (InterruptedException e) {
            String message = "getOption(remainingSpace) is interrupted.";
            logger.debug(message);
            throw new InterruptedException(message);
        } finally {
            executorService.shutdown();
        }
    }

    public boolean isEnoughStorage(long fileSize) throws InterruptedException, ExecutionException, IOException {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Future<Long> f = executorService.submit(new CheckStorageTask());
        long remainingSpace = -1l;

        try {
            remainingSpace = f.get();
            if (remainingSpace == -1) {
                throw new IOException("failed to get remaining space.");
            }

            long space = remainingSpace - fileSize;
            logger.debug("remainingSpace - fileSize = {} - {} = {}", remainingSpace, fileSize, space);

            return (space >= THRESHOLD_REJECT_PROCESS);

        } catch (InterruptedException e) {
            String message = "getOption(remainingSpace) is interrupted.";
            logger.debug(message);
            throw new InterruptedException(message);
        } finally {
            executorService.shutdown();
        }
    }

    public boolean isEnoughBattery() {
        IntentFilter iFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, iFilter);

        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        float batteryPct = level / (float)scale;
        logger.debug("Battery level: {}", batteryPct);

        if (batteryPct <= LOW_BATTERY_LEVEL) {
            logger.info("Battery level is low.");
            return false;
        }
        return true;
    }

    private class CheckStorageTask implements Callable<Long> {

        @Override
        public Long call() {
            Long remainingSpace = -1l;
            try {
                remainingSpace = theta.getOption(Options.REMAINING_SPACE);
            } catch (IOException e) {
                logger.error("failed to get remaining space.");
                e.printStackTrace();
            }
            return remainingSpace;
        }
    }
}

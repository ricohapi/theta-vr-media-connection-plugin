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
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.TextView;

import com.theta360.pluginlibrary.activity.PluginActivity;
import com.theta360.pluginlibrary.callback.KeyCallback;
import com.theta360.pluginlibrary.receiver.KeyReceiver;
import com.theta360.pluginlibrary.values.LedColor;
import com.theta360.pluginlibrary.values.LedTarget;

import org.fourthline.cling.UpnpService;
import org.fourthline.cling.UpnpServiceImpl;
import org.fourthline.cling.android.AndroidUpnpServiceConfiguration;
import org.fourthline.cling.binding.LocalServiceBindingException;
import org.fourthline.cling.binding.annotations.AnnotationLocalServiceBinder;
import org.fourthline.cling.model.DefaultServiceManager;
import org.fourthline.cling.model.ValidationException;
import org.fourthline.cling.model.meta.DeviceDetails;
import org.fourthline.cling.model.meta.DeviceIdentity;
import org.fourthline.cling.model.meta.Icon;
import org.fourthline.cling.model.meta.LocalDevice;
import org.fourthline.cling.model.meta.LocalService;
import org.fourthline.cling.model.meta.ManufacturerDetails;
import org.fourthline.cling.model.meta.ModelDetails;
import org.fourthline.cling.model.types.DeviceType;
import org.fourthline.cling.model.types.UDADeviceType;
import org.fourthline.cling.model.types.UDN;
import org.fourthline.cling.support.connectionmanager.ConnectionManagerService;
import org.fourthline.cling.support.model.Protocol;
import org.fourthline.cling.support.model.ProtocolInfo;
import org.fourthline.cling.support.model.ProtocolInfos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class MainActivity extends PluginActivity {

    private static final Logger logger = LoggerFactory.getLogger(MainActivity.class);
    private static final Integer LONGPRESS_TIME_MS = 2500;

    private static boolean isCorrectionMode = true;

    private final ProtocolInfos sourceProtocols = new ProtocolInfos(
            new ProtocolInfo(
                    Protocol.HTTP_GET,
                    ProtocolInfo.WILDCARD,
                    Constants.MimeType.JPEG,
                    "DLNA.ORG_PN=JPEG;DLNA.ORG_OP=01"
            ),
            new ProtocolInfo(
                    Protocol.HTTP_GET,
                    ProtocolInfo.WILDCARD,
                    Constants.MimeType.MP4,
                    "DLNA.ORG_PN=AVC_MP4_BL_L32_HD_AAC;DLNA.ORG_OP=01;DLNA.ORG_CI=0"
            )
    );

    private TextView textView;

    private UpnpService upnpService;

    private UDN udn = new UDN(UUID.randomUUID());
    private String ipAddress;

    private Contents contents;
    private VideoConverter videoConverter;

    private HttpServer httpServer;

    private ModeListener listener;

    private Handler longPressHandler = new Handler();
    private Runnable longPressReceiver = new Runnable() {
        @Override
        public void run() {
            // recognized long press
            if (isCorrectionMode) {
                isCorrectionMode = false;
                notificationLedBlink(LedTarget.LED5, LedColor.BLUE, 500);
            } else {
                isCorrectionMode = true;
            }
            listener.updateStatus(isCorrectionMode);
            notificationLedHide(LedTarget.LED5);
            notificationAudioSelf();
            logger.info("isCorrectionMode: {}", isCorrectionMode);
        }
    };

    private KeyCallback keyCallback = new KeyCallback() {
        @Override
        public void onKeyDown(int keyCode, KeyEvent event) {
            if (keyCode == KeyReceiver.KEYCODE_CAMERA) {
                longPressHandler.postDelayed(longPressReceiver, LONGPRESS_TIME_MS);
            }
        }

        @Override
        public void onKeyUp(int keyCode, KeyEvent event) {
            if (keyCode == KeyReceiver.KEYCODE_CAMERA) {
                longPressHandler.removeCallbacks(longPressReceiver);
            }
        }

        @Override
        public void onKeyLongPress(int keyCode, KeyEvent event) {

        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setAutoClose(true);
        final Context context = getApplicationContext();

        try {
            checkStorageStatus(context);
        } catch (InterruptedException e) {
            logger.debug("checking storage status process is interrupted.");
            notificationError("checkStorageStatus() is interrupted.");
        } catch (ExecutionException | IOException e) {
            logger.error("failed to check storage status.");
            e.printStackTrace();
            notificationError("checkStorageStatus() failed.");
        }

        // setting for keeping the screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        this.textView = (TextView) findViewById(R.id.textView);

        this.ipAddress = getIPAddress();
        this.textView.setText(this.ipAddress);

        this.contents = new Contents();
        final ContentsCreator contentsCreator = new ContentsCreator(context, this.ipAddress, this.contents, this.isCorrectionMode);
        setListener(contentsCreator);
        contentsCreator.execute();

        this.videoConverter = new VideoConverter(context, contentsCreator);
        this.httpServer = new HttpServer(context, this.ipAddress, this.contents, this.videoConverter);
        try {
            this.httpServer.start();
        } catch (IOException ex) {
            logger.error("failed to start HttpServer.");
            ex.printStackTrace();
            notificationError("failed to start HttpServer.");
        }

        try {
            LocalDevice localDevice = createDevice();
            logger.info("createDevice: {}", localDevice.toString());
            upnpService = new UpnpServiceImpl(new AndroidUpnpServiceConfiguration());
            upnpService.getRegistry().addDevice(localDevice);
        } catch (ValidationException | LocalServiceBindingException e) {
            logger.error("Creating LocalDevice failed: {}", e.toString());
            e.printStackTrace();
            notificationError("failed to create LocalDevice");
        }

        setKeyCallback(keyCallback);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        removeListener();

        if (upnpService != null) {
            upnpService.getRegistry().removeAllLocalDevices();
            upnpService.shutdown();
        }
        httpServer.stop();
        videoConverter.shutdown();
    }

    protected LocalDevice createDevice()
            throws ValidationException, LocalServiceBindingException {
        DeviceType type = new UDADeviceType("MediaServer", 1);
        DeviceDetails details = new DeviceDetails(
                getResources().getString(R.string.friendly_name),
                new ManufacturerDetails(getResources().getString(R.string.manufacturer)),
                new ModelDetails(
                        getResources().getString(R.string.app_name),
                        getResources().getString(R.string.description),
                        BuildConfig.VERSION_NAME
                )
        );

        LocalService contentDirectory = new AnnotationLocalServiceBinder()
                .read(ContentDirectoryService.class);
        contentDirectory.setManager(
                new DefaultServiceManager<ContentDirectoryService>(
                        contentDirectory, ContentDirectoryService.class) {
                    @Override
                    protected ContentDirectoryService createServiceInstance() throws Exception {
                        return new ContentDirectoryService(contents);
                    }
                }
        );

        LocalService connectionManager = new AnnotationLocalServiceBinder()
                .read(ConnectionManagerService.class);
        connectionManager.setManager(
                new DefaultServiceManager<ConnectionManagerService>(
                        connectionManager, null) {
                    @Override
                    protected ConnectionManagerService createServiceInstance() throws Exception {
                        return new ConnectionManagerService(sourceProtocols, null);
                    }
                }
        );

        Bitmap icon = BitmapFactory.decodeResource(getResources(), R.drawable.theta_image_01);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        icon.compress(Bitmap.CompressFormat.PNG, 100, baos);

        return new LocalDevice(
                new DeviceIdentity(udn),
                type,
                details,
                new Icon(Constants.MimeType.PNG,
                        icon.getWidth(), icon.getHeight(), 8,
                        "icon.png", baos.toByteArray()),
                new LocalService[]{
                        connectionManager, contentDirectory});
    }

    private void setListener(ModeListener listener) {
        this.listener = listener;
    }

    private void removeListener() {
        this.listener = null;
    }

    private void checkStorageStatus(Context context) throws InterruptedException, ExecutionException, IOException {
        StatusChecker checker = new StatusChecker(context);
        StatusChecker.StorageStatus storageStatus = checker.getStorageStatus();

        switch (storageStatus) {
            case VERY_FEW:
                notificationLedBlink(LedTarget.LED8, null, 2000);
                break;

            case FEW:
                notificationLedShow(LedTarget.LED8);
                break;

            default:
                notificationLedHide(LedTarget.LED8);
                break;
        }
    }

    private String getIPAddress() {
        String ipAddress = Constants.Net.AP_HOST;
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface netInterface : interfaces) {
                List<InetAddress> addrs = Collections.list(netInterface.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String tmp_addr = addr.getHostAddress();
                        // check whether IP Address is IPv4
                        if (tmp_addr.indexOf(':') < 0) {
                            ipAddress = tmp_addr;
                            logger.info("NetworkInterface:{} IPAddress:{}", netInterface.toString(), ipAddress);
                        }
                    }
                }
            }
        } catch (SocketException e) {
            logger.error("failed to get IP Address. {}", e.getMessage());
            e.printStackTrace();
        }
        return ipAddress;
    }
}

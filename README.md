# VR Media Connection plug-in

# 1. Overview
Using this plug-in, users can connect their THETA V wirelessly to a PlayStation VR, an Oculus Go, computer, smartphone or other devices which support Media Server (DLNA), and view 360° photos and videos direct from their THETA V.

# 2. Terms of Service

> You agree to comply with all applicable export and import laws and regulations applicable to the jurisdiction in which the Software was obtained and in which it is used. Without limiting the foregoing, in connection with use of the Software, you shall not export or re-export the Software into any U.S. embargoed countries (currently including, but necessarily limited to, Crimea – Region of Ukraine, Cuba, Iran, North Korea, Sudan and Syria) or to anyone on the U.S. Treasury Department’s list of Specially Designated Nationals or the U.S. Department of Commerce Denied Person’s List or Entity List.  By using the Software, you represent and warrant that you are not located in any such country or on any such list.  You also agree that you will not use the Software for any purposes prohibited by any applicable laws, including, without limitation, the development, design, manufacture or production of missiles, nuclear, chemical or biological weapons.

By using the VR Media Connection plug-in, you are agreeing to the above and the license terms, [license.txt](license.txt).

Copyright &copy; 2019 Ricoh Company, Ltd.

# 3. Build and Use Environment

## 3-1. Hardware

* RICOH THETA V and Z1
* Firmware ver.2.50.1 (V), 1.03.5 (Z1)

    > How to update your RICOH THETA
    > * [THETA V](https://support.theta360.com/en/manual/v/content/update/update_01.html)
    > * [THETA Z1](https://support.theta360.com/en/manual/z1/content/update/update_01.html)

## 3-2. Development Environment

This plug-in has been built under the following conditions.

#### Operating System

* Windows&trade; 10 Version 1709
* macOS&reg; High Sierra ver.10.13

#### Development environment

* Android&trade; Studio 3.3+
* gradle 3.3.2
* Android SDK (API Level 25)
* compileSdkVersion 25
* buildToolsVersion "28.0.3"
* minSdkVersion 25
* targetSdkVersion 25

# 4. Install
Android Studio install apk after build automatically. Or use the following command after build.

```
adb install -r app-debug.apk
```

### Give permissions for this plug-in.

  Using desktop viewing app as Vysor, open Settings app and turns on the permissions at "Apps" > "VR Media Connection" > "Permissions"

# 5. How to Use

A THETA V running the VR Media Connection can be configured to connect to the devices in two different wireless modes:

#### Access Point mode (AP-mode)

When using wireless LAN mode (AP-mode), you can connect directly to the THETA V and the device. You select the SSID of the THETA V from the network list and enter the password. This configuration works well when a local network is not available.

#### Client mode (CL-mode)

When using wireless LAN client mode (CL-mode), you connect both the THETA V and the device to a local network. This configuration works well when the device is already connected to a local network or you have multiple devices that you want connected to the local network at the same time.

## Instructions

#### For AP-mode

1. Turn on Wi-Fi on the THETA V (5GHz Wi-Fi setting on the THETA V is recommended)
2. From the device, find the THETA V SSID (ex. THETAYL12345678) and enter the THETA V password (ex. 12345678) from the network setting on the device. The Wi-Fi LED will indicate connection status: Solid Blue = connected, Flashing Blue = not connected.
3. Turn on Plug-in Mode on the THETA V by pressing the Mode button for 2 seconds. A White LED indicates Plug-in Mode is active.
4. On the device, open a media server (RICOH THETA) from the gallery.
5. Have fun watching 360°photos and videos direct from your THETA V.

#### For CL-mode

1. See the following for further details on how to connect the THETA V in wireless LAN client mode (CL-mode). (5GHz Wi-Fi setting on the THETA V is recommended)
Video: https://www.youtube.com/watch?v=tkqyBNOWWIY&t=9s
Manual: https://theta360.com/en/support/manual/v/content/prepare/prepare_08.html
2. Connect the device to same network that your THETA V is connected to. The Wi-Fi LED will indicate connection status: Solid Green = connected, Flashing Green = not connected.
3. Turn on Plug-in Mode on the THETA V by pressing the Mode button for 2 seconds. A White LED indicates Plug-in Mode is active.
4. On the device, open a media server (RICOH THETA) from the gallery.
5. Have fun watching 360°photos and videos direct from your THETA V.

# 6. History
* ver.1.0.0 (2019/04/12): Initial version for github.

---

## Trademark Information

The names of products and services described in this document are trademarks or registered trademarks of each company.

* PlayStation is a registered trademark or trademark of Sony Interactive Entertainment Inc.
* Oculus VR’s trademarks, including OCULUS, OCULUS GO, OCULUS RIFT, OCULUS TOUCH, OCULUS READY, POWERED BY OCULUS, VR FOR GOOD, and the Stadium logo, are owned by Facebook Technologies, LLC.
* Android, Nexus, Google Chrome, Google Play, Google Play logo, Google Maps, Google+, Gmail, Google Drive, Google Cloud Print and YouTube are trademarks of Google Inc.
* Apple, Apple logo, Macintosh, Mac, Mac OS, OS X, AppleTalk, Apple TV, App Store, AirPrint, Bonjour, iPhone, iPad, iPad mini, iPad Air, iPod, iPod mini, iPod classic, iPod touch, iWork, Safari, the App Store logo, the AirPrint logo, Retina and iPad Pro are trademarks of Apple Inc., registered in the United States and other countries. The App Store is a service mark of Apple Inc.
* Microsoft, Windows, Windows Vista, Windows Live, Windows Media, Windows Server System, Windows Server, Excel, PowerPoint, Photosynth, SQL Server, Internet Explorer, Azure, Active Directory, OneDrive, Outlook, Wingdings, Hyper-V, Visual Basic, Visual C ++, Surface, SharePoint Server, Microsoft Edge, Active Directory, BitLocker, .NET Framework and Skype are registered trademarks or trademarks of Microsoft Corporation in the United States and other countries. The name of Skype, the trademarks and logos associated with it, and the "S" logo are trademarks of Skype or its affiliates.
* Wi-Fi™, Wi-Fi Certified Miracast, Wi-Fi Certified logo, Wi-Fi Direct, Wi-Fi Protected Setup, WPA, WPA 2 and Miracast are trademarks of the Wi-Fi Alliance.
* The official name of Windows is Microsoft Windows Operating System.
* All other trademarks belong to their respective owners.

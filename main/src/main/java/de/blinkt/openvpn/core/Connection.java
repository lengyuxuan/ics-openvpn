/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.core;

import android.text.TextUtils;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.IOException;
import java.util.regex.Pattern;
import java.util.Locale;

public class Connection implements Serializable, Cloneable {
    public String mDynamicAddress = "";
    public boolean mDynamicAddressSwitch = false;
    public String mServerName = "openvpn.example.com";
    public String mServerPort = "1194";
    public boolean mUseUdp = true;
    public String mCustomConfiguration = "";
    public boolean mUseCustomConfig = false;
    public boolean mEnabled = true;
    public int mConnectTimeout = 0;
    public static final int CONNECTION_DEFAULT_TIMEOUT = 120;
    public ProxyType mProxyType = ProxyType.NONE;
    public String mProxyName = "proxy.example.com";
    public String mProxyPort = "8080";

    public boolean mUseProxyAuth;
    public String mProxyAuthUser = null;
    public String mProxyAuthPassword = null;

    public enum ProxyType {
        NONE,
        HTTP,
        SOCKS5,
        ORBOT
    }

    private static final long serialVersionUID = 92031902903829089L;


    public String getConnectionBlock(boolean isOpenVPN3) {
        String remoteAddress = getDynamicRemote();
        String cfg = "";

        // Server Address
        cfg += "remote " + remoteAddress;
        if (mUseUdp)
            cfg += " udp\n";
        else
            cfg += " tcp-client\n";

        if (mConnectTimeout != 0)
            cfg += String.format(Locale.US, " connect-timeout  %d\n", mConnectTimeout);

        // OpenVPN 2.x manages proxy connection via management interface
        if ((isOpenVPN3 || usesExtraProxyOptions()) && mProxyType == ProxyType.HTTP)
        {
            cfg+=String.format(Locale.US,"http-proxy %s %s\n", mProxyName, mProxyPort);
            if (mUseProxyAuth)
                cfg+=String.format(Locale.US, "<http-proxy-user-pass>\n%s\n%s\n</http-proxy-user-pass>\n", mProxyAuthUser, mProxyAuthPassword);
        }
        if (usesExtraProxyOptions() && mProxyType == ProxyType.SOCKS5) {
            cfg+=String.format(Locale.US,"socks-proxy %s %s\n", mProxyName, mProxyPort);
        }

        if (!TextUtils.isEmpty(mCustomConfiguration) && mUseCustomConfig) {
            cfg += mCustomConfiguration;
            cfg += "\n";
        }


        return cfg;
    }

    public boolean usesExtraProxyOptions() {
        return (mUseCustomConfig && mCustomConfiguration.contains("http-proxy-option "));
    }


    @Override
    public Connection clone() throws CloneNotSupportedException {
        return (Connection) super.clone();
    }

    public boolean isOnlyRemote() {
        return TextUtils.isEmpty(mCustomConfiguration) || !mUseCustomConfig;
    }

    public int getTimeout() {
        if (mConnectTimeout <= 0)
            return CONNECTION_DEFAULT_TIMEOUT;
        else
            return mConnectTimeout;
    }

    private String getDynamicRemote() {
        if (!mDynamicAddressSwitch) {
            return mServerName + " " + mServerPort;
        }
        try {
            URL url = new URL(mDynamicAddress);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            int resCode = conn.getResponseCode();
            if (resCode != HttpURLConnection.HTTP_OK) {
                return mServerName + " " + mServerPort;
            }
            InputStream inputStream = conn.getInputStream();
            ByteArrayOutputStream arrayOutputStream = new ByteArrayOutputStream();
            byte[] bytes = new byte[1024];
            int length = 0;
            while ((length = inputStream.read(bytes)) != -1) {
                arrayOutputStream.write(bytes, 0, length);
                arrayOutputStream.flush();
            }
            String res = arrayOutputStream.toString();
            Log.d("dynamic address", "response: " + res);
            return res;
        } catch (IOException e) {
            Log.e("dynamic address", "request error: " + e.toString());
            return mServerName + " " + mServerPort;
        }
    }
}

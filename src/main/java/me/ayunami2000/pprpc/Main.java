package me.ayunami2000.pprpc;

import com.del.potplayercontrol.api.JNAPotPlayerHelper;
import com.del.potplayercontrol.api.PlayStatus;
import com.del.potplayercontrol.api.PotPlayer;
import com.del.potplayercontrol.impl.JNAPotPlayer;
import com.del.potplayercontrol.impl.Window;
import com.sun.jna.platform.WindowUtils;
import com.sun.jna.platform.win32.User32;
import de.jcm.discordgamesdk.Core;
import de.jcm.discordgamesdk.CreateParams;
import de.jcm.discordgamesdk.activity.Activity;
import de.jcm.discordgamesdk.activity.ActivityType;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.Objects;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException, InterruptedException {
        if (SystemTray.isSupported()) {
            final PopupMenu popup = new PopupMenu();
            final TrayIcon trayIcon = new TrayIcon(ImageIO.read(Objects.requireNonNull(Main.class.getResource("/icon.png"))));
            final SystemTray tray = SystemTray.getSystemTray();

            MenuItem exitItem = new MenuItem("Exit PotPlayer RPC");
            exitItem.addActionListener(e -> System.exit(0));
            popup.add(exitItem);

            trayIcon.setPopupMenu(popup);

            try {
                tray.add(trayIcon);
            } catch (AWTException e) {
                System.err.println("TrayIcon could not be added.");
            }
        } else {
            System.err.println("SystemTray is not supported");
        }

        String name = "PotPlayer";
        String end = " - " + name;
        PotPlayer player;
        File discordLibrary = DownloadNativeLibrary.downloadDiscordLibrary();
        if (discordLibrary == null) {
            System.err.println("Error downloading Discord SDK.");
            System.exit(-1);
            return;
        }
        Core.init(discordLibrary);
        try (CreateParams params = new CreateParams()) {
            params.setClientID(1146489019925004359L);
            params.setFlags(CreateParams.getDefaultFlags());
            try (Core core = new Core(params)) {
                while (true) {
                    List<Window> windows = JNAPotPlayerHelper.getAllPlayerWindows(window -> window.getWindowText().endsWith(end) || window.getWindowText().equals(name));
                    if (!windows.isEmpty()) {
                        player = new JNAPotPlayer(windows.get(0));
                        if (player.getPlayStatus() == PlayStatus.Undefined) {
                            Thread.sleep(160);
                            continue;
                        }
                        int i = 0;
                        String t = name;
                        PlayStatus ps = null;
                        long ct = 0;
                        while (User32.INSTANCE.IsWindow(player.getWindow().getHwnd())) {
                            String tmp = WindowUtils.getWindowTitle(player.getWindow().getHwnd());
                            if (!tmp.endsWith(end) && !tmp.equals(name)) break;
                            PlayStatus tmpps = player.getPlayStatus();
                            long tmpct = player.getCurrentTime();
                            if (!t.equals(tmp) || tmpps != ps || Math.abs(tmpct - ct) > 1600) i = 1000;
                            ct = tmpct;
                            if (i++ >= 1000) {
                                i = 0;
                                t = tmp;
                                ps = tmpps;
                                try (Activity activity = new Activity()) {
                                    activity.setType(ActivityType.LISTENING);
                                    activity.assets().setLargeImage("logo");
                                    activity.assets().setLargeText("PotPlayer-DiscordRPC\ngithub.com/ayunami2000");
                                    String song = t;
                                    if (song.endsWith(end)) song = song.substring(0, song.lastIndexOf(end));
                                    if (ps == PlayStatus.Running) {
                                        activity.timestamps().setStart(Instant.now().minusMillis(ct));
                                        activity.setDetails(convertMillisecondsToHMmSs(player.getTotalTime()) + " total | " + song);
                                        activity.assets().setSmallImage("play");
                                        activity.assets().setSmallText("Playing");
                                    } else if (ps == PlayStatus.Paused) {
                                        activity.setDetails(convertMillisecondsToHMmSs(ct) + " elapsed | " + convertMillisecondsToHMmSs(player.getTotalTime()) + " total | " + song);
                                        activity.assets().setSmallImage("pause");
                                        activity.assets().setSmallText("Paused");
                                    } else {
                                        activity.setDetails("Idle");
                                        activity.assets().setSmallImage("stop");
                                        activity.assets().setSmallText("Stopped");
                                    }
                                    core.activityManager().updateActivity(activity);
                                }
                            }
                            core.runCallbacks();
                            Thread.sleep(16);
                        }
                        core.activityManager().clearActivity();
                    }
                    Thread.sleep(1600);
                }
            }
        }
    }

    private static String convertMillisecondsToHMmSs(long seconds) {
        seconds = seconds / 1000;
        long s = seconds % 60;
        long m = (seconds / 60) % 60;
        long h = (seconds / (60 * 60)) % 24;
        if (h == 0) return String.format("%02d:%02d", m, s);
        return String.format("%d:%02d:%02d", h, m, s);
    }
}
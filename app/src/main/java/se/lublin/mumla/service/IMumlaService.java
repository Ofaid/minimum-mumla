package se.lublin.mumla.service;

import java.util.List;

import se.lublin.humla.IHumlaService;

/**
 * Created by andrew on 28/02/17.
 */
public interface IMumlaService extends IHumlaService {
    void setOverlayShown(boolean showOverlay);

    boolean isOverlayShown();

    void clearChatNotifications();

    void markErrorShown();

    boolean isErrorShown();

    void onTalkKeyDown();

    void onTalkKeyUp();

    /** Updates the managed-radio TX gate after the configured room has been verified. */
    void setRadioRoomReady(boolean ready);

    boolean isRadioReceiving();

    List<String> getRadioTalkers();

    List<IChatMessage> getMessageLog();

    void clearMessageLog();

    void setSuppressNotifications(boolean suppressNotifications);
}

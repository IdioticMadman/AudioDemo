package com.robert.audiodemo;

public interface AppContract {

    // 服务器地址
    String SERVER_ADDRESS = "192.168.124.3";
    // 服务器端口
    int PORT = 30401;

    interface Presenter {
        void leaveRoom();

        void joinRoom(String code);

        void createRoom();

        void destroy();
    }

    interface View {
        void showProgress(int string);

        void dismissProgress();

        void showToast(String msg);

        void showRoomCode(String roomCode);

        void onOnline();

        void onOffline();
    }

}

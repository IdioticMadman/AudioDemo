package com.robert.audiodemo;

public class Presenter implements AppContract.Presenter {
    private final AppContract.View mView;

    public Presenter(AppContract.View view) {
        this.mView = view;
    }

    @Override
    public void leaveRoom() {

    }

    @Override
    public void joinRoom(String code) {

    }

    @Override
    public void createRoom() {

    }

    @Override
    public void destroy() {

    }
}

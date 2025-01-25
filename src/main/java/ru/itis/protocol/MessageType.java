package ru.itis.protocol;

public enum MessageType {
    CONNECT,
    DISCONNECT,
    STATE,
    SET_VELOCITY,
    LOBBY_UPDATE,
    ASSIGN_ROLE,
    START_GAME,
    RESET_LOBBY,
    KICK_PLAYER
}
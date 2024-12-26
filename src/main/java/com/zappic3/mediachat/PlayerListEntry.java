package com.zappic3.mediachat;

import java.util.UUID;

public class PlayerListEntry {
    private String player;
    private String id;


    // this is necessary so Janksons Marshaller can create a default object and populate
    // it later during the config file loading process
    public PlayerListEntry() {
        player = "Steve";
        id = "-1";
    }

    public PlayerListEntry(String player, String id) {
        this.player = player;
        this.id = id;
    }

    public PlayerListEntry(String player, UUID id) {
        this(player, id.toString()); // Convert UUID to String
    }

    public UUID getUUID() {
        return UUID.fromString(id); // Convert String back to UUID
    }

    public String getPlayer() {
        return player;
    }

    public void setPlayer(String player) {
        this.player = player;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof PlayerListEntry) {
            if (this.player.equals(((PlayerListEntry) obj).getPlayer())) {
                return this.id.equals(((PlayerListEntry) obj).getId());
            }
        }
        return false;
    }
}


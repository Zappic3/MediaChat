package com.zappic3.mediachat;

import java.awt.image.BufferedImage;

public class NetworkManager {

    public record mediaSyncPacket(BufferedImage image) {}
}

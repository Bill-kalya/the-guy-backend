package com.theguy.app.utils;

import java.util.LinkedHashMap;
import java.util.Map;

public final class CountyCoordinates {

    private static final Map<String, double[]> CENTROIDS = new LinkedHashMap<>();

    static {
        put("MOMBASA", -4.0435, 39.6682);
        put("KWALE", -4.1740, 39.4585);
        put("KILIFI", -3.5107, 39.9093);
        put("TANA RIVER", -0.5189, 40.5237);
        put("LAMU", -2.2705, 40.9020);
        put("TAITA TAVETA", -3.3829, 38.3520);
        put("GARISSA", -0.4569, 39.6583);
        put("WAJIR", 1.7500, 40.0600);
        put("MANDERA", 3.9370, 41.8670);
        put("MARSABIT", 2.3300, 37.9900);
        put("ISIOLO", 0.3546, 37.5820);
        put("MERU", 0.0500, 37.6500);
        put("THARAKA-NITHI", -0.3310, 37.8260);
        put("EMBU", -0.5373, 37.4555);
        put("KITUI", -1.3667, 38.0100);
        put("MACHAKOS", -1.5177, 37.2634);
        put("MAKUENI", -1.8030, 37.4580);
        put("NYANDARUA", -0.5830, 36.6040);
        put("NYERI", -0.4233, 36.9500);
        put("KIRINYAGA", -0.6590, 37.2720);
        put("MURANG'A", -0.7540, 37.0380);
        put("KIAMBU", -1.1700, 36.8200);
        put("TURKANA", 3.3200, 35.6000);
        put("WEST POKOT", 1.4500, 35.0900);
        put("SAMBURU", 1.2800, 36.8700);
        put("TRANS NZOIA", 1.0600, 34.9500);
        put("UASIN GISHU", 0.5200, 35.2800);
        put("ELGEYO-MARAKWET", 0.9500, 35.5000);
        put("NANDI", 0.1700, 35.0900);
        put("BARINGO", 0.4700, 35.9700);
        put("LAIKIPIA", 0.0900, 36.6500);
        put("NAKURU", -0.3031, 36.0800);
        put("NAROK", -1.0800, 35.8700);
        put("KAJIADO", -2.1000, 36.7800);
        put("KERICHO", -0.3680, 35.2830);
        put("BOMET", -0.7810, 35.3400);
        put("KAKAMEGA", 0.2827, 34.7519);
        put("VIHIGA", 0.0780, 34.7200);
        put("BUNGOMA", 0.5695, 34.5584);
        put("BUSIA", 0.4600, 34.1100);
        put("SIAYA", 0.0600, 34.2800);
        put("KISUMU", -0.0917, 34.7680);
        put("HOMA BAY", -0.5270, 34.4550);
        put("MIGORI", -1.0690, 34.4730);
        put("KISII", -0.6770, 34.7790);
        put("NYAMIRA", -0.5650, 34.9340);
        put("NAIROBI", -1.2864, 36.8172);
    }

    private static void put(String name, double lat, double lng) {
        CENTROIDS.put(name, new double[]{lat, lng});
    }

    /** Looks up county centroid coordinates by name (case-insensitive, partial match). */
    public static double[] find(String countyName) {
        if (countyName == null || countyName.isBlank()) return null;
        String key = countyName.trim().toUpperCase();

        if (CENTROIDS.containsKey(key)) return CENTROIDS.get(key);

        for (Map.Entry<String, double[]> entry : CENTROIDS.entrySet()) {
            if (entry.getKey().startsWith(key) || key.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }

        for (Map.Entry<String, double[]> entry : CENTROIDS.entrySet()) {
            if (entry.getKey().contains(key) || key.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        return null;
    }
}
